package ru.services.importing;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор полотна чужой выгрузки: HTML → ячейки с восстановленными датами.
 *
 * <p><b>Чистая функция без Spring и БД</b> — как {@link GroupNumberDecoder} и
 * {@code OrgUnitSubtree}. На вход строка или байты, на выход {@link ParsedSheet}; тотальна, ничего
 * не бросает.</p>
 *
 * <h2>Геометрия (снята с живых файлов, 2025/2026 осенний)</h2>
 * <ul>
 *   <li>Строка полотна = <b>(день недели, пара)</b>: у первой пары дня в строке есть ячейка дня
 *       ({@code rowspan=4}), у остальных её нет;</li>
 *   <li>Столбец = учебная неделя, ячейка каждой недели содержит <b>вложенную таблицу</b> из трёх
 *       строк;</li>
 *   <li>Перед каждым днём идёт строка <b>«Даты»</b> — число месяца этого дня в каждой неделе.</li>
 * </ul>
 *
 * <h2>Почему дата считается по дню недели, а не по номеру недели и не по строке «Месяц»</h2>
 * Оба «очевидных» пути в живых файлах врут:
 * <ul>
 *   <li><b>номер недели</b> — в преподавательском разрезе пустые недели вырезаны, а нумерация
 *       остаётся сплошной 1…26: у Ветрова первая колонка это 8 сентября, а подписана единицей;</li>
 *   <li><b>строка «Месяц»</b> — в групповом файле у сентября {@code colspan=4} при пяти
 *       сентябрьских понедельниках, а в преподавательском покрыто 11 колонок из 26.</li>
 * </ul>
 * Поэтому якорь — учебный год и семестр из шапки, а дальше для каждой колонки ищется <b>ближайшая
 * дата с нужным днём недели и нужным числом месяца</b>. Такой ход не зависит ни от нумерации
 * колонок, ни от того, вырезаны недели в начале или в середине: он опирается только на то, что
 * колонки идут по возрастанию времени. Совпадение дня недели при этом работает проверкой само по
 * себе — подогнать чужое число под чужой день нельзя.
 *
 * <h2>Кодировка</h2>
 * Файлы объявляют {@code utf-8}, но встречается <b>обрезанный по байтам символ</b> (в образце
 * «СР» записано тремя байтами вместо четырёх). Строгий декодер на этом падает, поэтому байты
 * декодируются с заменой: одна испорченная подпись не должна ронять разбор всего файла.
 */
public final class ScheduleSheetParser {

    private ScheduleSheetParser() {
    }

    /**
     * Владелец группового файла — <b>до конца графы шапки</b>, а не до первого пробела.
     *
     * <p>Файл бывает выписан на <b>объединённые группы</b>: «Учебная группа 10073/19, 10073/22».
     * Прежний {@code [^\s<]+} обрезал такую шапку по пробелу и отдавал «10073/19,» — номер с
     * запятой на конце, то есть новую группу-призрак в справочнике. Графы шапки склеены «|», по
     * нему и проходит граница; перечень разбирает {@link CellDialect}.</p>
     */
    private static final Pattern GROUP_OWNER = Pattern.compile("Учебная\\s+группа\\s+([^|<]+)");
    private static final Pattern EDUCATOR_OWNER = Pattern.compile("Преподаватель:\\s*(.+?)\\s*(?:Семестр:|\\||$)");
    private static final Pattern AUDITORIUM_OWNER = Pattern.compile("Загрузка\\s+учебной\\s+аудитории\\s+([^\\s|]+)");
    private static final Pattern FACULTY = Pattern.compile("Факультет\\s+([^\\s|]+)");
    private static final Pattern DEPARTMENT = Pattern.compile("Кафедра:\\s*([^|]+)");
    private static final Pattern STUDY_YEAR = Pattern.compile("(\\d{4})\\s*/\\s*\\d{4}\\s+учебный\\s+год");
    private static final Pattern SEMESTER = Pattern.compile("(осенний|весенний)");
    private static final Pattern SLOT_LABEL = Pattern.compile("(\\d)\\s*-\\s*(\\d)");

    /** Пар в дне столько же, сколько в нашей сетке; подпись в файле — «1-2», «3-4»… */
    private static final Map<String, TimeSlotPair> SLOTS = Map.of(
            "1-2", TimeSlotPair.FIRST,
            "3-4", TimeSlotPair.SECOND,
            "5-6", TimeSlotPair.THIRD,
            "7-8", TimeSlotPair.FOURTH
    );

