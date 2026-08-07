package ru.services.exporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.ScheduledLessonDto;
import ru.entity.StudyPeriod;
import ru.entity.constraints.ConstraintData;
import ru.entity.Educator;
import ru.entity.OrgUnit;
import ru.entity.read.ScheduleView;
import ru.enums.KindOfStudy;
import ru.repository.EducatorRepository;
import ru.repository.read.ScheduleViewRepository;
import ru.services.ScheduleResponseService;
import ru.services.StudyPeriodService;
import ru.services.constraints.ConstraintService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Выгрузка расписания периода в Excel (Facade + SRP-координатор): период → скоуп по оси → сетка
 * из {@code schedule_view} + ограничения сущности → заполнение бланка ({@link ScheduleWorkbookRenderer}).
 * Раскладки/POI здесь нет, чтения БД в рендерере нет.
 *
 * <p>Источник — Query Side, поэтому файл отражает <b>реально сохранённое</b> расписание (ручная
 * раскладка, пины). Строки склеиваются в занятия тем же {@link ScheduleResponseService#buildGridFromViews},
 * что кормит UI. Пустые ячейки бланка подписываются ограничением сущности, как в историческом экспорте.</p>
 *
 * <p>Скоуп: {@code entityId} задан — одна сущность (один файл {@code .xlsx}); иначе — все сущности
 * оси в расписании периода <b>раздельными файлами</b>: по одной книге на сущность, упакованными в
 * ZIP-архив. Сущности без размещений не появляются.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleExportService {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ZIP_MIME = "application/zip";
    /** Академических часов в одной паре — для пересчёта числа лекционных пар в часы в легенде. */
    private static final int ACADEMIC_HOURS_PER_PAIR = 2;

    private final StudyPeriodService studyPeriodService;
    private final ScheduleViewRepository viewRepository;
    private final ScheduleResponseService responseService;
    private final ConstraintService constraintService;
    private final EducatorRepository educatorRepository;
    private final ScheduleWorkbookRenderer renderer;

    /** Результат выгрузки: байты, имя файла (кириллица, кодируется в контроллером) и MIME-тип. */
    public record ExportResult(byte[] bytes, String filename, String contentType) {}

    @Transactional(readOnly = true)
    public ExportResult export(Integer periodId, ExportAxis axis, Integer entityId) {
        StudyPeriod period = studyPeriodService.getEntityById(periodId);
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();

        // Только строки, относящиеся к этой оси (у которых есть id сущности оси).
        List<ScheduleView> relevant = viewRepository.findByPeriod(start, end).stream()
                .filter(v -> axis.entityId(v) != null)
                .toList();

        // Ограничения оси (id сущности → развёрнутые в ячейки), один раз на выгрузку.
        Map<Integer, List<ConstraintData>> axisConstraints =
                axis.constraintsBy(constraintService.loadAllConstraints());

        // Для группы — карта «размещение → все его группы» (лекционный поток в легенде): строится из
        // ВСЕХ строк оси (не только текущей группы), т.к. поток — это со-группы по общему placementId.
        Map<UUID, Set<String>> streamGroups = axis == ExportAxis.GROUP
                ? groupsByPlacement(relevant) : Map.of();
        // Кафедры участников для колонки «Каф» — тоже только для группы (только у неё есть таблица).
        Map<Integer, String> departments = axis == ExportAxis.GROUP
                ? departmentsByEducator(relevant) : Map.of();

        // Правый край листов: последняя дата, на которой в расписании периода есть занятие.
        LocalDate contentEnd = contentEnd(relevant, end);

        // Одна сущность — одна книга .xlsx (как раньше).
        if (entityId != null) {
            List<ScheduleView> rows = relevant.stream()
                    .filter(v -> entityId.equals(axis.entityId(v)))
                    .toList();
            String scopeName = rows.isEmpty() ? ("#" + entityId) : axis.entityName(rows.get(0));
            var sheet = sheetOf(scopeName, entityId, rows, axisConstraints, start, end, axis,
                    streamGroups, departments);
            byte[] bytes = renderer.render(List.of(sheet), start, end, contentEnd, axis);
            String filename = buildFilename(period, axis, scopeName, ".xlsx");
            log.info("Экспорт расписания (бланк, .xlsx): период id={}, ось={}, сущность={}, {} байт",
                    periodId, axis, entityId, bytes.length);
            return new ExportResult(bytes, filename, XLSX_MIME);
        }

        // Все сущности оси — раздельными файлами (по книге на сущность) в ZIP-архиве.
        List<ScheduleWorkbookRenderer.SheetData> sheets = relevant.stream()
                .collect(Collectors.groupingBy(axis::entityId))
                .entrySet().stream()
                .sorted(Comparator.comparing(
                        (Map.Entry<Integer, List<ScheduleView>> e) ->
                                Optional.ofNullable(axis.entityName(e.getValue().get(0))).orElse(""),
                        String.CASE_INSENSITIVE_ORDER))
                .map(e -> sheetOf(axis.entityName(e.getValue().get(0)), e.getKey(),
                        e.getValue(), axisConstraints, start, end, axis, streamGroups, departments))
                .toList();

        byte[] bytes = zipPerEntity(sheets, start, end, contentEnd, axis);
        String scopeName = "все_" + axis.title().toLowerCase(Locale.ROOT);
        String filename = buildFilename(period, axis, scopeName, ".zip");
        log.info("Экспорт расписания (бланк, ZIP): период id={}, ось={}, файлов={}, {} байт",
                periodId, axis, sheets.size(), bytes.length);
        return new ExportResult(bytes, filename, ZIP_MIME);
    }

    /**
     * Упаковывает по одной книге {@code .xlsx} на сущность в ZIP-архив: каждый файл — самостоятельный
     * бланк одной сущности (тот же рендер, что и для одиночной выгрузки, список из одного листа).
     * Имена файлов внутри архива уникализируются (тёзки получают суффикс).
     */
    private byte[] zipPerEntity(List<ScheduleWorkbookRenderer.SheetData> sheets,
                                LocalDate start, LocalDate end, LocalDate contentEnd, ExportAxis axis) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, java.nio.charset.StandardCharsets.UTF_8)) {
            Set<String> usedNames = new HashSet<>();
            for (ScheduleWorkbookRenderer.SheetData sheet : sheets) {
                byte[] book = renderer.render(List.of(sheet), start, end, contentEnd, axis);
                zip.putNextEntry(new ZipEntry(zipEntryName(sheet.name(), usedNames)));
                zip.write(book);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать ZIP-архив расписания", e);
        }
    }

    /** Имя файла сущности внутри архива: очищенное имя + {@code .xlsx}, уникальное в пределах архива. */
    private String zipEntryName(String rawName, Set<String> used) {
        String base = (rawName == null || rawName.isBlank() ? "Лист" : rawName)
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String name = base;
        int n = 2;
        while (!used.add(name + ".xlsx")) {
            name = base + "_" + n++;
        }
        return name + ".xlsx";
    }

    /** Собирает данные листа: сетка занятий + карта ограничений + легенда «Обозначения» (для группы). */
    private ScheduleWorkbookRenderer.SheetData sheetOf(String name, Integer entityId, List<ScheduleView> rows,
                                                       Map<Integer, List<ConstraintData>> axisConstraints,
                                                       LocalDate start, LocalDate end, ExportAxis axis,
                                                       Map<UUID, Set<String>> streamGroups,
                                                       Map<Integer, String> departments) {
        Map<String, List<ScheduledLessonDto>> grid = responseService.buildGridFromViews(rows);
        List<ConstraintData> constraints = axisConstraints.get(entityId);
        Map<String, String> constraintAbbr = constraintMapFor(constraints, start, end);
        // Таблица «Обозначения» бланка и расшифровки под ней — только для группы; для препода и
        // аудитории таблица вычищается, а вместе с ней уходит и место под расшифровки.
        boolean group = axis == ExportAxis.GROUP;
        List<ScheduleWorkbookRenderer.LegendRow> legend =
                group ? buildLegend(rows, streamGroups, departments) : List.of();
        List<ScheduleWorkbookRenderer.Mark> kindMarks = group ? kindMarksOf(rows) : List.of();
        List<ScheduleWorkbookRenderer.Mark> otherMarks = group ? constraintMarksOf(constraints, start, end) : List.of();
        return new ScheduleWorkbookRenderer.SheetData(name, grid, constraintAbbr, legend, kindMarks, otherMarks);
    }

    /**
     * Дата последнего занятия расписания — правый край листов: недели правее неё обрезаются.
     *
     * <p>Считается по <b>занятиям</b>, а не по ограничениям. Ограничение (командировка, сессия)
     * растянуто на недели, где занятий нет вовсе, и учёт таких дат вернул бы границу к концу периода,
     * то есть отменил бы обрезку. «Учебная неделя» — это неделя с занятиями. ⚠️ Следствие: метка
     * ограничения на неделе, где во всём расписании нет ни одного занятия, в файл не попадёт.</p>
     *
     * <p>Считается по <b>всему</b> расписанию оси, а не по выбранной сущности: иначе файлы одной
     * выгрузки заканчивались бы на разных неделях, а «в расписании N учебных недель» — свойство
     * расписания, а не одной группы. Расписание пустое → {@code end} (режем по концу периода).</p>
     */
    private static LocalDate contentEnd(List<ScheduleView> relevant, LocalDate end) {
        return relevant.stream()
                .map(ScheduleView::getScheduledDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(end);
    }

    /** Виды занятий, реально встречающиеся на листе (в порядке справочника) — для расшифровки. */
    private static List<ScheduleWorkbookRenderer.Mark> kindMarksOf(List<ScheduleView> rows) {
        return rows.stream()
                .map(ScheduleView::getKindOfStudy)
                .map(ScheduleExportService::parseKind)
                .filter(Objects::nonNull)
                .distinct()
                .sorted() // порядок справочника: enum'ы сравниваются по ordinal
                .map(k -> new ScheduleWorkbookRenderer.Mark(k.getAbbreviationName(), k.getFullName()))
                .toList();
    }

    /** Виды ограничений, попавшие в сетку листа (в порядке справочника) — «Другие обозначения». */
    private static List<ScheduleWorkbookRenderer.Mark> constraintMarksOf(List<ConstraintData> constraints,
                                                                        LocalDate start, LocalDate end) {
        if (constraints == null || constraints.isEmpty()) return List.of();
        return constraints.stream()
                .filter(c -> !c.cell().getDate().isBefore(start) && !c.cell().getDate().isAfter(end))
                .map(ConstraintData::kind)
                .filter(Objects::nonNull)
                .distinct()
                .sorted() // порядок справочника: enum'ы сравниваются по ordinal
                .map(k -> new ScheduleWorkbookRenderer.Mark(k.getAbbreviationName(), k.getFullName()))
                .toList();
    }

    /** Карта «размещение → имена всех его групп» из строк проекции (для лекционного потока). */
    private static Map<UUID, Set<String>> groupsByPlacement(List<ScheduleView> rows) {
        Map<UUID, Set<String>> map = new HashMap<>();
        for (ScheduleView v : rows) {
            if (v.getPlacementId() == null || v.getGroupName() == null) continue;
            map.computeIfAbsent(v.getPlacementId(), k -> new TreeSet<>()).add(v.getGroupName());
        }
        return map;
    }

    /**
     * Легенда «Обозначения» группы из её же размещённых строк (без новых запросов): по дисциплине —
     * аббревиатура, название, лектор(ы), преподаватели других (нелекционных) видов занятий и кол-во
     * лекционных часов. Лекторов и «других» может быть несколько — собираем всех уникальных. Часы —
     * по <b>реально размещённым</b> лекциям: число лекционных пар × {@value #ACADEMIC_HOURS_PER_PAIR}
     * академ. часа (в модели хранимых часов нет). Порядок — по аббревиатуре/названию.
     */
    private List<ScheduleWorkbookRenderer.LegendRow> buildLegend(List<ScheduleView> rows,
                                                                 Map<UUID, Set<String>> streamGroups,
                                                                 Map<Integer, String> departments) {
        Map<String, List<ScheduleView>> byDiscipline = rows.stream()
                .collect(Collectors.groupingBy(
                        v -> nn(v.getDisciplineName()) + '\0' + nn(v.getDisciplineAbbr()),
                        LinkedHashMap::new, Collectors.toList()));

        List<ScheduleWorkbookRenderer.LegendRow> legend = new ArrayList<>();
        for (List<ScheduleView> disc : byDiscipline.values()) {
            String abbr = disc.get(0).getDisciplineAbbr();
            String name = disc.get(0).getDisciplineName();
            List<ScheduleView> lectures = disc.stream()
                    .filter(v -> "LECTURE".equals(v.getKindOfStudy()))
                    .toList();
            // «Другие виды занятий» — все НЕлекционные занятия дисциплины (ПЗ/ЛР/семинары/…).
            List<ScheduleView> nonLectures = disc.stream()
                    .filter(v -> !"LECTURE".equals(v.getKindOfStudy()))
                    .toList();

            String lecturers = distinctEducators(lectures);
            String others = distinctEducators(nonLectures);
            // Кол-во часов «лекц-нелекц»: число размещённых пар × 2 академ. часа, две цифры через дефис.
            int lectureHours = distinctPlacements(lectures) * ACADEMIC_HOURS_PER_PAIR;
            int nonLectureHours = distinctPlacements(nonLectures) * ACADEMIC_HOURS_PER_PAIR;
            String hours = (lectureHours == 0 && nonLectureHours == 0)
                    ? "" : lectureHours + "-" + nonLectureHours;
            // Отчёт — аббревиатуры зачётов/экзаменов дисциплины (если есть в расписании).
            String report = reportAbbreviations(disc);
            // Лекционный поток — все группы, слушающие лекции этой дисциплины вместе (со-группы по
            // общему placementId лекций). Пусто, если лекций нет.
            String lectureStream = lectures.stream()
                    .map(ScheduleView::getPlacementId)
                    .filter(Objects::nonNull)
                    .flatMap(pid -> streamGroups.getOrDefault(pid, Set.of()).stream())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));

            // «Каф» — подразделения ВСЕХ участников дисциплины (и лекторов, и ведущих остальные
            // виды): у дисциплины их может быть несколько, и это законно — межкафедральные потоки.
            String department = disc.stream()
                    .map(ScheduleView::getEducatorId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(departments::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));

            legend.add(new ScheduleWorkbookRenderer.LegendRow(
                    abbr, name, department, lecturers, others, hours, report, lectureStream));
        }
        legend.sort(Comparator.comparing(
                l -> Optional.ofNullable(l.abbr()).orElseGet(() -> nn(l.discipline())),
                String.CASE_INSENSITIVE_ORDER));
        return legend;
    }

    /**
     * Карта «преподаватель → его подразделение» для колонки «Каф»: краткое имя, а при его отсутствии
     * полное. Столбец бланка узкий, поэтому краткое имя — не косметика: длинное («Кафедра высшей
     * математики») перекрыло бы соседнюю колонку лектора.
     *
     * <p>Читается из master-данных <b>одним запросом на выгрузку</b>, а не из {@code schedule_view}:
     * подразделение в проекцию сознательно не денормализовано (иначе переименование кафедры обязано
     * было бы порождать перепроекцию — см. CQRS_ARCHITECTURE, «Что в проекцию НЕ кладут»).</p>
     *
     * <p>Показывается то подразделение, к которому преподаватель привязан фактически. Привязка
     * nullable и по модели это может быть не только кафедра, но и факультет или отдел: подставлять
     * вместо пустого значения догадку было бы хуже пустой ячейки.</p>
     */
    private Map<Integer, String> departmentsByEducator(List<ScheduleView> relevant) {
        Set<Integer> ids = relevant.stream()
                .map(ScheduleView::getEducatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();

        Map<Integer, String> byEducator = new HashMap<>();
        for (Educator educator : educatorRepository.findAllWithOrgUnitByIdIn(ids)) {
            OrgUnit unit = educator.getOrgUnit();
            if (unit == null) continue; // «без подразделения» — легитимное состояние, не ошибка
            String label = unit.getShortName() != null && !unit.getShortName().isBlank()
                    ? unit.getShortName() : unit.getName();
            if (label != null && !label.isBlank()) byEducator.put(educator.getId(), label);
        }
        return byEducator;
    }

    /** Уникальные имена преподавателей строк (в порядке появления), склеенные через запятую. */
    private static String distinctEducators(List<ScheduleView> rows) {
        return rows.stream()
                .map(ScheduleView::getEducatorName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    /** Число уникальных размещений (пар) в строках — одно размещение = одна пара. */
    private static int distinctPlacements(List<ScheduleView> rows) {
        return (int) rows.stream()
                .map(ScheduleView::getPlacementId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /** Виды занятий-«отчёты» для колонки «Отчет»: зачёты (с оценкой/без) и экзамен. */
    private static final Set<KindOfStudy> REPORT_KINDS = EnumSet.of(
            KindOfStudy.EXAM, KindOfStudy.CREDIT_WITH_GRADE, KindOfStudy.CREDIT_WITHOUT_GRADE);

    /** Аббревиатуры зачётов/экзаменов дисциплины (уникальные, через запятую); пусто — если их нет. */
    private static String reportAbbreviations(List<ScheduleView> rows) {
        return rows.stream()
                .map(ScheduleView::getKindOfStudy)
                .map(ScheduleExportService::parseKind)
                .filter(Objects::nonNull)
                .filter(REPORT_KINDS::contains)
                .distinct()
                .map(KindOfStudy::getAbbreviationName)
                .collect(Collectors.joining(", "));
    }

    /** Безопасный разбор строки вида занятия в enum ({@code null}, если значение неизвестно). */
    private static KindOfStudy parseKind(String name) {
        if (name == null) return null;
        try { return KindOfStudy.valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }

    private static String nn(String s) { return s == null ? "" : s; }

    /** Ограничения сущности → «date_SLOT → аббревиатура» в пределах периода (для пустых ячеек). */
    private Map<String, String> constraintMapFor(List<ConstraintData> constraints, LocalDate start, LocalDate end) {
        if (constraints == null || constraints.isEmpty()) return Map.of();
        Map<String, String> map = new HashMap<>();
        for (ConstraintData c : constraints) {
            LocalDate date = c.cell().getDate();
            if (date.isBefore(start) || date.isAfter(end)) continue;
            map.put(date + "_" + c.cell().getTimeSlotPair().name(), c.kind().getAbbreviationName());
        }
        return map;
    }

    private String buildFilename(StudyPeriod period, ExportAxis axis, String scopeName, String ext) {
        String raw = "Расписание_" + axis.title() + "_" + scopeName + "_" + period.getName();
        return raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ext;
    }
}
