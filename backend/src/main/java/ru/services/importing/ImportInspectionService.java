package ru.services.importing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.services.importing.CellDialect.LessonEntry;
import ru.services.importing.ParsedSheet.SheetCell;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Пробный разбор файла выгрузки: «что программа поняла» — без единой записи в базу.
 *
 * <p><b>Почему это отдельный, самый первый шаг импорта.</b> Первый прогон заведомо кривой, а
 * асимметрия необратимости жёсткая: завести преподавателя или дисциплину легко, убрать — нет (как
 * только на сущность сошлётся назначение, удаление упрётся в {@code RESTRICT}). Поэтому сначала
 * отчёт, потом сопоставление, и только потом запись — решение И-10.</p>
 *
 * <p>Сервис ничего не знает ни о репозиториях, ни о наших сущностях: он складывает вместе три
 * чистые функции ({@link ScheduleSheetParser}, {@link CellDialect},
 * {@link RoomNumberDecoder}) и считает по ним сводку. Сопоставление с базой — следующий слой.</p>
 */
@Slf4j
@Service
public class ImportInspectionService {

    /** Сколько занятий показать «на глаз» в сводке. */
    private static final int SAMPLE_SIZE = 10;

    /**
     * Разбирает один файл и отдаёт сводку.
     *
     * @param fileName имя файла (для отчёта; на разбор не влияет)
     * @param content  содержимое файла
     * @return сводка; при нечитаемом файле — она же, с замечаниями и нулями
     */
    public SheetInspection inspect(String fileName, byte[] content) {
        return inspect(fileName, ScheduleSheetParser.parse(content));
    }

    /**
     * Сводка по уже разобранному файлу.
     *
     * <p>Отдельный вход нужен, потому что разбор нужен не только сводке: сверка со справочниками
     * читает тот же {@link ParsedSheet}. Разбирать файл дважды ради двух отчётов не за чем — jsoup
     * на четверти мегабайта не бесплатен.</p>
     *
     * @param fileName имя файла (для отчёта; на разбор не влияет)
     * @param sheet    результат разбора
     */
    public SheetInspection inspect(String fileName, ParsedSheet sheet) {
        CellDialect dialect = CellDialect.of(sheet.header().kind());

        List<String> problems = new ArrayList<>(sheet.problems());
        List<LessonEntry> lessons = new ArrayList<>();
        Set<String> disciplines = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        Set<String> rooms = new LinkedHashSet<>();
        Set<String> markerCodes = new LinkedHashSet<>();
        int markers = 0;

        for (SheetCell cell : sheet.cells()) {
            if (cell.isMarker()) {
                markers++;
                markerCodes.add(cell.lines().get(0));
                continue;
            }
            if (dialect == null) {
                continue; // разрез не опознан — замечание об этом уже есть
            }
            LessonEntry entry = dialect.read(cell, sheet.header());
            lessons.add(entry);
            add(disciplines, entry.discipline());
            entry.groups().forEach(group -> add(groups, group));
            entry.rooms().forEach(room -> add(rooms, room));
        }

        if (dialect == null && !sheet.cells().isEmpty()) {
            problems.add("разрез не опознан — ячейки прочитаны, но истолковать их нечем");
        }
        problems.addAll(compareWithFooter(disciplines, sheet.footer()));

        LocalDate first = lessons.stream().map(LessonEntry::date).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = lessons.stream().map(LessonEntry::date).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        log.info("Разбор файла {}: разрез={}, владелец={}, занятий={}, маркеров={}, замечаний={}",
                fileName, sheet.header().kind(), sheet.header().owner(), lessons.size(), markers, problems.size());

        return new SheetInspection(
                fileName,
                sheet.header().kind(),
                sheet.header().owner(),
                sheet.header().faculty(),
                sheet.header().department(),
                sheet.header().startYear(),
                sheet.header().semester(),
                lessons.size(),
                markers,
                first,
                last,
                List.copyOf(disciplines),
                List.copyOf(groups),
                List.copyOf(rooms),
                List.copyOf(markerCodes),
                sheet.footer(),
                List.copyOf(problems),
                lessons.stream().limit(SAMPLE_SIZE).toList()
        );
    }

    /**
     * Сверка двух половин одного файла: обозначения дисциплин в ячейках против перечня в подвале.
     *
     * <p>Дешёвая и ранняя проверка целости разбора: обе стороны взяты из одного файла, значит
     * расхождение — это <b>наша</b> ошибка чтения, а не расхождение с базой. Читается как готовая
     * подсказка, куда смотреть, ещё до всякого сопоставления со справочниками.</p>
     *
     * <p>Асимметрия ожидаемая и потому в тексте разведена: дисциплина из подвала <b>без занятий</b> —
     * почти норма (занятия могли быть затёрты маркером «ЭкзС» или их нет в этом семестре), а вот
     * занятие по дисциплине, которой нет в подвале, значит, что <b>преподавателя для него взять
     * неоткуда</b> — такую строку нечем будет разрешить в назначение.</p>
     */
    private static List<String> compareWithFooter(Set<String> inCells,
                                                  List<DisciplineFooterParser.FooterRow> footer) {
        if (footer.isEmpty()) {
            return List.of(); // подвала нет — сверять не с чем; об этом уже сказал разбор
        }
        Set<String> inFooter = footer.stream()
                .map(DisciplineFooterParser.FooterRow::code)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> notes = new ArrayList<>();
        for (String code : inCells) {
            if (!inFooter.contains(code)) {
                notes.add("дисциплина «" + code + "» есть в занятиях, но её нет в подвале — "
                        + "преподавателя для этих занятий взять неоткуда");
            }
        }
        for (String code : inFooter) {
            if (!inCells.contains(code)) {
                notes.add("дисциплина «" + code + "» есть в подвале, но занятий по ней в файле нет");
            }
        }
        return notes;
    }

    private static void add(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}