    private static final Map<String, DayOfWeek> DAYS = new HashMap<>();

    static {
        for (DayOfWeek day : DayOfWeek.values()) {
            DAYS.put(day.getAbbreviation(), day);
        }
    }

    /**
     * Разбирает файл из байтов. Кодировка — UTF-8 с заменой нечитаемых последовательностей.
     *
     * @param html содержимое файла
     * @return разбор; при пустом входе — пустой результат с замечанием
     */
    public static ParsedSheet parse(byte[] html) {
        if (html == null || html.length == 0) {
            return empty("файл пуст");
        }
        return parse(new String(html, StandardCharsets.UTF_8));
    }

    /**
     * То же, но с именем файла — оно доезжает до сверки и до строк отчёта.
     *
     * <p>Отдельная перегрузка, а не параметр разбора: на сам разбор имя не влияет никак (разрез
     * определяется по шапке, а не по имени файла), и путать эти две роли не стоит.</p>
     *
     * @param html   содержимое файла
     * @param source имя файла — то, что человек увидит в отчёте и найдёт на диске
     */
    public static ParsedSheet parse(byte[] html, String source) {
        return parse(html).withSource(source);
    }

    /**
     * Разбирает файл из строки.
     *
     * @param html содержимое файла
     * @return разбор; ошибки — в {@link ParsedSheet#problems()}, исключений нет
     */
    public static ParsedSheet parse(String html) {
        if (html == null || html.isBlank()) {
            return empty("файл пуст");
        }

        List<String> problems = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        SheetHeader header = readHeader(doc, problems);
        List<DisciplineFooterParser.FooterRow> footer =
                DisciplineFooterParser.parse(doc, header.kind(), problems);

        Element grid = findGrid(doc);
        if (grid == null) {
            problems.add("не найдено полотно расписания (таблицы со строками дней)");
            return new ParsedSheet(null, header, List.of(), footer, List.copyOf(problems));
        }

        return new ParsedSheet(null, header, readCells(grid, header, problems), footer, List.copyOf(problems));
    }

    // =======================================================================
    // Шапка
    // =======================================================================

    private static SheetHeader readHeader(Document doc, List<String> problems) {
        // По ячейкам первой таблицы, а не по сплошному тексту документа: в шапке каждая графа —
        // отдельная ячейка («Преподаватель: … Семестр: осенний» | «Факультет 9Ф» | «Кафедра: …»),
        // и границы ячеек не дают регулярке утащить соседнюю графу. Сплошной текст такой границы
        // не имеет, а сразу за шапкой идёт полотно на тысячи слов.
        String text = headerText(doc);

        CutKind kind = CutKind.UNKNOWN;
        String owner = null;

        Matcher group = GROUP_OWNER.matcher(text);
        Matcher educator = EDUCATOR_OWNER.matcher(text);
        Matcher auditorium = AUDITORIUM_OWNER.matcher(text);
        if (auditorium.find()) {
            kind = CutKind.AUDITORIUM;
            owner = auditorium.group(1);
        } else if (educator.find()) {
            kind = CutKind.EDUCATOR;
            owner = educator.group(1);
        } else if (group.find()) {
            kind = CutKind.GROUP;
            owner = group.group(1).trim();
        } else {
            problems.add("шапка не опознана: ни группа, ни преподаватель, ни аудитория");
        }

        Integer startYear = null;
        Matcher year = STUDY_YEAR.matcher(text);
        if (year.find()) {
            startYear = Integer.parseInt(year.group(1));
        } else {
            problems.add("в шапке нет учебного года — даты восстановить не по чему");
        }

        Matcher semester = SEMESTER.matcher(text);
        String semesterName = semester.find() ? semester.group(1) : null;
        if (semesterName == null) {
            problems.add("в шапке нет семестра — начало отсчёта дат взято как осеннее");
        }

        Matcher faculty = FACULTY.matcher(text);
        Matcher department = DEPARTMENT.matcher(text);
        return new SheetHeader(
                kind,
                owner,
                faculty.find() ? faculty.group(1) : null,
                department.find() ? department.group(1).trim() : null,
                startYear,
                semesterName
        );
    }

