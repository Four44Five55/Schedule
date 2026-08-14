package ru.services.importing;

import java.util.List;

/**
 * Что импорт завёл в справочниках и чего не стал.
 *
 * <p><b>Пропуски здесь не менее важны, чем создания.</b> Заводится только то, что сверка назвала
 * отсутствующим (`MISSING`); неоднозначное и непрочитанное пропускается с причиной — завести ещё
 * одну строку поверх неоднозначности значило бы сделать её вечной.</p>
 *
 * @param sections по разделу на вид сущности, в порядке зависимостей
 */
public record ImportCreationReport(List<CreationSection> sections) {

    /**
     * @param title   вид сущности: «Преподаватели»
     * @param created сколько заведено
     * @param skipped сколько пропущено (с причинами в строках)
     * @param rows    что именно
     */
    public record CreationSection(String title, int created, int skipped, List<CreatedRow> rows) {
    }

    /**
     * @param source значение из файла, ради которого строка заводилась
     * @param id     id заведённой сущности; {@code null} — пропущено
     * @param name   как назвали
     * @param note   чего не хватает у заведённой строки либо причина пропуска
     */
    public record CreatedRow(String source, Integer id, String name, String note) {
    }
}
