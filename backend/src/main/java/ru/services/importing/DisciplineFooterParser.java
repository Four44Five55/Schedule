package ru.services.importing;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import ru.services.importing.ParsedSheet.CutKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Подвал группового файла — перечень дисциплин с преподавателями.
 *
 * <p>Таблица под полотном: {@code Обозн | Дисциплина | Каф. | Лектор, уч. степень, уч. звание |
 * Другие виды занятий | Кол-во часов | Отчет | Поток лекционный | Дом.задание…}. Ради неё этот
 * разбор и написан: <b>групповая ячейка преподавателя не несёт</b>, а без преподавателя строка не
 * разрешается в {@code Assignment} — размещать нечем. Подвал закрывает атрибуцию, пока у дисциплины
 * в группе один лектор и один практик (решение И-14).</p>
 *
 * <p><b>Колонки ищутся по заголовку, а не по номеру.</b> Справа от «Потока» идёт группа колонок под
 * общим заголовком с {@code colspan}, и позиционный счёт сломался бы на первом же файле, где её
 * нет. Заголовок сравнивается по началу строки: «Лектор, уч. степень, уч. звание» — это «лектор».</p>
 *
 * <p><b>Значения отдаются как в файле</b> — по тому же правилу, что и в {@link CellDialect}. Часы
 * «18-30» не разбираются на слагаемые, звание и степень от фамилии не отделяются (для этого нужен
 * справочник {@code special_rank}, а это уже слой сопоставления, а не разбора). Единственное
 * толкование, которое здесь делается, — <b>разделение перечня преподавателей по «;»</b>, потому что
 * это разделитель самого файла, а не наша догадка о его смысле.</p>
 *
 * <p><b>Разбор тотален</b> ({@link ScheduleSheetParser}, {@link GroupNumberDecoder}): кривизна
 * уходит в замечания, исключений нет. Живой файл 911 сразу дал два случая, ради которых это
 * правило и заведено:</p>
 * <ul>
 *   <li>у строки «Математическое обеспечение АССН» в колонке «Дисциплина» стоит <b>индекс учебного
 *       плана</b> («ДС.1.О»), а не название. Поля читаются как есть, а расхождение отдаётся
 *       замечанием: заводить дисциплину с именем «ДС.1.О» нельзя, и решать это должен человек;</li>
 *   <li>у дисциплины АСКС в «Других видах занятий» <b>два преподавателя</b> — это и есть открытый
 *       вопрос 1a, и подвал на него не отвечает: два практика он не разводит по занятиям.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../docs/IMPORT_FORMAT.md">IMPORT_FORMAT.md §8</a>
 */
public final class DisciplineFooterParser {

    private DisciplineFooterParser() {
    }

    /** Разделитель перечня преподавателей в одной клетке — из самого файла. */
    private static final String EDUCATOR_SEPARATOR = ";";

    /**
     * Колонки подвала. Заголовок в файле длиннее ключа («Лектор, уч. степень, уч. звание»), поэтому
     * сравнение идёт по началу строки в нижнем регистре.
     */
    private enum Column {
        CODE("обозн"),
        NAME("дисциплина"),
        DEPARTMENT("каф"),
        LECTURERS("лектор"),
        PRACTICIANS("другие виды занятий"),
        HOURS("кол-во часов"),
        REPORT("отчет"),
        STREAM("поток");

        private final String headerPrefix;

        Column(String headerPrefix) {
            this.headerPrefix = headerPrefix;
        }
    }

    /**
     * Строка подвала: одна дисциплина группы.
     *
     * @param code        «Обозн» — то, чем дисциплина подписана в ячейках полотна («АСКС»).
     *                    <b>Это и есть ключ склейки</b> с занятиями, а не название
     * @param name        «Дисциплина» — полное название; в живом файле встречается индекс плана
     *                    вместо названия, тогда рядом лежит замечание
     * @param department  «Каф.» — краткое имя кафедры («91»), ложится на {@code org_unit.short_name}
     * @param lecturers   «Лектор» — подписи как в файле: «Волков В.Ф. двн проф», «п/п-к Чащин С.В.»
     * @param practicians «Другие виды занятий» — те же подписи; больше одного = вопрос 1a
     * @param hours       «Кол-во часов» как в файле: «18-30». Не разбирается: смысл двух чисел не
     *                    подтверждён, а в план часы всё равно не пишутся — ими сверяется найденное
     * @param report      «Отчет»: «ЗО», «ЗЧ», «ЭКЗ» либо пусто
     * @param stream      «Поток лекционный» как в файле
     */
    public record FooterRow(
            String code,
            String name,
            String department,
            List<String> lecturers,
            List<String> practicians,
            String hours,
            String report,
            String stream
    ) {
        /** Все преподаватели строки — лекторы и практики вместе, для сверки со справочником. */
        public List<String> allEducators() {
            List<String> all = new ArrayList<>(lecturers);
            all.addAll(practicians);
            return List.copyOf(all);
        }
    }

