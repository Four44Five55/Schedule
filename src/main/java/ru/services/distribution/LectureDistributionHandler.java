package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.Educator;
import ru.entity.Lesson;
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
            if (educator.getId() == 301) {
                distributeForEducator(educator, semesterEnd);
            }
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

        int placedCount = 0;
        int alreadyPlacedCount = 0;
        int notPlacedCount = 0;

        for (Lesson lesson : lessons) {
            // Пропускаем уже размещённые
            if (context.isLessonDistributed(lesson)) {
                alreadyPlacedCount++;
                continue;
            }

            // Получаем дату из мапа
            LocalDate date = lessonToDateMap.get(lesson);
            if (date == null) {
                notPlacedCount++;
                log.warn("✗ Не назначена дата для: {}/{}",
                        lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                        lesson.getCurriculumSlot().getThemeLesson().getThemeNumber());
                continue;
            }

            // Размещаем занятие
            if (placement.place(lesson, date)) {
                placedCount++;
                context.addDistributedLesson(lesson);
                log.info("  ✓ {}/{}, тема: {}, дата: {}",
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
                String theme = l.getCurriculumSlot().getThemeLesson() != null
                        ? l.getCurriculumSlot().getThemeLesson().getThemeNumber()
                        : "N/A";
                log.warn("  {}/{}, тема: {}, группы: {}",
                        l.getKindOfStudy().getAbbreviationName(),
                        l.getCurriculumSlot().getPosition(),
                        theme,
                        l.getStudyStream().getGroups());
            }
        }
    }
}
