package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Обрабатывает фазу 1 — распределение лекций.
 */
@Slf4j
public class LectureDistributionHandler {
    private final DistributionContext context;
    private final LessonPlacementService placement;

    public LectureDistributionHandler(DistributionContext context,
                                      LessonPlacementService placement) {
        this.context = context;
        this.placement = placement;
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

        List<Lesson> lessons = placement.getLessonsForEducator(educator);
        if (lessons.isEmpty()) {
            log.info("Нет занятий для распределения");
            return;
        }

        List<LocalDate> availableDates = getAvailableDates(semesterEnd, lessons);
        if (availableDates.isEmpty()) {
            log.error("Нет доступных дат для {}", educator.getName());
            return;
        }

        log.info("Доступных дней: {}, занятий: {}", availableDates.size(), lessons.size());

        int placedCount = 0;
        int alreadyPlacedCount = 0;
        int notPlacedCount = 0;

        for (Lesson lesson : lessons) {
            // Пропускаем уже размещённые
            if (context.isLessonDistributed(lesson)) {
                alreadyPlacedCount++;
                continue;
            }

            // Ищем дату для занятия
            LocalDate date = findDateForLesson(lesson, availableDates);
            if (date == null) {
                notPlacedCount++;
                log.warn("✗ Не найдена дата для: {}/{}",
                        lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                        lesson.getCurriculumSlot().getThemeLesson().getThemeNumber());
                continue;
            }

            // Размещаем занятие
            if (placement.place(lesson, date)) {
                placedCount++;
                context.addDistributedLesson(lesson);
                log.info("  {}/{}, тема: {}, преподаватель: {}, группы: {}",
                        lesson.getKindOfStudy().getAbbreviationName(),
                        lesson.getCurriculumSlot().getPosition(),
                        lesson.getCurriculumSlot().getThemeLesson().getThemeNumber(),
                        lesson.getEducators(),
                        lesson.getStudyStream().getGroups());
            } else {
                notPlacedCount++;
                log.warn("✗ Не удалось разместить занятие на {}", date);
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
     * Ищет дату для размещения занятия.
     */
    private LocalDate findDateForLesson(Lesson lesson, List<LocalDate> dates) {
        // Сначала проверяем первую дату в списке
        if (!dates.isEmpty() && placement.canPlace(lesson, dates.getFirst())) {
            return dates.getFirst();
        }

        // Ищем любую доступную дату
        for (LocalDate date : dates) {
            if (placement.canPlace(lesson, date)) {
                return date;
            }
        }
        return null;
    }

    /**
     * Получает список доступных дат для занятий.
     */
    private List<LocalDate> getAvailableDates(LocalDate semesterEnd, List<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return List.of();
        }

        Lesson prototype = lessons.getFirst();
        return CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isAfter(semesterEnd))
                .filter(d -> placement.canPlace(prototype, d))
                .sorted()
                .toList();
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
