package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для размещения занятий.
 * Упрощённая версия, объединяющая LessonPlacementService и PlacementValidator.
 */
@Slf4j
public class LessonPlacementService {
    private final ScheduleWorkspace workspace;
    private final DistributionContext context;

    public LessonPlacementService(ScheduleWorkspace workspace, DistributionContext context) {
        this.workspace = workspace;
        this.context = context;
    }

    /**
     * Проверяет, можно ли разместить занятие в указанную дату.
     */
    public boolean canPlace(Lesson lesson, LocalDate date) {
        return canPlace(lesson, date, DEFAULT_SKIP);
    }

    /**
     * Проверяет, можно ли разместить занятие в указанную дату с ограничениями по парам.
     *
     * @param lesson    занятие
     * @param date      дата
     * @param skipPairs пары, которые нужно пропустить
     */
    public boolean canPlace(Lesson lesson, LocalDate date, Set<TimeSlotPair> skipPairs) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        for (CellForLesson cell : dayCells) {
            if (shouldSkipCell(cell, skipPairs)) continue;

            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Размещает занятие в указанную дату.
     *
     * @return true если размещение успешно
     */
    public boolean place(Lesson lesson, LocalDate date) {
        return place(lesson, date, DEFAULT_SKIP);
    }

    /**
     * Размещает занятие в указанную дату с ограничениями по парам.
     *
     * @param lesson    занятие
     * @param date      дата
     * @param skipPairs пары, которые нужно пропустить
     * @return true если размещение успешно
     */
    public boolean place(Lesson lesson, LocalDate date, Set<TimeSlotPair> skipPairs) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        for (CellForLesson cell : dayCells) {
            if (shouldSkipCell(cell, skipPairs)) continue;

            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (option.isPossible()) {
                workspace.executePlacement(option);
                context.addDistributedLesson(lesson);
/*
                String theme = lesson.getCurriculumSlot().getThemeLesson() == null ? "N/A" : lesson.getCurriculumSlot().getThemeLesson().getThemeNumber();
                log.info("✓ {} - {}/{}  размещено на {}", lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                        lesson.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                        theme,
                        date);
*/

                return true;
            }
        }
/*        String theme = lesson.getCurriculumSlot().getThemeLesson() == null ? "N/A" : lesson.getCurriculumSlot().getThemeLesson().getThemeNumber();
        log.warn("✗ {} - {}/{}  НЕ размещено на {}", lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                lesson.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                theme,
                date);*/
        return false;
    }

    /**
     * Определяет итоговые skipPairs для конкретной даты.
     * Если в этот день уже размещена лекция для участников занятия —
     * добавляет FIRST в skipPairs (1-я пара запрещена).
     * После фазы 1 все лекции уже размещены, поэтому проверка корректна.
     *
     * @param lesson        занятие для размещения
     * @param date          целевая дата
     * @param baseSkipPairs базовые ограничения из стратегии
     * @return итоговые skipPairs с учётом наличия лекции в этот день
     */
    public Set<TimeSlotPair> resolveSkipPairs(Lesson lesson,
                                              LocalDate date,
                                              Set<TimeSlotPair> baseSkipPairs) {
        if (hasLectureOnDate(lesson, date)) {
            Set<TimeSlotPair> result = new HashSet<>(baseSkipPairs);
            result.add(TimeSlotPair.FIRST);
            return Collections.unmodifiableSet(result);
        }
        return baseSkipPairs;
    }

