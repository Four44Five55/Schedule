package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.services.LessonSortingService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Обрабатывает фазу 1 — распределение лекций.
 */
@Slf4j
public class LectureDistributionHandler {
    private final DistributionContext context;
    private final LessonPlacementService placement;
    private final LessonSortingService lessonSortingService;
    private final LessonDateFinder dateFinder;

    public LectureDistributionHandler(DistributionContext context,
                                      LessonPlacementService placement,
                                      LessonSortingService sorting) {
        this.context = context;
        this.placement = placement;
        this.lessonSortingService = sorting;
        this.dateFinder = new LessonDateFinder(context, placement);
    }

    /**
     * Распределяет лекции для всех преподавателей.
     */
    public void distributeLectures(LocalDate semesterEnd) {
        for (Educator educator : context.getEducators()) {
            if (educator.getId() == 304) {
                distributeForEducator(educator, semesterEnd);
            }
            /*distributeForEducator(educator, semesterEnd);*/
        }
    }

    /**
     * Распределяет занятия для указанного преподавателя.
     */
    public void distributeForEducator(Educator educator, LocalDate semesterEnd) {
        log.info("=== Распределение для преподавателя: {} ===", educator.getName());

        List<Lesson> tempLessons = placement.getLessonsForEducator(educator);
        List<Lesson> lessons = lessonSortingService.getSortedLessons(tempLessons);

        if (lessons.isEmpty()) {
            log.info("Нет занятий для распределения");
            return;
        }

        List<LocalDate> availableDates = dateFinder.getAvailableDates(lessons, semesterEnd);
        if (availableDates.isEmpty()) {
            log.error("Нет доступных дат для {}", educator.getName());
            return;
        }

        Map<Lesson, LocalDate> lessonToDateMap = dateFinder.calculatePotentialDates(lessons, availableDates);
        log.info("Доступных дней: {}, занятий: {}, назначено дат: {}",
                availableDates.size(), lessons.size(), lessonToDateMap.size());

        // Сохраняем мапу для использования в фазе 2 (практики)
        context.mergeIntoLessonToDateMap(lessonToDateMap);

        int placedCount = 0;
        int alreadyPlacedCount = 0;
        int notPlacedCount = 0;

        for (Lesson lesson : lessons) {
            // Размещаем только лекции в этой фазе
            if (lesson.getKindOfStudy() != KindOfStudy.LECTURE) {
                continue;
            }

            // Пропускаем уже размещённые
            if (context.isLessonDistributed(lesson)) {
                alreadyPlacedCount++;
                continue;
            }

            // Получаем дату из мапа
            LocalDate date = lessonToDateMap.get(lesson);
            if (date == null) {
                notPlacedCount++;
/*                log.warn("✗ Не назначена дата для: {}/{}",
                        lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                        lesson.getCurriculumSlot().getThemeLesson().getThemeNumber());*/

                log.warn("✗ {} {}/{}, тема: {}",
                        lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                        lesson.getKindOfStudy().getAbbreviationName(),
                        lesson.getCurriculumSlot().getPosition(),
                        lesson.getCurriculumSlot().getThemeLesson().getThemeNumber());

                continue;
            }

            // Размещаем занятие с проверкой перегрузки дат для поточных лекций
            if (placeLectureWithLoadCheck(lesson, date, availableDates)) {
                placedCount++;
                context.addDistributedLesson(lesson);
                log.info("  ✓ {} {}/{}, тема: {}, дата: {}",
                        lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                        lesson.getKindOfStudy().getAbbreviationName(),
                        lesson.getCurriculumSlot().getPosition(),
                        lesson.getCurriculumSlot().getThemeLesson().getThemeNumber(),
                        date);
            } else {
                notPlacedCount++;
                log.warn("✗ Не удалось разместить {}/{} на {}",
                        lesson.getKindOfStudy().getAbbreviationName(),
                        lesson.getCurriculumSlot().getPosition(),
                        date);
            }
        }

        log.info("=== Результат для {}: размещено={}, уже было={}, неразмещено={} ===",
                educator.getName(), placedCount, alreadyPlacedCount, notPlacedCount);

        // Лог неразмещённых
        if (placedCount + alreadyPlacedCount < lessons.size()) {
            logUnplacedLessons(educator, lessons);
        }
    }

