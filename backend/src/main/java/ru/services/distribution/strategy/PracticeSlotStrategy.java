package ru.services.distribution.strategy;

import ru.enums.TimeSlotPair;
import java.util.Set;

/**
 * Стратегия выбора временных слотов для размещения практик.
 * Определяет базовые ограничения по парам.
 * Ограничение на 1-ю пару добавляется динамически в LessonPlacementService
 * на основе наличия лекции в конкретный день.
 */
public interface PracticeSlotStrategy {

    /**
     * Базовые пары для пропуска (без учёта 1-й пары — она проверяется динамически).
     */
    Set<TimeSlotPair> getSkipPairs();

    /**
     * Следующий уровень стратегии при нехватке слотов.
     * Если стратегия является потолком — возвращает себя.
     */
    PracticeSlotStrategy escalate();

    String getName();
}