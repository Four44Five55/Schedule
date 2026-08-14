package ru.enums;

/**
 * Уровень учёной степени.
 *
 * <p><b>Почему enum, а не справочник</b> (в отличие от {@code special_rank} и
 * {@code science_branch}): значений ровно два, задаются нормативкой, добавлять их пользователю
 * нечего. Критерий один и тот же во всём проекте — кто добавляет значение и требует ли добавление
 * релиза. Копия строк лежит в таблице {@code academic_degree} ради FK и читаемости сырого SQL,
 * как {@link KindOfStudy} при {@code kind_of_study}; источник правды — этот enum.</p>
 *
 * <p><b>Степень двумерна:</b> «ктн» = уровень (кандидат) × отрасль науки (технические).
 * Отрасль живёт отдельным справочником и отдельным полем, а не в именах констант: иначе
 * получился бы перечень из N×M комбинаций, где каждая новая отрасль добавляет две константы.
 * Собирает их вместе {@code EducatorTitles} — здесь только части.</p>
 *
 * <p>Сокращения — <b>без точек</b>, и склеиваются они тоже без точек (уточнено 2026-08-14: было
 * «к.т.н.», стало «ктн» — так пишут документы, из которых мы читаем данные). {@code abbreviation} —
 * буквенная часть составного сокращения («к» → «ктн»), {@code standalone} — форма для случая, когда
 * отрасль не указана («канд наук»): записи «кн» не существует.</p>
 */
public enum AcademicDegree {

    CANDIDATE("кандидат наук", "к", "канд наук"),
    DOCTOR("доктор наук", "д", "д-р наук");

    private final String fullName;
    private final String abbreviation;
    private final String standalone;

    AcademicDegree(String fullName, String abbreviation, String standalone) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
        this.standalone = standalone;
    }

    public String getFullName() {
        return fullName;
    }

    /** Буквенная часть составного сокращения: «к» в «ктн». */
    public String getAbbreviation() {
        return abbreviation;
    }

    /** Сокращение без отрасли науки: «канд наук». */
    public String getStandalone() {
        return standalone;
    }
}
