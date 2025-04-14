package ru.entity;

import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Priority {
    private final List<DayOfWeek> dayOfWeeks = new ArrayList<>();
    private final List<TimeSlotPair> slotPairs = new ArrayList<>();

    public Priority() {

    }

    public void addDefaultDays() {
        dayOfWeeks.addAll(Arrays.asList(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        ));
    }

    public void addDefaultTimeSlots() {
        slotPairs.addAll(Arrays.asList(
                TimeSlotPair.FIRST, TimeSlotPair.SECOND, TimeSlotPair.THIRD
        ));
    }

    public List<DayOfWeek> getDayOfWeeks() {
        return Collections.unmodifiableList(dayOfWeeks);
    }

    public List<TimeSlotPair> getSlotPairs() {
        return Collections.unmodifiableList(slotPairs);
    }
}
