package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обрабатывает фазу 1 — распределение лекций.
 * Вынесены методы из DistributionDiscipline:
 * - distributeLecturesPhase()
 * - distributeLessonsForEducator() (только лекционная часть)
 */
@Slf4j
public class LectureDistributionHandler {
    private final DistributionContext context;
    private final EducatorPrioritizer prioritizer;
    private final LessonPlacementService placementService;
    private final ChainPlacementHandler chainHandler;

    public LectureDistributionHandler(DistributionContext context,
                                      EducatorPrioritizer prioritizer,
                                      LessonPlacementService placementService,
                                      ChainPlacementHandler chainHandler) {
        this.context = context;
        this.prioritizer = prioritizer;
        this.placementService = placementService;
        this.chainHandler = chainHandler;
    }

    /**
     * Распределяет лекции для всех преподавателей.
     */
    public void distributeLectures(LocalDate semesterEnd) {
        List<Educator> sortedEducators = prioritizer.sortByPriority(context.getEducators(), context.getLessons());

        for (Educator educator : sortedEducators) {
            distributeLecturesForEducator(educator, semesterEnd);
        }
    }

    /**
     * Распределяет занятия (лекции и практики) для указанного преподавателя.
     * Используется для начального распределения на этапе лекций.
     */
    public void distributeLecturesForEducator(Educator educator, LocalDate semesterEnd) {
        log.info("=== НАЧАЛО: Распределение занятий для преподавателя: {} ===", educator.getName());

        // 1. Подготовка списка занятий преподавателя
        List<Lesson> educatorLessons = context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .sorted((l1, l2) -> Integer.compare(
                        l1.getCurriculumSlot().getPosition(),
                        l2.getCurriculumSlot().getPosition()))
                .collect(Collectors.toList());

        if (educatorLessons.isEmpty()) {
            log.info("Нет занятий для распределения у преподавателя: {}", educator.getName());
            return;
        }

        // Временно используем LessonSortingService для сортировки занятий
        // В реальном коде нужно передать sortingService через конструктор
        List<Lesson> sortedLessons = educatorLessons; // Упрощение

        // 2. Расчет необходимого количества дней
        int neededDays = calculateDaysForLessons(sortedLessons);

        // первый урок как образец для проверки ограничений
        Lesson prototypeLesson = sortedLessons.get(0);

        List<LocalDate> viableDates = CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isAfter(semesterEnd))
                .filter(d -> isDayViableForLesson(d, prototypeLesson))
                .sorted()
                .collect(Collectors.toList());

        if (viableDates.isEmpty()) {
            log.error("CRITICAL: Нет ни одного доступного дня для {}", educator.getName());
            return;
        }

        // 3. Выбор целевых дат
        List<Integer> targetDateIndices = distributeLessonsEvenly(viableDates.size(), neededDays);

        int lessonIndex = 0;

        // 4. Главный цикл распределения
        for (Integer dateIdx : targetDateIndices) {
            if (lessonIndex >= sortedLessons.size()) break;

            LocalDate idealDate = viableDates.get(dateIdx);
            Lesson nextLesson = sortedLessons.get(lessonIndex);

            LocalDate realDate = placementService.findNearestAvailableDate(idealDate, nextLesson, viableDates);

            if (realDate == null) {
                log.error("CRITICAL: Не нашлось свободного дня для {}", nextLesson);
                continue;
            }

            // Заполняем этот день
            List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(realDate);
            int lecturesToday = 0;
            int practicesToday = 0;

            while (lessonIndex < sortedLessons.size()) {
                Lesson currentLesson = sortedLessons.get(lessonIndex);

                boolean isLecture = currentLesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE;
                if (isLecture) {
                    if (lecturesToday > 0) break;
                } else {
                    if (practicesToday >= 2) break;
                }

                List<Lesson> chain = chainHandler.getChainForLesson(currentLesson, sortedLessons);

                if (chain.isEmpty() || (chain.size() > 1 && !chain.get(0).equals(currentLesson))) {
                    lessonIndex++;
                    continue;
                }

                if (chain.getFirst().getKindOfStudy() != ru.enums.KindOfStudy.LECTURE) {
                    practicesToday += chain.size();
                    lessonIndex += chain.size();
                    break;
                }

                if (chainHandler.tryPlaceChainInDay(chain, dayCells, lecturesToday == 0)) {
                    lessonIndex += chain.size();

                    for (Lesson l : chain) {
                        if (l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {
                            lecturesToday++;
                            practicesToday = 0;
                        } else {
                            practicesToday++;
                        }
                    }
                } else {
                    break;
                }
            }
        }

        // Лог остатка
        if (lessonIndex < sortedLessons.size()) {
            log.warn("=== WARN: Нераспределенные занятия для {} ===", educator.getName());
            log.warn("Всего не влезло: {}", (sortedLessons.size() - lessonIndex));

            for (int i = lessonIndex; i < sortedLessons.size(); i++) {
                Lesson l = sortedLessons.get(i);
                String themeNumber = l.getCurriculumSlot().getThemeLesson() != null
                        ? l.getCurriculumSlot().getThemeLesson().getThemeNumber()
                        : "N/A";
                log.warn("х Зан. НЕ размещено: {}/{}, {}, позиция в списке ={}, позиция в плане={}",
                        l.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                        themeNumber,
                        l.getStudyStream().getGroups(),
                        i, l.getCurriculumSlot().getPosition());
            }
        }
    }

    /**
     * Проверяет, доступен ли день для занятия.
     */
    private boolean isDayViableForLesson(LocalDate date, Lesson lesson) {
        List<CellForLesson> cells = CellForLessonFactory.getCellsForDate(date);
        for (CellForLesson cell : cells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) continue;
            if (context.getWorkspace().findPlacementOption(lesson, cell).isPossible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Создает список индексов для равномерного распределения.
     */
    private List<Integer> distributeLessonsEvenly(int totalAvailableDays, int requiredDays) {
        List<Integer> distribution = new ArrayList<>();

        if (requiredDays <= 0 || totalAvailableDays <= 0) {
            return distribution;
        }

        if (requiredDays <= totalAvailableDays) {
            float step = (float) totalAvailableDays / requiredDays;
            for (int i = 0; i < requiredDays; i++) {
                int day = Math.round(i * step);
                distribution.add(day);
            }
        } else {
            float step = (float) totalAvailableDays / requiredDays;
            for (int i = 0; i < requiredDays; i++) {
                int day = (int) (i * step);
                distribution.add(day);
            }
        }

        return distribution;
    }

    /**
     * Вычисляет необходимое количество дней для распределения.
     */
    private int calculateDaysForLessons(List<Lesson> lessons) {
        int days = 0;
        int lecturesToday = 0;
        int practicesToday = 0;

        for (Lesson lesson : lessons) {
            if (lesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {
                days++;
                lecturesToday = 1;
                practicesToday = 0;
            } else {
                if (practicesToday < 2) {
                    practicesToday++;
                    if (lecturesToday == 0 && practicesToday == 1) {
                        days++;
                    }
                } else {
                    days++;
                    practicesToday = 1;
                    lecturesToday = 0;
                }
            }
        }

        return days;
    }
}
