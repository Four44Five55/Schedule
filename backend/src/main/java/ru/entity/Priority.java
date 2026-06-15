package ru.entity;

import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

import java.util.*;

public class Priority {
    private final Set<DayOfWeek> dayOfWeeks = new HashSet<>();
    private final Set<TimeSlotPair> slotPairs = new HashSet<>();

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
                TimeSlotPair.FIRST,
                TimeSlotPair.SECOND,
                TimeSlotPair.THIRD
        ));
    }


    public void addPriorityDay(DayOfWeek dayOfWeek) {
        if (dayOfWeek != null) {
            dayOfWeeks.add(dayOfWeek);
        }
    }


    public void addPriorityDays(Collection<DayOfWeek> days) {
        if (days != null) {
            dayOfWeeks.addAll(days);
        }
    }


    public void removePriorityDay(DayOfWeek dayOfWeek) {
        if (dayOfWeek != null) {
            dayOfWeeks.remove(dayOfWeek);
        }
    }


    public void addTimeSlot(TimeSlotPair timeSlot) {
        if (timeSlot != null) {
            slotPairs.add(timeSlot);
        }
    }

    public void addTimeSlots(Collection<TimeSlotPair> timeSlots) {
        if (timeSlots != null) {
            slotPairs.addAll(timeSlots);
        }
    }

    public void removeTimeSlot(TimeSlotPair timeSlot) {
        if (timeSlot != null) {
            slotPairs.remove(timeSlot);
        }
    }

    // Возвращает неизменяемые копии для безопасности
    public Set<DayOfWeek> getDayOfWeeks() {
        return Collections.unmodifiableSet(dayOfWeeks);
    }

    public Set<TimeSlotPair> getSlotPairs() {
        return Collections.unmodifiableSet(slotPairs);
    }
}