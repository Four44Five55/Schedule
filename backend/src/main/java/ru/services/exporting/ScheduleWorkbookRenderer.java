package ru.services.exporting;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import ru.dto.ScheduledLessonDto;
import ru.enums.TimeSlotPair;
import ru.services.ScheduleDaysSlotsConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Рендер расписания в Excel заполнением бланка {@code templates/template.xlsx} (лист «Расписание»),
 * SRP — только POI, без чтения БД/бизнес-логики.
 *
 * <p>Шаблон — матрица institutional-формы: строки = день × пара × 3 строки-данных, столбцы = недели.
 * Заполняются только ячейки недельных столбцов (шапка/левые метки/легенда — статика шаблона).
 * Геометрия воспроизводит исторический {@code ExcelExportService} и сверена с merge бланка
 * (Пн–Пт по 12 строк, Сб — 9 + спец-зона 4-й пары `A83:A85`):</p>
 * <pre>
 *   столбец недели = FIRST_WEEK_COL + (номер недели периода)
 *   строка даты дня = FIRST_DATE_ROW + (день недели Пн..Сб) * DAY_STRIDE
 *   строки пары s   = строка_даты + 1 + s * LINES_PER_SLOT  (3 строки: см. ExportAxis.cellLines)
 * </pre>
 *
 * <p>Одна сущность — один лист (первый = лист-шаблон, остальные — {@code cloneSheet} пристина-шаблона,
 * каждый = точный бланк). В отличие от старого кода — позиционирование по фактическому дню недели и
 * номеру недели (корректно и при старте не с понедельника; для Пн идентично) и стиль ячеек бланка
 * (границы) сохраняется.</p>
 *
 * <p><b>Требование к шаблону:</b> {@code cloneSheet}/{@code setSheetName} заставляют POI перепарсить
 * формулы листа; формулы с внешним макросом ({@code [1]!CountLectures}) парсер не понимает и падает
 * с {@code FormulaParseException}. Такие формулы в бланке быть НЕ должны (убраны из template.xlsx).</p>
 */
@Component
public class ScheduleWorkbookRenderer {

    private static final String TEMPLATE = "templates/template.xlsx";
    private static final String TEMPLATE_SHEET = "Расписание";

    private static final Locale RU = Locale.forLanguageTag("ru");

    // Геометрия бланка (POI, 0-индекс). Сверено с merge A8:A19 (Пн) … A73:A81 (Сб).
    private static final int MONTH_ROW = 5;        // строка «месяц» (Excel 6): метка на смене месяца
    private static final int FIRST_DATE_ROW = 6;   // строка даты блока «Пн» (Excel 7 = строка «даты»)
    private static final int FIRST_WEEK_COL = 3;   // столбец D — первая неделя
    private static final int LAST_TEMPLATE_WEEK_COL = 32; // AG — последний недельный столбец шаблона (нед. 30)
    private static final int LAST_COL = 80;        // столбец CC (dimension A1:CC106) — предел недель
    private static final int DAY_STRIDE = 13;      // строка даты + 4 пары × 3 строки
    private static final int LINES_PER_SLOT = 3;

    /**
     * Один лист выгрузки: имя (сущность), сетка «date_SLOT → занятия» (как отдаёт Query Side) и
     * карта ограничений «date_SLOT → аббревиатура» (для пустых ячеек, как в историческом экспорте).
     */
    public record SheetData(String name,
                            Map<String, List<ScheduledLessonDto>> grid,
                            Map<String, String> constraintAbbr) {}

