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
     * @param source    значение из файла, ради которого строка заводилась
     * @param id        id заведённой сущности; {@code null} — пропущено
     * @param name      как назвали
     * @param note      чего не хватает у заведённой строки либо причина пропуска
     * @param files     файлы, откуда значение приехало (первые несколько)
     * @param fileCount во скольких файлах всего встретилось
     * @param derived   строка производная (поток из одной группы) — на экране сворачивается
     */
    public record CreatedRow(String source, Integer id, String name, String note,
                             List<String> files, int fileCount, boolean derived) {

        /**
         * Строка о судьбе значения из сверки.
         *
         * <p>Через фабрику, а не конструктором: провенанс должен доехать <b>из той же строки
         * сверки</b>, по которой заведение и решалось, — иначе «заведено как X» приходится
         * сверять с файлом вручную, а именно этого мы и избегаем. Забыть его так нельзя.</p>
         *
         * @param from строка сверки, по которой шло заведение
         * @param id   id заведённой сущности; {@code null} — пропущено
         * @param name как назвали
         * @param note чего не хватает либо почему пропущено
         */
        public static CreatedRow of(ImportMatchReport.MatchRow from, Integer id, String name, String note) {
            return new CreatedRow(from.source(), id, name, note,
                    from.files(), from.fileCount(), from.derived());
        }
    }
}
