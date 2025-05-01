package ru.enums;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

public enum BlockedDaysAndSlots {
    WEEKDAY(day -> Collections.emptySet()), // Пн–Чт без ограничений
    FRIDAY(day -> Set.of(TimeSlotPair.FOURTH)), // Пт: 4-я пара запрещена
    SATURDAY(day -> Set.of(TimeSlotPair.FOURTH)), // Сб: 4-я пара запрещена
    SUNDAY(day -> Set.of(TimeSlotPair.values())); // Вс: все пары запрещены

    private final Function<LocalDate, Set<TimeSlotPair>> exclusionProvider;

    BlockedDaysAndSlots(Function<LocalDate, Set<TimeSlotPair>> exclusionProvider) {
        this.exclusionProvider = exclusionProvider;
    }

    public Set<TimeSlotPair> getExcludedSlots(LocalDate date) {
        return exclusionProvider.apply(date);
    }
}
