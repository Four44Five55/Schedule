package ru.services.exporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.ScheduledLessonDto;
import ru.entity.Assignment;
import ru.entity.Group;
import ru.entity.StudyPeriod;
import ru.entity.constraints.ConstraintData;
import ru.entity.constraints.ConstraintKindRef;
import ru.entity.Educator;
import ru.entity.OrgUnit;
import ru.entity.read.ScheduleView;
import ru.enums.KindOfStudy;
import ru.repository.AssignmentRepository;
import ru.repository.EducatorRepository;
import ru.repository.read.ScheduleViewRepository;
import ru.services.ScheduleResponseService;
import ru.services.StudyPeriodService;
import ru.services.constraints.ConstraintService;
import ru.services.educator.EducatorCredentialsAssembler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final AssignmentRepository assignmentRepository;
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
        // Запасные (И-22) — только для группы: их нет ни в одной строке проекции (занятий они не
        // ведут), поэтому подвал — единственное место, где они показываются, и данные приходится
        // брать с write-стороны. Ключ — группа, потом дисциплина.
        Map<Integer, Map<String, ReserveSlot>> reserve = axis == ExportAxis.GROUP
                ? reserveByGroup(periodId) : Map.of();
        // Подписи участников для таблицы «Обозначения» (кафедра + регалии) — тоже только для
        // группы: у преподавателя и аудитории таблица удаляется целиком. Запасные подмешиваются в
        // тот же запрос: подпись им нужна ровно та же, а второго похода в БД она не стоит.
        Map<Integer, EducatorLabel> educatorLabels = axis == ExportAxis.GROUP
                ? labelsByEducator(relevant, reserveEducatorIds(reserve)) : Map.of();

        // Правый край листов: последняя дата, на которой в расписании периода есть занятие.
        LocalDate contentEnd = contentEnd(relevant, end);
        // Подпись под расписанием — реквизит документа, живёт у периода (чейнджлог 026). Одна на всю
        // выгрузку: подписант один, и в ZIP на сотню файлов он тот же самый.
        var signature = signatureOf(period);

        // Одна сущность — одна книга .xlsx (как раньше).
        if (entityId != null) {
            List<ScheduleView> rows = relevant.stream()
                    .filter(v -> entityId.equals(axis.entityId(v)))
                    .toList();
            String scopeName = rows.isEmpty() ? ("#" + entityId) : axis.entityName(rows.get(0));
            var sheet = sheetOf(scopeName, entityId, rows, axisConstraints, start, end, axis,
                    streamGroups, educatorLabels, reserve);
            byte[] bytes = renderer.render(List.of(sheet), start, end, contentEnd, axis, signature);
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
                        e.getValue(), axisConstraints, start, end, axis, streamGroups, educatorLabels,
                        reserve))
                .toList();

        byte[] bytes = zipPerEntity(sheets, start, end, contentEnd, axis, signature);
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
                                LocalDate start, LocalDate end, LocalDate contentEnd, ExportAxis axis,
                                ScheduleWorkbookRenderer.Signature signature) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, java.nio.charset.StandardCharsets.UTF_8)) {
            Set<String> usedNames = new HashSet<>();
            for (ScheduleWorkbookRenderer.SheetData sheet : sheets) {
                byte[] book = renderer.render(List.of(sheet), start, end, contentEnd, axis, signature);
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

    /**
     * Подпись под расписанием из реквизитов периода.
     *
     * <p>Читается здесь, а не в рендерере: рендерер о БД не знает. Ничего не сохраняет — подпись
     * правится своей командой ({@code PUT /api/study-periods/{id}/signature}), а выгрузка остаётся
     * чистым чтением.</p>
     */
    private ScheduleWorkbookRenderer.Signature signatureOf(StudyPeriod period) {
        return new ScheduleWorkbookRenderer.Signature(
                period.getSignerPosition(), period.getSignerCredentials(), period.getSignerName());
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
                                                       Map<Integer, EducatorLabel> educatorLabels,
                                                       Map<Integer, Map<String, ReserveSlot>> reserve) {
        Map<String, List<ScheduledLessonDto>> grid = responseService.buildGridFromViews(rows);
        List<ConstraintData> constraints = axisConstraints.get(entityId);
        Map<String, String> constraintAbbr = constraintMapFor(constraints, start, end);
        // Таблица «Обозначения» бланка и расшифровки под ней — только для группы; для препода и
        // аудитории таблица вычищается, а вместе с ней уходит и место под расшифровки.
        boolean group = axis == ExportAxis.GROUP;
        List<ScheduleWorkbookRenderer.LegendRow> legend = group
                ? buildLegend(rows, streamGroups, educatorLabels,
                        reserve.getOrDefault(entityId, Map.of()))
                : List.of();
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
                // Порядок справочника — им теперь распоряжается пользователь (sortOrder), а не
                // ordinal enum-а; при равном порядке сортируем по аббревиатуре, чтобы легенда
                // не прыгала между выгрузками.
                .sorted(Comparator.comparingInt(ConstraintKindRef::sortOrder)
                        .thenComparing(ConstraintKindRef::abbreviation))
                .map(k -> new ScheduleWorkbookRenderer.Mark(k.abbreviation(), k.fullName()))
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
     * Легенда «Обозначения» группы из её же размещённых строк: по дисциплине — аббревиатура,
     * название, лектор(ы), преподаватели других (нелекционных) видов занятий и кол-во лекционных
     * часов. Лекторов и «других» может быть несколько — собираем всех уникальных. Часы — по
     * <b>реально размещённым</b> лекциям: число лекционных пар × {@value #ACADEMIC_HOURS_PER_PAIR}
     * академ. часа (в модели хранимых часов нет). Порядок — по аббревиатуре/названию.
     *
     * <p><b>Запасные (И-22) дописываются после ведущих</b>, без пометки (решение заказчика
     * 2026-08-24): в исходных файлах подвал ролей не различает, и бланк воспроизводит их форму.
     * Порядок — единственное, что о роли говорит: сначала те, у кого занятия стоят поклеточно.
     * Колонку выбирает вид занятия назначения: лекционное → «Лектор», любое другое → «Другие виды
     * занятий».</p>
     *
     * <p>⚠️ Часы и «Каф» запасные не меняют: часы считаются по размещённым парам (запасной их не
     * ведёт), а «Каф» описывает подразделения тех, кто занятие проводит.</p>
     *
     * <p>⚠️ Строки заводятся по размещённым занятиям, поэтому дисциплина, у которой в этой группе
     * не размещено <b>ничего</b>, в подвал не попадает — вместе со своими запасными. Это то же
     * правило, что и для ведущих: подвал описывает расписание, а не учебный план.</p>
     */
    private List<ScheduleWorkbookRenderer.LegendRow> buildLegend(List<ScheduleView> rows,
                                                                 Map<UUID, Set<String>> streamGroups,
                                                                 Map<Integer, EducatorLabel> educatorLabels,
                                                                 Map<String, ReserveSlot> reserve) {
        Map<String, List<ScheduleView>> byDiscipline = rows.stream()
                .collect(Collectors.groupingBy(
                        v -> disciplineKey(v.getDisciplineName(), v.getDisciplineAbbr()),
                        LinkedHashMap::new, Collectors.toList()));

        List<ScheduleWorkbookRenderer.LegendRow> legend = new ArrayList<>();
        for (Map.Entry<String, List<ScheduleView>> entry : byDiscipline.entrySet()) {
            List<ScheduleView> disc = entry.getValue();
            ReserveSlot reserveSlot = reserve.getOrDefault(entry.getKey(), ReserveSlot.EMPTY);
            String abbr = disc.get(0).getDisciplineAbbr();
            String name = disc.get(0).getDisciplineName();
            List<ScheduleView> lectures = disc.stream()
                    .filter(v -> "LECTURE".equals(v.getKindOfStudy()))
                    .toList();
            // «Другие виды занятий» — все НЕлекционные занятия дисциплины (ПЗ/ЛР/семинары/…).
            List<ScheduleView> nonLectures = disc.stream()
                    .filter(v -> !"LECTURE".equals(v.getKindOfStudy()))
                    .toList();

            String lecturers = withReserve(distinctEducators(lectures, educatorLabels),
                    lectures, reserveSlot.lecturers(), educatorLabels);
            String others = withReserve(distinctEducators(nonLectures, educatorLabels),
                    nonLectures, reserveSlot.others(), educatorLabels);
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
                    .map(educatorLabels::get)
                    .filter(Objects::nonNull)
                    .map(EducatorLabel::department)
                    .filter(d -> d != null && !d.isBlank())
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
     * Что бланк пишет о преподавателе: подразделение для колонки «Каф» и подпись с регалиями для
     * колонок лектора и ведущих остальные виды.
     *
     * @param department краткое имя подразделения («ВМ»); пусто — привязки нет
     * @param titleLine  подпись «п-к юст Иванов И.И., ктн, доц»; без регалий равна ФИО
     */
    private record EducatorLabel(String department, String titleLine) {}

    /**
     * Карта «преподаватель → подписи для таблицы „Обозначения“»: подразделение и ФИО с регалиями.
     *
     * <p>Подразделение — краткое имя, а при его отсутствии полное. Столбец бланка узкий, поэтому
     * краткое имя — не косметика: длинное («Кафедра высшей математики») перекрыло бы соседнюю
     * колонку лектора.</p>
     *
     * <p>Читается из master-данных <b>одним запросом на выгрузку</b>, а не из {@code schedule_view}:
     * ни подразделение, ни регалии в проекцию сознательно не денормализованы (иначе переименование
     * кафедры и присвоение звания обязаны были бы порождать перепроекцию — см. CQRS_ARCHITECTURE,
     * «Что в проекцию НЕ кладут»). Регалии приезжают тем же запросом, что и кафедра: это простые
     * поля той же сущности, второго обращения к БД они не стоят.</p>
     *
     * <p>Показывается то подразделение, к которому преподаватель привязан фактически. Привязка
     * nullable и по модели это может быть не только кафедра, но и факультет или отдел: подставлять
     * вместо пустого значения догадку было бы хуже пустой ячейки. То же и с регалиями — их
     * отсутствие даёт просто ФИО.</p>
     */
    private Map<Integer, EducatorLabel> labelsByEducator(List<ScheduleView> relevant,
                                                         Set<Integer> extraIds) {
        Set<Integer> ids = relevant.stream()
                .map(ScheduleView::getEducatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        // Запасные (И-22) в проекции не встречаются вовсе, а подпись в подвале им нужна такая же.
        ids.addAll(extraIds);
        if (ids.isEmpty()) return Map.of();

        Map<Integer, EducatorLabel> byEducator = new HashMap<>();
        for (Educator educator : educatorRepository.findAllWithDetailsByIdIn(ids)) {
            OrgUnit unit = educator.getOrgUnit();
            String department = null;
            if (unit != null) { // «без подразделения» — легитимное состояние, не ошибка
                department = unit.getShortName() != null && !unit.getShortName().isBlank()
                        ? unit.getShortName() : unit.getName();
            }
            // Подпись собирает общий форматтер — тот же, что кормит карточку преподавателя.
            byEducator.put(educator.getId(),
                    new EducatorLabel(department, EducatorCredentialsAssembler.lineOf(educator)));
        }
        return byEducator;
    }

    /**
     * Уникальные преподаватели строк (в порядке появления) — подписью с регалиями, через запятую.
     *
     * <p>Различаются по {@code educatorId}, а склеиваются из master-данных: имя в
     * {@code schedule_view} — снимок без регалий, и брать текст оттуда значило бы завести второй
     * формат подписи. Если преподавателя в карте почему-то нет, остаётся имя из проекции —
     * потерять человека в легенде хуже, чем показать его без звания.</p>
     */
    private static String distinctEducators(List<ScheduleView> rows,
                                            Map<Integer, EducatorLabel> educatorLabels) {
        return rows.stream()
                .filter(v -> v.getEducatorId() != null)
                .collect(Collectors.toMap(
                        ScheduleView::getEducatorId,
                        v -> {
                            EducatorLabel label = educatorLabels.get(v.getEducatorId());
                            return label != null && label.titleLine() != null && !label.titleLine().isBlank()
                                    ? label.titleLine() : nn(v.getEducatorName());
                        },
                        (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * Дописывает запасных (И-22) после ведущих в одну колонку подвала — без пометки, тем же
     * разделителем.
     *
     * <p>Из состава выбрасываются те, кто уже назван ведущим в этой же колонке: человек может
     * числиться запасным по дисциплине и при этом вести часть занятий её вида (замена состоялась,
     * а назначение осталось). Показать его дважды значило бы соврать о числе преподавателей.</p>
     *
     * @param leading    уже собранная строка ведущих (может быть пустой)
     * @param leadingRows строки, из которых она собрана — по ним считается, кого уже назвали
     * @param reserveIds запасные этой колонки, в порядке заведения
     * @param labels     подписи преподавателей (кафедра + регалии)
     */
    private static String withReserve(String leading, List<ScheduleView> leadingRows,
                                      Set<Integer> reserveIds, Map<Integer, EducatorLabel> labels) {
        if (reserveIds.isEmpty()) {
            return leading;
        }
        Set<Integer> alreadyNamed = leadingRows.stream()
                .map(ScheduleView::getEducatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        String tail = reserveIds.stream()
                .filter(id -> !alreadyNamed.contains(id))
                .map(labels::get)
                .filter(Objects::nonNull)
                .map(EducatorLabel::titleLine)
                .filter(line -> line != null && !line.isBlank())
                .collect(Collectors.joining(", "));
        if (tail.isEmpty()) {
            return leading;
        }
        return leading.isBlank() ? tail : leading + ", " + tail;
    }

    /**
     * Запасные (И-22) периода, разложенные «группа → дисциплина → лекционные/остальные».
     *
     * <p>Раскладка по группам, а не по потокам: лист бланка выписывается на группу, и запасной
     * лекционного назначения обязан появиться в подвале каждой группы потока — ровно там же, где
     * стоят сами лекции.</p>
     *
     * <p>Колонку выбирает {@code kindOfStudy} слота: {@link KindOfStudy#LECTURE} → «Лектор», всё
     * остальное → «Другие виды занятий». Отдельного признака роли у запасного нет намеренно — вид
     * занятия уже известен из плана, и второй источник мог бы с ним разойтись.</p>
     */
    private Map<Integer, Map<String, ReserveSlot>> reserveByGroup(Integer periodId) {
        List<Assignment> withReserve = assignmentRepository.findWithReserveByPeriodId(periodId);
        if (withReserve.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Map<String, ReserveSlot>> byGroup = new HashMap<>();
        for (Assignment a : withReserve) {
            var discipline = a.getCurriculumSlot().getDisciplineCourse().getDiscipline();
            String key = disciplineKey(discipline.getName(), discipline.getAbbreviation());
            boolean lecture = a.getCurriculumSlot().getKindOfStudy() == KindOfStudy.LECTURE;
            List<Integer> ids = a.getReserveEducators().stream()
                    .map(Educator::getId)
                    .sorted() // состав — Set: без сортировки порядок колонки плавал бы между выгрузками
                    .toList();
            for (Group group : a.getStudyStream().getGroups()) {
                ReserveSlot slot = byGroup
                        .computeIfAbsent(group.getId(), g -> new HashMap<>())
                        .computeIfAbsent(key, k -> ReserveSlot.empty());
                (lecture ? slot.lecturers() : slot.others()).addAll(ids);
            }
        }
        return byGroup;
    }

    /** Все запасные выгрузки — чтобы подписи (кафедра, регалии) пришли тем же запросом, что и у ведущих. */
    private static Set<Integer> reserveEducatorIds(Map<Integer, Map<String, ReserveSlot>> reserve) {
        return reserve.values().stream()
                .flatMap(byDiscipline -> byDiscipline.values().stream())
                .flatMap(slot -> Stream.concat(slot.lecturers().stream(), slot.others().stream()))
                .collect(Collectors.toSet());
    }

    /**
     * Запасные одной группы по одной дисциплине, разложенные по колонкам подвала.
     *
     * <p>{@link LinkedHashSet} — потому что колонка печатается перечнем: нужен и порядок
     * (устойчивый между выгрузками), и отсутствие повторов (один человек может быть запасным на
     * нескольких занятиях одного вида).</p>
     */
    private record ReserveSlot(LinkedHashSet<Integer> lecturers, LinkedHashSet<Integer> others) {

        /** Общая пустая: дисциплин без запасных подавляющее большинство, плодить объекты незачем. */
        private static final ReserveSlot EMPTY =
                new ReserveSlot(new LinkedHashSet<>(), new LinkedHashSet<>());

        private static ReserveSlot empty() {
            return new ReserveSlot(new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    /** Ключ дисциплины в подвале: название + аббревиатура (обе части значащие — тёзки различаются). */
    private static String disciplineKey(String disciplineName, String disciplineAbbr) {
        return nn(disciplineName) + '\0' + nn(disciplineAbbr);
    }

    /** Число уникальных размещений (пар) в строках — одно размещение = одна пара. */
    private static int distinctPlacements(List<ScheduleView> rows) {
        return (int) rows.stream()
                .map(ScheduleView::getPlacementId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /** Аббревиатуры зачётов/экзаменов дисциплины (уникальные, через запятую); пусто — если их нет. */
    private static String reportAbbreviations(List<ScheduleView> rows) {
        return rows.stream()
                .map(ScheduleView::getKindOfStudy)
                .map(ScheduleExportService::parseKind)
                .filter(Objects::nonNull)
                .filter(KindOfStudy::isAssessment) // колонка «Отчет» — ровно аттестации курса
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
            map.put(date + "_" + c.cell().getTimeSlotPair().name(), c.kind().abbreviation());
        }
        return map;
    }

    private String buildFilename(StudyPeriod period, ExportAxis axis, String scopeName, String ext) {
        String raw = "Расписание_" + axis.title() + "_" + scopeName + "_" + period.getName();
        return raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ext;
    }
}
