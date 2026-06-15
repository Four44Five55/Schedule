package ru.enums;

import java.time.LocalTime;

public enum TimeSlotPair {
    FIRST("1-я пара", "1", 9, 0, 10, 35),
    SECOND("2-я пара", "2", 10, 55, 12, 30),
    THIRD("3-я пара", "3", 12, 50, 14, 25),
    FOURTH("4-я пара", "4", 16, 20, 17, 55);

    private final String fullName;
    private final String abbreviation;
    private final LocalTime startTime;
    private final LocalTime endTime;

    TimeSlotPair(String fullName, String abbreviation,
                 int startHour, int startMinute, int endHour, int endMinute) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
        this.startTime = LocalTime.of(startHour, startMinute);
        this.endTime = LocalTime.of(endHour, endMinute);
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    /**
     * Форматированное время: "09:00 – 10:35"
     */
    public String getTimeRange() {
        return startTime + " – " + endTime;
    }
}
