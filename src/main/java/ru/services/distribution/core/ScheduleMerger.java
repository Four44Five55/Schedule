package ru.services.distribution.core;

import ru.entity.ScheduleGrid;

/**
 * Интерфейс для стратегий объединения расписаний
 */
public interface ScheduleMerger {
    /**
     * Объединяет новое расписание с существующим
     */
    boolean merge(ScheduleGrid targetSchedule, ScheduleGrid scheduleToMerge);

    /**
     * Проверяет возможность объединения расписаний
     */
    boolean canMerge(ScheduleGrid targetSchedule, ScheduleGrid scheduleToMerge);

    /**
     * Получает информацию о последнем объединении
     */
    MergeResult getLastMergeResult();
} 