    /**
     * Логирует неразмещённые занятия.
     */
    private void logUnplacedLessons(Educator educator, List<Lesson> lessons) {
        List<Lesson> unplaced = lessons.stream()
                .filter(l -> !context.isLessonDistributed(l))
                .toList();

        if (!unplaced.isEmpty()) {
            log.warn("=== Неразпределённые занятия для {} ===", educator.getName());
            for (Lesson l : unplaced) {
                if (l.getKindOfStudy() == KindOfStudy.LECTURE) {
                    String theme = l.getCurriculumSlot().getThemeLesson() != null
                            ? l.getCurriculumSlot().getThemeLesson().getThemeNumber()
                            : "N/A";
                    log.warn("  {} {}/{}, тема: {}, группы: {}",
                            l.getDisciplineCourse().getDiscipline().getAbbreviation(),
                            l.getKindOfStudy().getAbbreviationName(),
                            l.getCurriculumSlot().getPosition(),
                            theme,
                            l.getStudyStream().getGroups());
                }

            }
        }
    }

    /**
     * Размещает лекцию с учётом перегрузки дат для поточных лекций.
     * Для поточных лекций (>=3 групп) проверяет нагрузку на дату и при необходимости
     * ищет более раннюю свободную дату.
     */
    private boolean placeLectureWithLoadCheck(Lesson lesson,
                                              LocalDate targetDate,
                                              List<LocalDate> availableDates) {
        // Только для поточных лекций (>=3 групп)
        if (!isLargeStreamLecture(lesson)) {
            return placement.place(lesson, targetDate);
        }

        // Поточная лекция — проверяем нагрузку на целевую дату
        if (!isDateOverloaded(targetDate)) {
            return placement.place(lesson, targetDate);
        }

        // Дата перегружена — ищем более раннюю
        LocalDate earlierDate = findEarlierAvailableDate(lesson, targetDate, availableDates);
        if (earlierDate != null) {
            log.info("  [перенос поточной лекции] {} -> {} (перегрузка на {})",
                    lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                    earlierDate, targetDate);
            return placement.place(lesson, earlierDate);
        }

        // Не нашли более раннюю — пробуем исходную дату
        return placement.place(lesson, targetDate);
    }

    /**
     * Проверяет, является ли лекция поточной (для большого числа групп).
     * Поточная лекция — это лекция для 3 и более групп.
     */
    private boolean isLargeStreamLecture(Lesson lesson) {
        if (lesson.getStudyStream() == null || lesson.getStudyStream().getGroups() == null) {
            return false;
        }
        return lesson.getStudyStream().getGroups().size() >= 3;
    }

    /**
     * Проверяет, перегружена ли дата лекциями (>=3 лекции).
     */
    private boolean isDateOverloaded(LocalDate date) {
        return countLecturesOnDate(date) >= 3;
    }

    /**
     * Подсчитывает количество лекций на указанную дату.
     * Учитывает уже размещённые лекции И лекции с назначенными датами (для других преподавателей).
     */
    private int countLecturesOnDate(LocalDate date) {
        int count = 0;

        // 1. Уже размещённые лекции
        count += (int) context.getDistributedLessons().stream()
                .filter(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
                .filter(l -> context.getWorkspace().getCellForLesson(l) != null)
                .filter(l -> context.getWorkspace().getCellForLesson(l).getDate().equals(date))
                .count();

        // 2. Лекции с назначенными датами, но ещё не размещённые (через lessonToDateMap)
        // Для этого нужно пройтись по всем занятиям в контексте
        for (Lesson lesson : context.getLessons()) {
            if (lesson.getKindOfStudy() == KindOfStudy.LECTURE
                    && !context.isLessonDistributed(lesson)
                    && context.hasDateForLesson(lesson)) {
                LocalDate assignedDate = context.getDateForLesson(lesson);
                if (assignedDate.equals(date)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Ищет более раннюю доступную дату с минимальной нагрузкой.
     * Проверяет до 10 дат раньше целевой, выбирает дату с наименьшей нагрузкой.
     */
    private LocalDate findEarlierAvailableDate(Lesson lesson,
                                               LocalDate targetDate,
                                               List<LocalDate> availableDates) {
        int targetIndex = availableDates.indexOf(targetDate);
        if (targetIndex <= 0) return null;

        LocalDate bestDate = null;
        int minLoad = Integer.MAX_VALUE;

        // Ищем дату раньше с минимальной нагрузкой (максимум 10 дней назад)
        for (int i = targetIndex - 1; i >= Math.max(0, targetIndex - 10); i--) {
            LocalDate earlier = availableDates.get(i);
            int load = countLecturesOnDate(earlier);

            // Пропускаем, если уже перегружено
            if (load >= 3) continue;

            // Проверяем возможность размещения
            if (!placement.canPlace(lesson, earlier)) continue;

            // Выбираем дату с минимальной нагрузкой
            if (load < minLoad) {
                minLoad = load;
                bestDate = earlier;

                // Идеальный вариант нашли — дата пустая
                if (load == 0) break;
            }
        }

        return bestDate;
    }
}
