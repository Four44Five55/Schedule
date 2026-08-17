package ru.services.importing;

import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Что означают три строки ячейки — в каждом разрезе своё.
 *
 * <p><b>Strategy как enum</b> — тем же приёмом, что {@code ExportAxis} и {@code ProjectionSource}:
 * геометрию полотна разобрал {@link ScheduleSheetParser} (она у трёх разрезов одна), а смысл строк
 * знает диалект. Новый разрез = новая константа, разбор не трогается.</p>
 *
 * <p><b>Состав ячейки снят с живых файлов</b> и в трёх разрезах действительно разный:</p>
 * <table>
 *   <caption>Строки ячейки по разрезам</caption>
 *   <tr><th>Разрез</th><th>Строка 1</th><th>Строка 2</th><th>Строка 3</th></tr>
 *   <tr><td>Группа</td><td>{@code Л/Т.4} — вид и тема</td><td>{@code УПМВ}</td><td>{@code 416-3}</td></tr>
 *   <tr><td>Аудитория</td><td>{@code П} — вид</td><td>{@code 911}</td><td>{@code НИР}</td></tr>
 *   <tr><td>Преподаватель</td><td>{@code 252-3}</td><td>{@code 911}</td><td>{@code АСКС}</td></tr>
 * </table>
 *
 * <p><b>Недостающее не выдумывается.</b> В преподавательской ячейке нет вида занятия, в
 * аудиторной — темы; соответствующие поля остаются {@code null}. Восполнять их придётся склейкой
 * разрезов по дате и паре, и это работа следующего слоя, у которого есть все файлы сразу.</p>
 *
 * <p>Значения отдаются <b>как в файле</b>: «911», «АСКС», «252-3». Сопоставление с нашими
 * сущностями — отдельный шаг, и первый его прогон по решению И-10 идёт вообще без записи.</p>
 *
 * <p><b>Единственное толкование, которое диалект себе позволяет, — перечень групп.</b> Объединённые
 * группы записаны то отдельными строками, то одной строкой через запятую («10073/19, 10073/22»), и
 * это оформление, а не разные вещи: в обоих случаях занятие слушают две группы, то есть перед нами
 * поток. Поэтому разделители перечня снимаются здесь, у единственного владельца смысла ячейки, а не
 * у каждого потребителя порознь.</p>
 */
public enum CellDialect {

    /**
     * Групповой разрез: {@code вид/тема · дисциплина · аудитория(и)}; группа — владелец файла.
     * Комнат может быть несколько — занятие делится по кабинетам.
     */
    GROUP {
        @Override
        public LessonEntry read(SheetCell cell, SheetHeader header) {
            String[] kindAndTheme = splitKind(line(cell, 0));
            return new LessonEntry(
                    cell.date(), cell.slot(),
                    kindAndTheme[0], kindAndTheme[1],
                    line(cell, 1),
                    groups(header.owner()),
                    tail(cell, 2),
                    null);
        }
    },

    /** Аудиторный разрез: {@code вид · группа(ы) · дисциплина}; комната — владелец файла. */
    AUDITORIUM {
        @Override
        public LessonEntry read(SheetCell cell, SheetHeader header) {
            return new LessonEntry(
                    cell.date(), cell.slot(),
                    line(cell, 0), null,
                    last(cell),
                    middle(cell, 1),
                    header.owner() == null ? List.of() : List.of(header.owner()),
                    null);
        }
    },

    /**
     * Преподавательский разрез: {@code аудитория · группа(ы) · дисциплина}; преподаватель — владелец
     * файла. <b>Вида занятия в ячейке нет</b> — это и есть причина, по которой разрез не может быть
     * единственным источником.
     */
    EDUCATOR {
        @Override
        public LessonEntry read(SheetCell cell, SheetHeader header) {
            return new LessonEntry(
                    cell.date(), cell.slot(),
                    null, null,
                    last(cell),
                    middle(cell, 1),
                    line(cell, 0) == null ? List.of() : List.of(line(cell, 0)),
                    header.owner());
        }
    };

    /**
     * Занятие как оно записано в файле: ничего не сопоставлено и не разрешено.
     *
     * @param date       дата, восстановленная разбором
     * @param slot       пара
     * @param kind       обозначение вида занятия: «Л», «П», «КП»; {@code null} в преподавательском разрезе
     * @param theme      тема: «Т.4»; {@code null} везде, кроме группового разреза
     * @param discipline обозначение дисциплины: «АСКС»
     * @param groups     номера групп: «911». <b>Их может быть несколько</b> — потоковое занятие
     * @param rooms      номера аудиторий: «252-3»; в групповом разрезе тоже бывает несколько
     * @param educator   подпись преподавателя; заполнена только в преподавательском разрезе
     */
    public record LessonEntry(
            LocalDate date,
            ru.enums.TimeSlotPair slot,
            String kind,
            String theme,
            String discipline,
            List<String> groups,
            List<String> rooms,
            String educator
    ) {
    }

