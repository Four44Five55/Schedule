package ru.services.distribution.merge;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.services.solver.model.ScheduleGrid;
import ru.services.distribution.core.MergeResult;
import ru.services.distribution.core.ScheduleMerger;
import ru.services.distribution.swap.SwapOption;
import ru.services.distribution.swap.SwapStrategy;
import ru.services.distribution.week.WeekSchedule;
import ru.services.distribution.week.WeeklyScheduleIterator;
import ru.services.statistics.ScheduleStatistics;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Базовая реализация объединения расписаний
 */
public class DefaultScheduleMerger implements ScheduleMerger {
    private final SwapStrategy swapStrategy;
    private final ScheduleStatistics statistics;
    private MergeResult lastMergeResult;

    public DefaultScheduleMerger(SwapStrategy swapStrategy, ScheduleStatistics statistics) {
        this.swapStrategy = swapStrategy;
        this.statistics = statistics;
    }

    @Override
    public boolean merge(ScheduleGrid targetSchedule, ScheduleGrid scheduleToMerge) {
        if (!canMerge(targetSchedule, scheduleToMerge)) {
            return false;
        }

        lastMergeResult = new MergeResult();
        return mergeByWeeks(targetSchedule, scheduleToMerge);
    }

    @Override
    public boolean canMerge(ScheduleGrid targetSchedule, ScheduleGrid scheduleToMerge) {
        return targetSchedule != null && 
               scheduleToMerge != null &&
               targetSchedule.getStartDate().equals(scheduleToMerge.getStartDate()) &&
               targetSchedule.getEndDate().equals(scheduleToMerge.getEndDate());
    }

    @Override
    public MergeResult getLastMergeResult() {
        return lastMergeResult;
    }

    private boolean mergeByWeeks(ScheduleGrid targetSchedule, ScheduleGrid scheduleToMerge) {
        WeeklyScheduleIterator weekIterator = new WeeklyScheduleIterator(
            scheduleToMerge.getStartDate(),
            scheduleToMerge.getEndDate(),
            scheduleToMerge
        );

        while (weekIterator.hasNext()) {
            WeekSchedule weekSchedule = weekIterator.next();
            if (!mergeWeek(targetSchedule, scheduleToMerge, weekSchedule)) {
                // Неделя объединена с конфликтами
            }
        }

        return !lastMergeResult.hasConflicts();
    }

    private boolean mergeWeek(ScheduleGrid targetSchedule, ScheduleGrid sourceSchedule, WeekSchedule week) {
        boolean weekSuccess = true;
        
        for (Map.Entry<LocalDate, List<CellForLesson>> dayEntry : week.dailySchedule.entrySet()) {
            LocalDate date = dayEntry.getKey();
            List<CellForLesson> dayCells = dayEntry.getValue();

            if (!mergeDay(targetSchedule, sourceSchedule, date, dayCells)) {
                weekSuccess = false;
            }
        }

        return weekSuccess;
    }

    private boolean mergeDay(ScheduleGrid targetSchedule, ScheduleGrid sourceSchedule, 
                           LocalDate date, List<CellForLesson> dayCells) {
        // Сортируем ячейки по временным слотам
        List<CellForLesson> sortedCells = dayCells.stream()
            .sorted(Comparator.comparing(CellForLesson::getTimeSlotPair))
            .collect(Collectors.toList());

        for (CellForLesson cell : sortedCells) {
            List<AbstractLesson> lessons = sourceSchedule.getListLessonInCell(cell);
            for (AbstractLesson lesson : lessons) {
                CellForLesson targetCell = new CellForLesson(date, cell.getTimeSlotPair());
                
                if (hasConflicts(targetSchedule, targetCell, lesson)) {
                    // Пытаемся найти вариант перестановки
                    Optional<SwapOption> swapOption = swapStrategy.findSwapOption(targetSchedule, targetCell, lesson);
                    
                    if (swapOption.isPresent()) {
                        // Выполняем перестановку
                        executeSwap(targetSchedule, swapOption.get());
                        lastMergeResult.addSuccessfulMerge(targetCell, lesson);
                    } else {
                        // Не удалось разрешить конфликт
                        String conflictDescription = createConflictDescription(targetSchedule, targetCell, lesson);
                        lastMergeResult.addConflict(targetCell, lesson, conflictDescription);
                        return false;
                    }
                } else {
                    // Добавляем занятие в исходную ячейку
                    targetSchedule.addLessonToCell(targetCell, lesson);
                    lastMergeResult.addSuccessfulMerge(targetCell, lesson);
                }
            }
        }

        return true;
    }

    private void executeSwap(ScheduleGrid schedule, SwapOption swap) {
        // Временно удаляем занятия
        schedule.removeLessonFromCell(swap.originalCell(), swap.originalLesson());
        schedule.removeLessonFromCell(swap.targetCell(), swap.targetLesson());

        // Добавляем занятия в новые позиции
        schedule.addLessonToCell(swap.targetCell(), swap.originalLesson());
        schedule.addLessonToCell(swap.originalCell(), swap.targetLesson());
    }

    private boolean hasConflicts(ScheduleGrid schedule, CellForLesson cell, AbstractLesson lesson) {
        List<AbstractLesson> existingLessons = schedule.getListLessonInCell(cell);
        
        for (AbstractLesson existingLesson : existingLessons) {
            // Проверяем пересечение групп
            if (hasGroupOverlap(existingLesson, lesson)) {
                return true;
            }
            
            // Проверяем занятость преподавателей
            if (!Collections.disjoint(existingLesson.getEducators(), lesson.getEducators())) {
                return true;
            }
            
            // Проверяем аудитории
            if (existingLesson.getAuditorium().equals(lesson.getAuditorium())) {
                return true;
            }
        }
        
        return false;
    }

    private boolean hasGroupOverlap(AbstractLesson lesson1, AbstractLesson lesson2) {
        return lesson1.getGroupCombinations().stream()
            .flatMap(gc1 -> gc1.getGroups().stream())
            .anyMatch(group1 -> 
                lesson2.getGroupCombinations().stream()
                    .flatMap(gc2 -> gc2.getGroups().stream())
                    .anyMatch(group2 -> group1.equals(group2)));
    }

    private String createConflictDescription(ScheduleGrid schedule, CellForLesson cell, AbstractLesson lesson) {
        StringBuilder description = new StringBuilder();
        List<AbstractLesson> existingLessons = schedule.getListLessonInCell(cell);
        
        description.append(String.format("Конфликт в слоте %s %s:%n", 
            cell.getDate(), cell.getTimeSlotPair()));
            
        for (AbstractLesson existing : existingLessons) {
            if (hasGroupOverlap(existing, lesson)) {
                description.append("- Пересечение групп\n");
            }
            if (!Collections.disjoint(existing.getEducators(), lesson.getEducators())) {
                description.append("- Преподаватель занят\n");
            }
            if (existing.getAuditorium().equals(lesson.getAuditorium())) {
                description.append("- Аудитория занята\n");
            }
        }
        
        return description.toString();
    }
} 