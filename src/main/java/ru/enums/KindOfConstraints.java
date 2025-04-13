package ru.enums;

public enum KindOfConstraints {
    BUSINESS_TRIP("Командировка", "Ком"),
    VACATION("Отпуск", "Отп"),
    EXAM_SESSION("Экзаменационная сессия", "ЭкзС"),
    MEDICAL_CARE("Углубленно-медицинское обеспечение", "УМО"),
    LIBRARY("Библиотека", "Биб"),
    FINAL_STATE_ATTESTATION("Государственная итоговая аттестация", "ГИА"),
    OTHER("Другой вид ограничения", "ДВО");

    private final String fullName;
    private final String abbreviationName;

    KindOfConstraints(String fullName, String abbreviationName) {
        this.fullName = fullName;
        this.abbreviationName = abbreviationName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviationName() {
        return abbreviationName;
    }
}
