package ru.enums;

public enum DayOfWeek {
    MONDAY("Понедельник", "Пн"),
    TUESDAY("Вторник", "Вт"),
    WEDNESDAY("Среда", "Ср"),
    THURSDAY("Четверг", "Чт"),
    FRIDAY("Пятница", "Пт"),
    SATURDAY("Суббота", "Сб"),
    SUNDAY("Воскресенье", "Вс");

    private final String fullName;
    private final String abbreviation;

    DayOfWeek(String fullName, String abbreviation) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public java.time.DayOfWeek toJavaTimeDayOfWeek() {
        return java.time.DayOfWeek.valueOf(this.name());
    }

    public static DayOfWeek fromJavaTimeDayOfWeek(java.time.DayOfWeek day) {
        return DayOfWeek.valueOf(day.name());
    }
}