    /**
     * @param sheets листы (для одной сущности — один); первый занимает лист-шаблон, остальные —
     *               клоны пристина-шаблона (каждый = точный бланк)
     * @param start  начало периода (включительно)
     * @param end    конец периода (включительно)
     * @param axis   перспектива (задаёт содержимое ячеек)
     * @return байты .xlsx (заполненный бланк)
     */
    public byte[] render(List<SheetData> sheets, LocalDate start, LocalDate end, ExportAxis axis) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(TEMPLATE)) {
            if (is == null) {
                throw new IllegalStateException("Шаблон не найден в ресурсах: " + TEMPLATE);
            }
            try (XSSFWorkbook wb = new XSSFWorkbook(is); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                int templateIdx = wb.getSheetIndex(TEMPLATE_SHEET);
                if (templateIdx < 0) {
                    throw new IllegalStateException("Лист '" + TEMPLATE_SHEET + "' не найден в шаблоне");
                }

                if (sheets.isEmpty()) {
                    wb.write(out); // пустой период — отдаём чистый бланк как есть
                    return out.toByteArray();
                }

                // Клонируем ДО заполнения, чтобы каждый клон был пристина-шаблоном.
                List<Sheet> targets = new ArrayList<>();
                targets.add(wb.getSheetAt(templateIdx));
                for (int i = 1; i < sheets.size(); i++) {
                    targets.add(wb.cloneSheet(templateIdx));
                }

                CellStyle dateStyle = buildDateStyle(wb, targets.get(0));
                Set<String> usedNames = new HashSet<>();
                for (int i = 0; i < sheets.size(); i++) {
                    Sheet sheet = targets.get(i);
                    wb.setSheetName(wb.getSheetIndex(sheet), safeSheetName(sheets.get(i).name(), usedNames));
                    fillSheet(sheet, sheets.get(i), start, end, axis, dateStyle);
                }

                wb.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать Excel-книгу расписания", e);
        }
    }

    private void fillSheet(Sheet sheet, SheetData data, LocalDate start, LocalDate end,
                           ExportAxis axis, CellStyle dateStyle) {
        writeMonthHeader(sheet, start, end);

        LocalDate startMonday = start.with(DayOfWeek.MONDAY);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) continue; // Вс не учебное

            int weekIdx = (int) ChronoUnit.WEEKS.between(startMonday, date.with(DayOfWeek.MONDAY));
            int col = FIRST_WEEK_COL + weekIdx;
            if (col > LAST_COL) continue; // за пределами бланка — пропускаем

            int dateRow = FIRST_DATE_ROW + (dow.getValue() - 1) * DAY_STRIDE; // Пн..Сб → 0..5

            Cell dateCell = cell(sheet, dateRow, col);
            dateCell.setCellValue(date);         // POI 5.x: setCellValue(LocalDate)
            dateCell.setCellStyle(dateStyle);    // формат "dd" + границы бланка

            for (TimeSlotPair slot : TimeSlotPair.values()) {
                if (!ScheduleDaysSlotsConfig.isSlotAvailable(date, slot)) continue; // Сб-4 → спец-зона бланка

                int baseRow = dateRow + 1 + slot.ordinal() * LINES_PER_SLOT;
                String key = date + "_" + slot.name();
                List<ScheduledLessonDto> lessons = data.grid().get(key);

                if (lessons != null && !lessons.isEmpty()) {
                    List<String> lines = axis.cellLines(lessons.get(0)); // 3 строки
                    for (int k = 0; k < LINES_PER_SLOT; k++) {
                        cell(sheet, baseRow + k, col).setCellValue(k < lines.size() ? lines.get(k) : "");
                    }
                } else {
                    // Пустая ячейка: ограничение сущности — в среднюю строку (как исторический экспорт).
                    String abbr = data.constraintAbbr().get(key);
                    if (abbr != null) {
                        cell(sheet, baseRow + 1, col).setCellValue(abbr);
                    }
                }
            }
        }
    }

    /**
     * Заполняет строку «месяц» бланка по фактическому периоду: сначала чистит стале-метки примера
     * шаблона по всем недельным столбцам, затем пишет название месяца в столбец недели <b>при смене
     * месяца</b> (как в бланке — одна метка на месяц). Месяц недели — по её четвергу (середина
     * учебной недели Пн–Сб), чтобы недели на стыке месяцев подписывались основным месяцем.
     */
    private void writeMonthHeader(Sheet sheet, LocalDate start, LocalDate end) {
        // Чистим стале-пример шаблона (месяцы + даты по 6 дням) во ВСЕХ недельных столбцах —
        // иначе в неиспользуемых периодом столбцах остаются даты/месяцы 2023 года.
        for (int c = FIRST_WEEK_COL; c <= LAST_TEMPLATE_WEEK_COL; c++) {
            blank(sheet, MONTH_ROW, c);
            for (int d = 0; d < 6; d++) {
                blank(sheet, FIRST_DATE_ROW + d * DAY_STRIDE, c);
            }
        }

        LocalDate startMonday = start.with(DayOfWeek.MONDAY);
        LocalDate endMonday = end.with(DayOfWeek.MONDAY);
        Month prev = null;
        for (LocalDate week = startMonday; !week.isAfter(endMonday); week = week.plusWeeks(1)) {
            int col = FIRST_WEEK_COL + (int) ChronoUnit.WEEKS.between(startMonday, week);
            if (col > LAST_COL) break;
            Month month = week.plusDays(3).getMonth(); // четверг недели — представитель месяца
            if (month != prev) {
                cell(sheet, MONTH_ROW, col).setCellValue(month.getDisplayName(TextStyle.FULL_STANDALONE, RU));
                prev = month;
            }
        }
    }

    /** Дата как «dd» с сохранением стиля (границ) ячейки бланка, если он там есть. */
    private CellStyle buildDateStyle(Workbook wb, Sheet templateSheet) {
        CellStyle style = wb.createCellStyle();
        Row row = templateSheet.getRow(FIRST_DATE_ROW);
        Cell sample = row != null ? row.getCell(FIRST_WEEK_COL) : null;
        if (sample != null && sample.getCellStyle() != null) {
            style.cloneStyleFrom(sample.getCellStyle()); // наследуем границы/шрифт бланка
        }
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd"));
        return style;
    }

    /** Очищает значение ячейки (если она есть), сохраняя стиль бланка. */
    private void blank(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        Cell c = r != null ? r.getCell(col) : null;
        if (c != null) c.setBlank();
    }

    /** Ячейка по (row, col); строка/ячейка создаются при отсутствии (стиль бланка сохраняется). */
    private Cell cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col);
        if (c == null) c = r.createCell(col);
        return c;
    }

    /** Имя листа Excel: ≤31 символа, без запрещённых символов, непустое и уникальное в книге. */
    private String safeSheetName(String raw, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(raw == null || raw.isBlank() ? "Лист" : raw);
        String name = base;
        int n = 2;
        while (!used.add(name)) {
            String suffix = " (" + n++ + ")";
            name = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
        }
        return name;
    }
}
