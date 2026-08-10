package ru.enums;

/**
 * Учёное звание преподавателя.
 *
 * <p><b>Не путать с должностью.</b> «Доцент» бывает и учёным званием, и должностью
 * (доцент кафедры), и это разные факты о человеке: звание присваивается пожизненно, должность
 * меняется с местом работы. Должность в модели сейчас отсутствует сознательно (решение
 * заказчика); когда понадобится — это отдельное поле и отдельный справочник, а не новое значение
 * здесь.</p>
 *
 * <p>Enum, а не справочник, по тому же критерию, что и {@link AcademicDegree}: два значения,
 * заданы нормативкой. Копия строк — в таблице {@code academic_title} ради FK; источник правды
 * здесь. Сокращения без точек — их расставляет форматтер {@code EducatorTitles}.</p>
 */
public enum AcademicTitle {

    ASSOCIATE_PROFESSOR("доцент", "доц"),
    PROFESSOR("профессор", "проф");

    private final String fullName;
    private final String abbreviation;

    AcademicTitle(String fullName, String abbreviation) {
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