    /**
     * Текст шапки: ячейки первой таблицы, склеенные разделителем. Разделитель нужен, чтобы
     * регулярка не перескакивала из графы в графу — «Преподаватель: …» кончается там же, где
     * кончается его ячейка.
     */
    private static String headerText(Document doc) {
        Element first = doc.selectFirst("table");
        if (first == null) {
            return normalize(doc.text());
        }
        List<String> parts = new ArrayList<>();
        for (Element row : ownRows(first)) {
            for (Element cell : ownCells(row)) {
                String text = normalize(cell.text());
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            }
        }
        return String.join(" | ", parts);
    }

    // =======================================================================
    // Полотно
    // =======================================================================

    /** Полотно — верхнеуровневая таблица с наибольшим числом собственных строк. */
    private static Element findGrid(Document doc) {
        Element grid = null;
        int best = 0;
        for (Element table : doc.select("table")) {
            int rows = ownRows(table).size();
            if (rows > best) {
                best = rows;
                grid = table;
            }
        }
        return best >= 2 ? grid : null;
    }

    private static List<SheetCell> readCells(Element grid, SheetHeader header, List<String> problems) {
        List<SheetCell> cells = new ArrayList<>();
        List<Integer> daysOfMonth = List.of();
        DayOfWeek day = null;
        boolean geometryReported = false;

        for (Element row : ownRows(grid)) {
            List<Element> tds = ownCells(row);
            if (tds.isEmpty()) {
                continue;
            }
            List<String> head = new ArrayList<>();
            for (int i = 0; i < Math.min(3, tds.size()); i++) {
                head.add(normalize(tds.get(i).text()));
            }

            int datesAt = head.indexOf("Даты");
            if (datesAt >= 0) {
                daysOfMonth = readDaysOfMonth(tds, datesAt + 1);
                continue;
            }

            DayOfWeek rowDay = DAYS.get(head.get(0));
            int weeksFrom;
            String slotLabel;
            if (rowDay != null) {
                day = rowDay;
                slotLabel = head.size() > 1 ? head.get(1) : "";
                weeksFrom = 3;
            } else if (day != null && isSlotLabel(head.get(0))) {
                slotLabel = head.get(0);
                weeksFrom = 2;
            } else {
                continue; // строка недель, строка месяцев, прочая шапка
            }

            TimeSlotPair slot = SLOTS.get(slotLabel);
            if (slot == null) {
                problems.add("неизвестная пара «" + slotLabel + "» в дне " + day.getAbbreviation());
                continue;
            }

            // Рассинхрон геометрии — причина, а не следствие: если чисел в строке «Даты» не столько
            // же, сколько недельных колонок в строке занятий, то все даты этого дня встают не на
            // свои места. Сообщаем один раз на файл: иначе одна причина размножилась бы на два
            // десятка строк и спрятала остальные находки.
            int weekColumns = tds.size() - weeksFrom;
            if (!geometryReported && !daysOfMonth.isEmpty() && daysOfMonth.size() != weekColumns) {
                problems.add("⚠ геометрия: в строке «Даты» " + daysOfMonth.size()
                        + " чисел, а недельных колонок в строке занятий " + weekColumns
                        + " (день " + day.getAbbreviation() + ") — числа месяца встают не на свои колонки");
                geometryReported = true;
            }

            cells.addAll(readRow(tds, weeksFrom, day, slot, daysOfMonth, header, problems));
        }
        return List.copyOf(cells);
    }

