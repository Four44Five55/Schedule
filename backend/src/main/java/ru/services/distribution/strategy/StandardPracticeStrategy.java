package ru.services.distribution.strategy;

import ru.enums.TimeSlotPair;
import java.util.Set;

/**
 * Уровень 1 — стандартная стратегия.
 * Базово запрещает только 4-ю пару.
 * 1-я пара запрещается динамически если в этот день есть лекция.
 */
public class StandardPracticeStrategy implements PracticeSlotStrategy {

    @Override
    public Set<TimeSlotPair> getSkipPairs() {
        return Set.of(TimeSlotPair.FOURTH);
    }

    @Override
    public PracticeSlotStrategy escalate() {
        return new ExtendedPracticeStrategy();
    }

    @Override
    public String getName() {
        return "стандартная (1-3 пары, без 4-й)";
    }
}