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

    /**
     * Проверяет, является ли данный тип занятия лекционным.
     *
     * @return true, если это лекция.
     */
    public boolean isLectureType() {
        return this == LECTURE;
    }

    /**
     * Проверяет, можно ли менять занятия этого типа с занятиями другого типа.
     *
     * @param other другой тип занятия.
     * @return true, если перестановка разрешена.
     */
    public boolean isInterchangeableWith(KindOfStudy other) {
        if (this.isLectureType() || other.isLectureType()) {
            // Если хотя бы одно из занятий - лекция, они должны быть строго одного типа.
            return this == other;
        }
        // Если оба занятия - не лекции (практики, семинары и т.д.), их можно менять между собой.
        return true;
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