    private static List<SheetCell> readRow(List<Element> tds,
                                           int weeksFrom,
                                           DayOfWeek day,
                                           TimeSlotPair slot,
                                           List<Integer> daysOfMonth,
                                           SheetHeader header,
                                           List<String> problems) {
        List<SheetCell> cells = new ArrayList<>();
        LocalDate cursor = anchor(header);
        int weekColumns = tds.size() - weeksFrom;

        for (int column = 0; column + weeksFrom < tds.size(); column++) {
            Element td = tds.get(column + weeksFrom);
            LocalDate date = null;
            LocalDate searchedFrom = cursor;
            Integer dayOfMonth = column < daysOfMonth.size() ? daysOfMonth.get(column) : null;

            if (dayOfMonth != null) {
                date = nextDate(cursor, day, dayOfMonth);
            }

            // Курсор двигается на неделю ДАЖЕ когда дату восстановить не удалось: колонка в файле
            // есть, значит неделя прошла. Иначе одна дырка в строке «Даты» отбрасывала бы точку
            // отсчёта назад для всех следующих колонок — и дальше либо нужная дата уходила за окно
            // поиска (замечание не там, где причина), либо, что хуже, находилась ДРУГАЯ дата с тем
            // же числом неделями раньше: молча, без единого замечания.
            cursor = date != null ? date.plusDays(1) : cursor.plusWeeks(1);

            List<String> lines = cellLines(td);
            if (lines.isEmpty()) {
                continue;
            }
            if (date == null) {
                problems.add(dateFailure(day, slot, column, dayOfMonth, searchedFrom,
                        daysOfMonth.size(), weekColumns, lines));
            }
            boolean shaded = td.attr("style").contains("background");
            cells.add(new SheetCell(day, slot, column, date, lines, shaded));
        }
        return cells;
    }

    /**
     * Замечание о невосстановленной дате — со всем, что разбор знал в этот момент.
     *
     * <p><b>Почему так подробно.</b> Прежний текст называл только место («Вт 1 колонка 5») и
     * содержимое ячейки, и проверить его глазами было нельзя: в файле числа на месте, а почему они
     * не подошли — не видно. Здесь сразу лежат все три возможные причины, различённые между собой:
     * числа нет вовсе; чисел меньше, чем колонок (тогда они разъехались и виноват не этот столбец);
     * число есть, но такого дня недели с таким числом нет в окне поиска.</p>
     */
    private static String dateFailure(DayOfWeek day,
                                      TimeSlotPair slot,
                                      int column,
                                      Integer dayOfMonth,
                                      LocalDate searchedFrom,
                                      int datesInRow,
                                      int weekColumns,
                                      List<String> lines) {
        StringBuilder message = new StringBuilder("не удалось определить дату: ")
                .append(day.getAbbreviation()).append(" ").append(slot.getAbbreviation())
                .append(" колонка ").append(column + 1).append(" — ");

        if (dayOfMonth == null) {
            message.append("в строке «Даты» для этой колонки числа нет");
        } else {
            // Без склонений: «вторника» и «среды» — разные окончания, а сообщение собирается кодом.
            message.append("напечатано число ").append(dayOfMonth)
                    .append(", но дня ").append(day.getAbbreviation())
                    .append(" с таким числом нет за ").append(MAX_GAP_WEEKS)
                    .append(" недель от ").append(searchedFrom);
        }

        if (datesInRow != weekColumns) {
            message.append("; ⚠ строка «Даты» описывает ").append(datesInRow)
                    .append(" колонок, а занятий в строке ").append(weekColumns)
                    .append(" — числа встали не на свои колонки");
        }
        return message.append(" — ").append(lines).toString();
    }

    /**
     * Начало отсчёта: 1 сентября для осеннего семестра, 1 января следующего года — для весеннего.
     *
     * <p>Строка «Месяц» намеренно не используется даже как якорь: в живых файлах она врёт
     * (см. javadoc класса), а семестр и учебный год в шапке — врать не могут, они и есть
     * идентификация файла.</p>
     */
    private static LocalDate anchor(SheetHeader header) {
        int year = header.startYear() == null ? LocalDate.now().getYear() : header.startYear();
        return "весенний".equals(header.semester())
                ? LocalDate.of(year + 1, 1, 1)
                : LocalDate.of(year, 9, 1);
    }

    /**
     * Насколько далеко вперёд ищется дата колонки.
     *
     * <p>Пропуск колонок — норма: у преподавателя вырезаны недели без занятий, а зимние каникулы и
     * отпуск дают подряд идущие дыры. Окно всё же конечно: без него одно кривое число месяца увело
     * бы курсор на месяцы вперёд и молча испортило все следующие колонки.</p>
     *
     * <p><b>Было 12 недель — мало</b> (исправлено 2026-08-15 по живым файлам). Квартал казался
     * «заведомо больше любого законного пропуска», но реальные разрывы оказались длиннее:</p>
     * <ul>
     *   <li>у преподавателя, чьи занятия начинаются в конце ноября, <b>первая же колонка</b> — это
     *       13-й вторник от 1 сентября, то есть на неделю дальше прежнего окна;</li>
     *   <li>осенний семестр кончается сессией <b>в январе</b>: разрыв «конец сентября → 13 января»
     *       это пятнадцать недель, и он совершенно законен.</li>
     * </ul>
     *
     * <p>Полугодие — это длина семестра целиком: дальше него законной даты в файле одного семестра
     * быть не может, а внутри него любой пропуск допустим. Защиту от кривого числа держит не
     * столько размер окна, сколько <b>совпадение дня недели</b>: подогнать чужое число под чужой
     * день нельзя.</p>
     */
    private static final int MAX_GAP_WEEKS = 26;

