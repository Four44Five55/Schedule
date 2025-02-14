package ru.enums;

import java.time.LocalTime;

public enum TimeSlotPair {
    FIRST(9, 0, 10, 35),
    SECOND(10, 55, 12, 30),
    THIRD(12, 50, 14, 25),
    FOURTH(16, 20, 17, 55);

    private final LocalTime startTime;
    private final LocalTime endTime;

    TimeSlotPair(int startHour, int startMinute, int endHour, int endMinute) {
        this.startTime = LocalTime.of(startHour, startMinute);
        this.endTime = LocalTime.of(endHour, endMinute);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
