package ru.enums;

public enum KindOfStudy {

    LECTURE("Лекция", "Л"),
    PRACTICAL_WORK("Практическое занятие", "ПЗ"),
    LAB_WORK("Лабораторная работа", "ЛР"),
    SEMINAR("Семинар", "С"),
    GROUP_WORK("Групповое занятие", "ГЗ"),
    GROUP_EXERCISE("Групповое упражнение", "ГУ"),
    QUIZ("Контрольная работа", "КР"),
    INDIVIDUAL_REVIEW_INTERVIEW("Индивидуальное контрольное собеседование", "ИКС"),
    CREDIT_WITH_GRADE("Зачет с оценкой", "ЗО"),
    CREDIT_WITHOUT_GRADE("Зачет без оценки", "ЗЧ"),
    EXAM("Экзамен", "ЭКЗ"),
    INDEPENDENT_STUDY("Самостоятельная работа", "СР");
    private final String fullName;
    private final String abbreviationName;

    KindOfStudy(String fullName, String abbreviationName) {
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
/*Лекция – Lecture
 * Семинар – Seminar
 * Практическое занятие – Practical Class
 * Лабораторная работа – Lab Work / Laboratory Session
 * Групповое занятие – Group Class / Group Session
 * Групповое упражнение – Group Exercise
 * Контрольная работа – Test / Quiz
 * Зачет с оценкой "Graded Pass/Fail Exam" или "Credit with Grade".
 * Зачет без оценки "Pass/Fail Exam" или "Credit without Grade".
 * Самостоятельная работа – Independent Study
 *  */