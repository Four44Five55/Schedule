package ru.enums;

public enum KindOfStudy {

    LECTURE("Лекция", "Л", Category.LECTURE),
    PRACTICAL_WORK("Практическое занятие", "ПЗ", Category.PRACTICE),
    LAB_WORK("Лабораторная работа", "ЛР", Category.PRACTICE),
    SEMINAR("Семинар", "С", Category.PRACTICE),
    GROUP_WORK("Групповое занятие", "ГЗ", Category.PRACTICE),
    GROUP_EXERCISE("Групповое упражнение", "ГУ", Category.PRACTICE),
    QUIZ("Контрольная работа", "КР", Category.PROGRESS_CHECK),
    // ИКС по смыслу ближе к текущему контролю, но исторически ведёт себя как практическое занятие
    // (в подсветке и в бланке) — категория оставлена PRACTICE, чтобы этот перевод ничего не менял
    // в поведении. Если решишь, что ИКС — контроль, поменяй здесь: одной этой строки хватит.
    INDIVIDUAL_REVIEW_INTERVIEW("Индивидуальное контрольное собеседование", "ИКС", Category.PRACTICE),
    CREDIT_WITH_GRADE("Зачет с оценкой", "ЗО", Category.ASSESSMENT),
    CREDIT_WITHOUT_GRADE("Зачет без оценки", "ЗЧ", Category.ASSESSMENT),
    EXAM("Экзамен", "ЭКЗ", Category.ASSESSMENT),
    INDEPENDENT_STUDY("Самостоятельная работа", "СР", Category.PRACTICE);

    /**
     * Категория вида занятия — ЕДИНСТВЕННЫЙ владелец классификации на весь проект.
     *
     * <p>Раньше «лекционность» и «аттестационность» проверялись сравнением с константами, а список
     * аттестаций был выписан в четырёх местах бэка и ещё в двух на фронте. Новый вид занятия
     * приходилось вносить в каждое, и забытое место молча вело себя неправильно: аттестация вставала
     * в середину курса, теряла заливку в бланке, красилась как практика. Теперь категория
     * объявляется ОДИН раз — в строке самого вида, — и её же получает фронт через {@code /api/enums}.</p>
     *
     * <p>Названа {@code Category}, а не {@code Group}: «группа» в этом проекте — учебная группа
     * ({@link ru.entity.Group}), и два разных {@code Group} в одном файле читались бы как ловушка.</p>
     *
     * <ul>
     *   <li>{@code LECTURE} — читается потоку, задаёт порядок изучения: практика по теме идёт
     *       после своей лекции. Виды этой категории не переставляются с другими.</li>
     *   <li>{@code PRACTICE} — отрабатывается группой после лекции (практика, лаба, семинар,
     *       групповые формы, самоподготовка). Взаимозаменяемы между собой.</li>
     *   <li>{@code PROGRESS_CHECK} — текущий контроль внутри курса (контрольная работа).</li>
     *   <li>{@code ASSESSMENT} — итоговая аттестация в конце курса (зачёты, экзамен): её отрыв от
     *       последней лекции нормален, и в бланке она заливается темнее.</li>
     * </ul>
     *
     * <p>Добавляя вид занятия, укажи его категорию — этого достаточно, чтобы распределение, правило
     * порядка, экспорт и подсветка на фронте повели себя правильно без единой правки в них.</p>
     */
    public enum Category {
        LECTURE,
        PRACTICE,
        PROGRESS_CHECK,
        ASSESSMENT
    }

    private final String fullName;
    private final String abbreviationName;
    private final Category category;

    KindOfStudy(String fullName, String abbreviationName, Category category) {
        this.fullName = fullName;
        this.abbreviationName = abbreviationName;
        this.category = category;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviationName() {
        return abbreviationName;
    }

    public Category getCategory() {
        return category;
    }

    /**
     * Проверяет, является ли данный тип занятия лекционным.
     *
     * @return true, если это лекция.
     */
    public boolean isLectureType() {
        return category == Category.LECTURE;
    }

    /**
     * Проверяет, является ли вид занятия аттестацией (экзамен, зачёт с оценкой, зачёт без оценки).
     *
     * <p>Признак выводится из {@link Category} — списка видов здесь намеренно нет, см. комментарий
     * к самой категории. Фронт получает её через {@code /api/enums/kind-of-study} и своей копии
     * не держит.</p>
     *
     * @return true, если вид завершает курс аттестацией.
     */
    public boolean isAssessment() {
        return category == Category.ASSESSMENT;
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