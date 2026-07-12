package ru.services.exporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.ScheduledLessonDto;
import ru.entity.StudyPeriod;
import ru.entity.constraints.ConstraintData;
import ru.entity.read.ScheduleView;
import ru.repository.read.ScheduleViewRepository;
import ru.services.ScheduleResponseService;
import ru.services.StudyPeriodService;
import ru.services.constraints.ConstraintService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Выгрузка расписания периода в Excel (Facade + SRP-координатор): период → скоуп по оси → сетка
 * из {@code schedule_view} + ограничения сущности → заполнение бланка ({@link ScheduleWorkbookRenderer}).
 * Раскладки/POI здесь нет, чтения БД в рендерере нет.
 *
 * <p>Источник — Query Side, поэтому файл отражает <b>реально сохранённое</b> расписание (ручная
 * раскладка, пины). Строки склеиваются в занятия тем же {@link ScheduleResponseService#buildGridFromViews},
 * что кормит UI. Пустые ячейки бланка подписываются ограничением сущности, как в историческом экспорте.</p>
 *
 * <p>Скоуп: {@code entityId} задан — одна сущность (один лист); иначе — все сущности оси в
 * расписании периода (лист на каждую, по имени). Сущности без размещений не появляются.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleExportService {

    private final StudyPeriodService studyPeriodService;
    private final ScheduleViewRepository viewRepository;
    private final ScheduleResponseService responseService;
    private final ConstraintService constraintService;
    private final ScheduleWorkbookRenderer renderer;

    /** Результат выгрузки: байты книги + имя файла (кириллица, кодируется в контроллере). */
    public record ExportResult(byte[] bytes, String filename) {}

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

        List<ScheduleWorkbookRenderer.SheetData> sheets = new ArrayList<>();
        String scopeName;

        if (entityId != null) {
            List<ScheduleView> rows = relevant.stream()
                    .filter(v -> entityId.equals(axis.entityId(v)))
                    .toList();
            scopeName = rows.isEmpty() ? ("#" + entityId) : axis.entityName(rows.get(0));
            sheets.add(sheetOf(scopeName, entityId, rows, axisConstraints, start, end));
        } else {
            Map<Integer, List<ScheduleView>> byEntity = relevant.stream()
                    .collect(Collectors.groupingBy(axis::entityId));
            byEntity.entrySet().stream()
                    .sorted(Comparator.comparing(
                            (Map.Entry<Integer, List<ScheduleView>> e) ->
                                    Optional.ofNullable(axis.entityName(e.getValue().get(0))).orElse(""),
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(e -> sheets.add(sheetOf(
                            axis.entityName(e.getValue().get(0)), e.getKey(), e.getValue(), axisConstraints, start, end)));
            scopeName = "все (" + axis.title().toLowerCase(Locale.ROOT) + ")";
        }

        byte[] bytes = renderer.render(sheets, start, end, axis);
        String filename = buildFilename(period, axis, scopeName);
        log.info("Экспорт расписания (бланк): период id={}, ось={}, сущность={}, листов={}, {} байт",
                periodId, axis, entityId, sheets.size(), bytes.length);
        return new ExportResult(bytes, filename);
    }

    /** Собирает данные листа: сетка занятий + карта ограничений сущности по ячейкам. */
    private ScheduleWorkbookRenderer.SheetData sheetOf(String name, Integer entityId, List<ScheduleView> rows,
                                                       Map<Integer, List<ConstraintData>> axisConstraints,
                                                       LocalDate start, LocalDate end) {
        Map<String, List<ScheduledLessonDto>> grid = responseService.buildGridFromViews(rows);
        Map<String, String> constraintAbbr = constraintMapFor(axisConstraints.get(entityId), start, end);
        return new ScheduleWorkbookRenderer.SheetData(name, grid, constraintAbbr);
    }

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

    private String buildFilename(StudyPeriod period, ExportAxis axis, String scopeName) {
        String raw = "Расписание_" + axis.title() + "_" + scopeName + "_" + period.getName() + ".xlsx";
        return raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