    /**
     * Проверяет, есть ли лекция в указанный день у участников занятия.
     */
    private boolean hasLectureOnDate(Lesson lesson, LocalDate date) {
        return context.getDistributedLessons().stream()
                .filter(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
                .filter(l -> {
                    CellForLesson cell = context.getWorkspace().getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .anyMatch(l -> sharesParticipants(l, lesson));
    }

    /**
     * Проверяет пересечение участников двух занятий.
     */
    private boolean sharesParticipants(Lesson a, Lesson b) {
        boolean sharedEducator = a.getEducators().stream()
                .anyMatch(e -> b.getEducators().contains(e));
        if (sharedEducator) return true;

        if (a.getStudyStream() == null || b.getStudyStream() == null) return false;
        return a.getStudyStream().getGroups().stream()
                .anyMatch(g -> b.getStudyStream().getGroups().contains(g));
    }
    public String explainWhyCannotPlace(Lesson lesson, LocalDate date, Set<TimeSlotPair> skipPairs) {
        // Проверяем каждый ресурс отдельно
        List<String> reasons = new ArrayList<>();

        List<CellForLesson> cells = CellForLessonFactory.getCellsForDate(date).stream()
                .filter(c -> !skipPairs.contains(c.getTimeSlotPair()))
                .toList();

        if (cells.isEmpty()) {
            return "нет пар в этот день (все в skipPairs)";
        }

        boolean educatorFree = cells.stream().anyMatch(c ->
                lesson.getEducators().stream().allMatch(e ->
                        context.getWorkspace().getResourceManager()
                                .getEducatorResource(e.getId()).isFree(c)));

        boolean groupFree = cells.stream().anyMatch(c ->
                lesson.getStudyStream().getGroups().stream().allMatch(g ->
                        context.getWorkspace().getResourceManager()
                                .getGroupResource(g.getId()).isFree(c)));

        if (!educatorFree) reasons.add("преподаватель занят на всех парах");
        if (!groupFree) reasons.add("группа занята на всех парах");

        return reasons.isEmpty() ? "аудитория недоступна" : String.join(", ", reasons);
    }
    /**
     * Размещает занятие в первую доступную ячейку указанной даты.
     */
    public boolean placeInDay(Lesson lesson, LocalDate date) {
        return place(lesson, date);
    }

    /**
     * Находит первую доступную дату для занятия из списка.
     */
    public LocalDate findAvailableDate(Lesson lesson, List<LocalDate> dates) {
        for (LocalDate date : dates) {
            if (canPlace(lesson, date)) {
                return date;
            }
        }
        return null;
    }

    // ========== Константы ограничений ==========

    /**
     * Стандартное ограничение: пропускаем только 4-ю пару.
     */
    public static final Set<TimeSlotPair> DEFAULT_SKIP = Set.of(TimeSlotPair.FOURTH);

    /**
     * Ограничение для практик: пропускаем 1-ю и 4-ю пары.
     */
    public static final Set<TimeSlotPair> PRACTICE_SKIP = Set.of(TimeSlotPair.FIRST, TimeSlotPair.FOURTH);

    /**
     * Ограничение для лекций: пропускаем только 4-ю пару.
     */
    public static final Set<TimeSlotPair> LECTURE_SKIP = Set.of(TimeSlotPair.FOURTH);

    /**
     * Проверяет, следует ли пропустить ячейку на основе ограничений.
     *
     * @param cell      ячейка для проверки
     * @param skipPairs набор пар, которые нужно пропустить
     * @return true если ячейку нужно пропустить
     */
    public static boolean shouldSkipCell(CellForLesson cell, Set<TimeSlotPair> skipPairs) {
        return skipPairs.contains(cell.getTimeSlotPair());
    }

    /**
     * Проверяет, следует ли пропустить ячейку (4-я пара).
     *
     * @deprecated Используйте {@link #shouldSkipCell(CellForLesson, Set)}
     */
    @Deprecated
    private boolean shouldSkipCell(CellForLesson cell) {
        return shouldSkipCell(cell, DEFAULT_SKIP);
    }

    /**
     * Получает список занятий преподавателя.
     */
    public List<Lesson> getLessonsForEducator(Educator educator) {
        return context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .collect(Collectors.toList());
    }

    /**
     * Получает лекции преподавателя.
     */
    public List<Lesson> getLecturesForEducator(Educator educator) {
        return context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    /**
     * Получает практики преподавателя (не лекции).
     */
    public List<Lesson> getPracticesForEducator(Educator educator) {
        return context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    /**
     * Получает даты уже размещённых лекций преподавателя.
     *
     * @deprecated Используйте {@link #getOccupiedDates(Educator)} для учёта всех занятий
     */
    @Deprecated
    public Set<LocalDate> getLectureDates(Educator educator) {
        return context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE)
                .map(l -> workspace.getCellForLesson(l))
                .filter(cell -> cell != null)
                .map(CellForLesson::getDate)
                .collect(Collectors.toSet());
    }

    /**
     * Получает даты всех уже размещённых занятий преподавателя.
     * Используется для приоритизации дат при размещении практик —
     * занятия предпочитается размещать в дни, когда у преподавателя уже есть занятия.
     *
     * @param educator преподаватель
     * @return множество дат, в которые у преподавателя есть занятия
     */
    public Set<LocalDate> getOccupiedDates(Educator educator) {
        return context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .map(l -> workspace.getCellForLesson(l))
                .filter(cell -> cell != null)
                .map(CellForLesson::getDate)
                .collect(Collectors.toSet());
    }

    /**
     * Получает список доступных дат для указанного занятия.
     */
    public List<LocalDate> getAvailableDates(Lesson lesson, LocalDate startDate, LocalDate endDate) {
        return CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isBefore(startDate))
                .filter(d -> !d.isAfter(endDate))
                .filter(d -> canPlace(lesson, d))
                .sorted()
                .collect(Collectors.toList());
    }
}