    /**
     * Находит подвал и читает его строки.
     *
     * @param doc      разобранный документ
     * @param cut      разрез файла: отсутствие подвала — замечание только для группового, у
     *                 остальных его и не должно быть
     * @param problems куда складывать замечания (пополняется на месте)
     * @return строки подвала; пустой список, если подвала нет
     */
    public static List<FooterRow> parse(Document doc, CutKind cut, List<String> problems) {
        if (doc == null) {
            return List.of();
        }
        for (Element table : doc.select("table")) {
            List<Element> rows = ScheduleSheetParser.ownRows(table);
            for (int i = 0; i < rows.size(); i++) {
                Map<Column, Integer> columns = mapColumns(rows.get(i));
                if (columns == null) {
                    continue;
                }
                reportMissingColumns(columns, problems);
                return readRows(rows, i + 1, columns, problems);
            }
        }
        if (cut == CutKind.GROUP) {
            problems.add("подвал с перечнем дисциплин не найден — преподавателей взять неоткуда");
        }
        return List.of();
    }

    /**
     * Шапка подвала → номера колонок.
     *
     * @return {@code null}, если строка не похожа на шапку подвала. Признак — наличие «Обозн» и
     * «Дисциплина» разом: по одному заголовку можно наткнуться на чужую таблицу
     */
    private static Map<Column, Integer> mapColumns(Element row) {
        List<Element> cells = ScheduleSheetParser.ownCells(row);
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        for (int i = 0; i < cells.size(); i++) {
            String text = ScheduleSheetParser.normalize(cells.get(i).text()).toLowerCase();
            for (Column column : Column.values()) {
                if (!columns.containsKey(column) && text.startsWith(column.headerPrefix)) {
                    columns.put(column, i);
                }
            }
        }
        return columns.containsKey(Column.CODE) && columns.containsKey(Column.NAME) ? columns : null;
    }

    private static void reportMissingColumns(Map<Column, Integer> columns, List<String> problems) {
        List<String> missing = Arrays.stream(Column.values())
                .filter(column -> !columns.containsKey(column))
                .map(column -> column.headerPrefix)
                .toList();
        if (!missing.isEmpty()) {
            problems.add("в шапке подвала нет колонок: " + String.join(", ", missing));
        }
    }

    private static List<FooterRow> readRows(List<Element> rows,
                                            int from,
                                            Map<Column, Integer> columns,
                                            List<String> problems) {
        int required = columns.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        List<FooterRow> result = new ArrayList<>();
        int blank = 0;

        for (int i = from; i < rows.size(); i++) {
            List<Element> cells = ScheduleSheetParser.ownCells(rows.get(i));
            if (cells.size() < required) {
                // Подшапка «вид | выд. | сдача» и прочая машинерия заголовка: колонок меньше, чем
                // объявлено. Молча, потому что это не кривизна файла, а его нормальное устройство.
                continue;
            }
            String code = value(cells, columns, Column.CODE);
            if (code.isEmpty()) {
                blank++;
                continue;
            }
            String name = value(cells, columns, Column.NAME);
            if (looksLikePlanIndex(name)) {
                problems.add("подвал, «" + code + "»: в колонке «Дисциплина» стоит индекс плана «"
                        + name + "», а не название — дисциплину под таким именем заводить нельзя");
            }
            result.add(new FooterRow(
                    code,
                    name,
                    value(cells, columns, Column.DEPARTMENT),
                    splitEducators(value(cells, columns, Column.LECTURERS)),
                    splitEducators(value(cells, columns, Column.PRACTICIANS)),
                    value(cells, columns, Column.HOURS),
                    value(cells, columns, Column.REPORT),
                    value(cells, columns, Column.STREAM)
            ));
        }

        if (blank > 0) {
            problems.add("в подвале " + blank + " строк(и) без обозначения дисциплины — пропущены");
        }
        if (result.isEmpty()) {
            problems.add("подвал найден, но ни одной строки дисциплины прочитать не удалось");
        }
        return List.copyOf(result);
    }

    /**
     * Индекс учебного плана вместо названия: «ДС.1.О», «Б1.В.ОД.5».
     *
     * <p>Признак — <b>ни одного пробела</b> при наличии точки и цифры. Название дисциплины в этой
     * колонке всегда многословно («Автоматизированные системы управления КС»), поэтому короткий
     * токен с точкой и цифрой — это заведомо не название.</p>
     */
    static boolean looksLikePlanIndex(String text) {
        return !text.isEmpty()
                && !text.contains(" ")
                && text.indexOf('.') >= 0
                && text.chars().anyMatch(Character::isDigit);
    }

    /** «Волков В.Ф. двн проф; п/п-к Чащин С.В.» → две подписи; звание и степень остаются при них. */
    private static List<String> splitEducators(String raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(raw.split(EDUCATOR_SEPARATOR))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static String value(List<Element> cells, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null ? "" : ScheduleSheetParser.normalize(cells.get(index).text());
    }
}