    /**
     * Читает ячейку по правилам своего разреза.
     *
     * @param cell   ячейка полотна (ожидается занятие, а не маркер)
     * @param header шапка файла — из неё берётся владелец
     */
    public abstract LessonEntry read(SheetCell cell, SheetHeader header);

    /**
     * Все занятия листа — ячейки, прочитанные диалектом своего разреза.
     *
     * <p>Здесь живут два правила, которые иначе пришлось бы повторять каждому потребителю (а их уже
     * двое — сводка и сверка): <b>маркер занятости — не занятие</b> (у него одна строка вместо трёх)
     * и <b>неопознанный разрез не толкуется вовсе</b>. Вторая копия этих правил разошлась бы с
     * первой, как разошлись два описателя занятия в аудите §1.2.</p>
     *
     * @param sheet разобранный лист
     * @return занятия в порядке файла; пусто, если разрез не опознан
     */
    public static List<LessonEntry> readAll(ParsedSheet sheet) {
        CellDialect dialect = of(sheet.header().kind());
        if (dialect == null) {
            return List.of();
        }
        return sheet.cells().stream()
                .filter(cell -> !cell.isMarker())
                .map(cell -> dialect.read(cell, sheet.header()))
                .toList();
    }

    /**
     * Диалект по разрезу файла.
     *
     * @return {@code null} для {@link CutKind#UNKNOWN} — неопознанный файл разбирать нечем, и
     * подставлять сюда «самый вероятный» разрез нельзя: ошибка была бы тихой
     */
    public static CellDialect of(CutKind kind) {
        return switch (kind) {
            case GROUP -> GROUP;
            case AUDITORIUM -> AUDITORIUM;
            case EDUCATOR -> EDUCATOR;
            case UNKNOWN -> null;
        };
    }

    /** «Л/Т.4» → вид «Л» и тема «Т.4»; без разделителя тема неизвестна. */
    private static String[] splitKind(String text) {
        if (text == null) {
            return new String[]{null, null};
        }
        int slash = text.indexOf('/');
        return slash < 0
                ? new String[]{text, null}
                : new String[]{text.substring(0, slash).trim(), text.substring(slash + 1).trim()};
    }

    private static String line(SheetCell cell, int index) {
        return index < cell.lines().size() ? cell.lines().get(index) : null;
    }

    /** Последняя строка ячейки — в аудиторном и преподавательском разрезах это дисциплина. */
    private static String last(SheetCell cell) {
        return cell.lines().isEmpty() ? null : cell.lines().get(cell.lines().size() - 1);
    }

    /**
     * Строки между первой и последней — группы потока.
     *
     * <p><b>Число строк в ячейке не фиксировано</b>, и это выяснилось на живых файлах: ячейка
     * {@code [338-7, 1155-1, 1155-2, 1155-3, ОВО]} — это аудитория, <b>три группы</b> и дисциплина.
     * Прежний разбор брал строку 2 как дисциплину и молча получал «1155-2»: в сверку уезжал мусор,
     * а поток из трёх групп выглядел одногрупповым — то есть и вместимость аудитории считалась
     * втрое меньше нужной.</p>
     */
    private static List<String> middle(SheetCell cell, int from) {
        int to = cell.lines().size() - 1;
        return from >= to ? List.of() : groups(cell.lines().subList(from, to));
    }

    /**
     * Разделители перечня групп <b>внутри одной строки</b>: «10073/19, 10073/22» — это две группы.
     *
     * <p>Объединённые группы пишутся то отдельными строками ячейки, то одной строкой через запятую,
     * и разница эта — оформление, а не смысл. Без разбиения такая запись доезжала до справочника
     * <b>целиком</b>: строка «10073/19, 10073/22» не разбиралась декодером («больше одного „/“ в
     * номере»), помечалась «в базе нет» и заводилась третьей группой — той, которой не существует.
     * Настоящий же смысл записи в том, что это <b>поток</b>: две группы, слушающие занятие вместе,
     * и увидеть их надо порознь в разделе «Группы» и вместе — в разделе «Потоки».</p>
     */
    private static final Pattern GROUP_LIST = Pattern.compile("[,;]");

    /** Строки-перечни → отдельные номера групп, в порядке файла и без пустых. */
    private static List<String> groups(List<String> lines) {
        return lines.stream().flatMap(line -> groups(line).stream()).toList();
    }

    /**
     * Одна строка-перечень → отдельные номера групп; {@code null} — групп нет вовсе.
     *
     * <p>Наружу открыто, потому что перечнем бывает записана не только ячейка, но и <b>шапка</b>
     * группового файла («Учебная группа 10073/19, 10073/22»), а читают её не через диалект. Второе
     * правило разбиения там завелось бы неизбежно — и разошлось бы с этим.</p>
     */
    public static List<String> groups(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        return GROUP_LIST.splitAsStream(line)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /** Хвост ячейки начиная с указанной строки — в групповом разрезе это аудитории. */
    private static List<String> tail(SheetCell cell, int from) {
        return from >= cell.lines().size() ? List.of() : List.copyOf(cell.lines().subList(from, cell.lines().size()));
    }
}
