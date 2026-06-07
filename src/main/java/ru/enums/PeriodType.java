package ru.enums;

public enum PeriodType {
    FALL_SEMESTER("Осенний семестр", "Осень"),
    SPRING_SEMESTER("Весенний семестр", "Весна"),
    FALL_EXAM_SESSION("Осенняя сессия", "Осенняя ЭкзС"),
    SPRING_EXAM_SESSION("Весенняя сессия", "Весенняя ЭкзС");

    private final String fullName;
    private final String abbreviation;

    PeriodType(String fullName, String abbreviation) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

}
