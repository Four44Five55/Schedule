package ru.services.distribution.swap;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.entity.ScheduleGrid;

import java.util.Optional;

/**
 * Интерфейс для стратегий перестановки занятий при конфликтах
 */
public interface SwapStrategy {
    /**
     * Ищет возможность перестановки для конфликтующего занятия
     * @param schedule текущее расписание
     * @param conflictCell ячейка с конфликтом
     * @param lesson занятие, которое нужно переставить
     * @return Optional с вариантом перестановки, если она возможна
     */
    Optional<SwapOption> findSwapOption(ScheduleGrid schedule, CellForLesson conflictCell, AbstractLesson lesson);
} 