package ru.services.distribution.strategy;

import ru.enums.TimeSlotPair;
import java.util.Set;

/**
 * Уровень 2 — расширенная стратегия.
 * Разрешает все пары включая 4-ю.
 * 1-я пара по-прежнему запрещается динамически если в этот день есть лекция.
 */
public class ExtendedPracticeStrategy implements PracticeSlotStrategy {

    @Override
    public Set<TimeSlotPair> getSkipPairs() {
        return Set.of(); // базово ничего не пропускаем
    }

    @Override
    public PracticeSlotStrategy escalate() {
        return this; // потолок
    }

    @Override
    public String getName() {
        return "расширенная (все пары включая 4-ю)";
    }
}