    /**
     * Ближайшая с {@code from} дата, у которой нужный день недели и нужное число месяца.
     *
     * <p>Перебор идёт неделями, поэтому вырезанные пустые недели ничего не ломают: следующая
     * колонка просто найдётся дальше по календарю. Переходы через месяц и через год получаются
     * сами — календарь считает их за нас, и строка «Месяц» (которая в живых файлах врёт) не нужна
     * вовсе.</p>
     *
     * @return дата либо {@code null}, если в пределах {@value #MAX_GAP_WEEKS} недель такой нет
     */
    static LocalDate nextDate(LocalDate from, DayOfWeek day, int dayOfMonth) {
        LocalDate candidate = from;
        while (candidate.getDayOfWeek() != day.toJavaTimeDayOfWeek()) {
            candidate = candidate.plusDays(1);
        }
        for (int week = 0; week < MAX_GAP_WEEKS; week++) {
            if (candidate.getDayOfMonth() == dayOfMonth) {
                return candidate;
            }
            candidate = candidate.plusWeeks(1);
        }
        return null;
    }

    // =======================================================================
    // Мелкая механика
    // =======================================================================

    private static List<Integer> readDaysOfMonth(List<Element> tds, int from) {
        List<Integer> values = new ArrayList<>();
        for (int i = from; i < tds.size(); i++) {
            String text = normalize(tds.get(i).text());
            Integer value = null;
            if (text.matches("\\d{1,2}")) {
                int parsed = Integer.parseInt(text);
                if (parsed >= 1 && parsed <= 31) {
                    value = parsed;
                }
            }
            values.add(value);
        }
        return values;
    }

    /** Строки ячейки: вложенная таблица даёт по строке, иначе — собственный текст. */
    private static List<String> cellLines(Element td) {
        List<String> lines = new ArrayList<>();
        List<Element> inner = td.children().stream().filter(e -> "table".equals(e.tagName())).toList();
        if (inner.isEmpty()) {
            String text = normalize(td.text());
            if (!text.isEmpty()) {
                lines.add(text);
            }
            return lines;
        }
        for (Element row : ownRows(inner.get(0))) {
            String text = normalize(row.text());
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }
        return lines;
    }

    /**
     * Собственные строки таблицы — без строк вложенных таблиц (ячейки сами таблицы).
     *
     * <p>Пакетная видимость: тем же обходом пользуется {@link DisciplineFooterParser}. Разбор
     * полотна и разбор подвала — разные задачи, а вот «что считать строкой таблицы» в этом формате
     * одно на всех, и вторая копия разъехалась бы с первой.</p>
     */
    static List<Element> ownRows(Element table) {
        List<Element> rows = new ArrayList<>();
        for (Element child : table.children()) {
            if ("tr".equals(child.tagName())) {
                rows.add(child);
            } else if ("tbody".equals(child.tagName()) || "thead".equals(child.tagName())) {
                for (Element inner : child.children()) {
                    if ("tr".equals(inner.tagName())) {
                        rows.add(inner);
                    }
                }
            }
        }
        return rows;
    }

    static List<Element> ownCells(Element row) {
        return row.children().stream().filter(e -> "td".equals(e.tagName()) || "th".equals(e.tagName())).toList();
    }

    private static boolean isSlotLabel(String text) {
        return SLOT_LABEL.matcher(text).matches();
    }

    /** Неразрывные пробелы — в обычные, повторы — в один. В подвале их особенно много. */
    static String normalize(String text) {
        return text == null ? "" : text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private static ParsedSheet empty(String problem) {
        return new ParsedSheet(
                null,
                new SheetHeader(CutKind.UNKNOWN, null, null, null, null, null),
                List.of(),
                List.of(),
                List.of(problem)
        );
    }
}
