package ru.services.exporting;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import ru.dto.ScheduledLessonDto;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.services.ScheduleDaysSlotsConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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

    // Таблица «Обозначения» внизу бланка (только группа): шапка стр. 83–85, данные 86–96 (11 строк).
    private static final int LEGEND_HEADER_FIRST_ROW = 82; // 0-индекс, Excel 83
    private static final int LEGEND_FIRST_DATA_ROW = 85;   // 0-индекс, Excel 86
    private static final int LEGEND_LAST_DATA_ROW = 95;    // 0-индекс, Excel 96
    private static final int LEGEND_LAST_ROW = 105;        // 0-индекс, Excel 106 (для полной очистки)
    private static final int LEGEND_COL_ABBR = 0;          // A — обозначение
    private static final int LEGEND_COL_DISCIPLINE = 1;    // B:H — дисциплина (пишем в якорь B)
    private static final int LEGEND_COL_LECTURER = 9;      // J:M — лектор (якорь J)
    private static final int LEGEND_COL_OTHERS = 13;       // N:Q — другие виды занятий (якорь N)
    private static final int LEGEND_COL_HOURS = 17;        // R — кол-во часов «лекц-нелекц»
    private static final int LEGEND_COL_REPORT = 18;       // S — отчёт (вид сдачи)
    private static final int LEGEND_COL_STREAM = 19;       // T:U — поток лекционный (якорь T)
    private static final int LEGEND_LAST_COL = 23;         // X — правый край легенды (Дом.задание V:X)

    /**
     * Один лист выгрузки: имя (сущность), сетка «date_SLOT → занятия» (как отдаёт Query Side),
     * карта ограничений «date_SLOT → аббревиатура» (для пустых ячеек, как в историческом экспорте)
     * и строки легенды «Обозначения» (заполняются только для группы; для препода/аудитории пусто).
     */
    public record SheetData(String name,
                            Map<String, List<ScheduledLessonDto>> grid,
                            Map<String, String> constraintAbbr,
                            List<LegendRow> legend) {}

    /**
     * Одна строка таблицы «Обозначения» бланка (для группы): аббревиатура и название дисциплины,
     * лектор(ы), преподаватели других (нелекционных) видов занятий, кол-во часов «лекц-нелекц»
     * (напр. {@code "12-26"}), отчёт — аббревиатуры зачётов/экзаменов, и лекционный поток — группы,
     * слушающие лекции вместе. Лекторов, «других», отчётов и групп потока может быть несколько.
     */
    public record LegendRow(String abbr, String discipline, String lecturer, String others,
                            String hours, String report, String lectureStream) {}

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
                CellStyler styler = new CellStyler(wb); // заливки по виду + выравнивание влево
                Set<String> usedNames = new HashSet<>();
                for (int i = 0; i < sheets.size(); i++) {
                    Sheet sheet = targets.get(i);
                    wb.setSheetName(wb.getSheetIndex(sheet), safeSheetName(sheets.get(i).name(), usedNames));
                    fillSheet(sheet, sheets.get(i), start, end, axis, dateStyle, styler);
                }

                wb.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать Excel-книгу расписания", e);
        }
    }

    private void fillSheet(Sheet sheet, SheetData data, LocalDate start, LocalDate end,
                           ExportAxis axis, CellStyle dateStyle, CellStyler styler) {
        writeMonthHeader(sheet, start, end);
        writeEntityHeader(sheet, data.name(), start, end, axis);
        // Таблица «Обозначения» внизу бланка — групповая по смыслу (дисциплины/лекторы/часы группы).
        // Преподавателю и аудитории она не нужна — чистим целиком; группе — заполняем реальными данными.
        if (axis == ExportAxis.GROUP) {
            writeDisciplineLegend(sheet, data.legend(), styler);
        } else {
            deleteDisciplineLegend(sheet);
        }

        // Данные ячеек занятий (вид/тема/дисциплина/группы/аудитория) — по центру (шаблонный дефолт).
        // Влево выравниваются только колонки лекторов в легенде (см. writeDisciplineLegend).

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
                    ScheduledLessonDto lesson = lessons.get(0);
                    List<String> lines = axis.cellLines(lesson); // 3 строки
                    CellStyler.Tint tint = tintForKind(lesson.kindOfStudy()); // цвет по виду занятия
                    for (int k = 0; k < LINES_PER_SLOT; k++) {
                        Cell c = cell(sheet, baseRow + k, col);
                        c.setCellValue(k < lines.size() ? lines.get(k) : "");
                        styler.apply(c, tint, false); // выравнивание — по центру (шаблон)
                    }
                } else {
                    // Пустая ячейка: ограничение сущности — в среднюю строку (как исторический экспорт).
                    String abbr = data.constraintAbbr().get(key);
                    if (abbr != null) {
                        // Ячейка ограничения — светло-серым (все 3 строки блока), аббревиатура — в середину.
                        // У преподавателя ограничения серым НЕ выделяем (требование заказчика).
                        CellStyler.Tint tint = axis == ExportAxis.EDUCATOR ? CellStyler.Tint.NONE : CellStyler.Tint.LIGHT;
                        for (int k = 0; k < LINES_PER_SLOT; k++) {
                            styler.apply(cell(sheet, baseRow + k, col), tint, false); // по центру
                        }
                        cell(sheet, baseRow + 1, col).setCellValue(abbr);
                    }
                }
            }
        }

        hideUnusedWeekColumns(sheet, start, end, axis);
    }

    /**
     * Прячет хвостовые столбцы недель бланка, не попавшие в период (примерно 10 при семестре ~20
     * недель): пустой «расчерченный» блок справа не показывается и не печатается. Именно <b>скрытие</b>,
     * а не физическое удаление: недельные столбцы и таблица «Обозначения» внизу бланка <b>делят одни
     * столбцы</b>, а строку месяца держат объединения по 4 столбца — сдвиг колонок разрушил бы и то,
     * и другое. Для группы столбцы легенды (до {@code X}) не прячем, чтобы не срезать её таблицу.
     */
    private void hideUnusedWeekColumns(Sheet sheet, LocalDate start, LocalDate end, ExportAxis axis) {
        int lastUsedWeekCol = FIRST_WEEK_COL
                + (int) ChronoUnit.WEEKS.between(start.with(DayOfWeek.MONDAY), end.with(DayOfWeek.MONDAY));
        int firstHide = lastUsedWeekCol + 1;
        if (axis == ExportAxis.GROUP) {
            firstHide = Math.max(firstHide, LEGEND_LAST_COL + 1); // легенду группы не режем
        }
        for (int c = firstHide; c <= LAST_TEMPLATE_WEEK_COL; c++) {
            sheet.setColumnHidden(c, true);
        }
    }

    /**
     * Заполняет строку «месяц» бланка. Строка месяца <b>объединена в фиксированные блоки</b>
     * (D6:G6, H6:K6 … AD6:AG6), а запись в НЕ-якорную ячейку объединения POI молча теряет — из-за
     * этого прежде выпадали месяцы, начало которых не попадало на левый угол блока (ноябрь/январь/
     * февраль). Поэтому пишем в ЯКОРЬ каждого блока, а месяц блока определяем по его <b>средней</b>
     * неделе (четверг — середина учебной недели Пн–Сб). Блоки за пределами периода — чистим.
     */
    private void writeMonthHeader(Sheet sheet, LocalDate start, LocalDate end) {
        // Стале-даты примера шаблона во всех недельных столбцах (реальные впишет fillSheet).
        for (int c = FIRST_WEEK_COL; c <= LAST_TEMPLATE_WEEK_COL; c++) {
            for (int d = 0; d < 6; d++) {
                blank(sheet, FIRST_DATE_ROW + d * DAY_STRIDE, c);
            }
        }

        LocalDate startMonday = start.with(DayOfWeek.MONDAY);
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            var region = sheet.getMergedRegion(i);
            if (region.getFirstRow() != MONTH_ROW) continue; // только блоки строки месяца
            int midCol = (region.getFirstColumn() + region.getLastColumn()) / 2;
            int weekIdx = midCol - FIRST_WEEK_COL;
            if (weekIdx < 0) continue;
            LocalDate week = startMonday.plusWeeks(weekIdx);
            Cell anchor = cell(sheet, MONTH_ROW, region.getFirstColumn());
            if (week.isAfter(end)) {
                anchor.setBlank(); // блок за периодом — убрать стале-пример
            } else {
                anchor.setCellValue(week.plusDays(3).getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RU));
            }
        }
    }

    /**
     * Заполняет шапку по оси: год и семестр (в бланке захардкожены «2023/2024 / ВЕСЕННИЙ») —
     * из дат периода; поле сущности (N4:O4) её реквизитами и метку (K4) под ось. Для преподавателя
     * убирает группо-специфичные поля бланка (ФАКУЛЬТЕТ, АУД.САМОСТ.РАБОТЫ).
     */
    private void writeEntityHeader(Sheet sheet, String entityName, LocalDate start, LocalDate end, ExportAxis axis) {
        boolean autumn = start.getMonthValue() >= 8;
        int academicYear = autumn ? start.getYear() : start.getYear() - 1;
        String semester = autumn ? "ОСЕННИЙ" : "ВЕСЕННИЙ";
        setString(sheet, 1, 0, "РАСПИСАНИЕ УЧЕБНЫХ ЗАНЯТИЙ НА " + semester + " СЕМЕСТР"); // A2
        setString(sheet, 2, 0, academicYear + "/" + (academicYear + 1) + " УЧЕБНЫЙ ГОД");   // A3

        // Поле сущности бланка: метка K4 + значение N4 (в бланке «УЧЕБНАЯ ГРУППА», пусто).
        setString(sheet, 3, 13, entityName != null ? entityName : ""); // N4 — реквизиты (для преподавателя ФИО)
        setString(sheet, 3, 10, switch (axis) {                        // K4 — метка под ось
            case EDUCATOR -> "ПРЕПОДАВАТЕЛЬ";
            case AUDITORIUM -> "АУДИТОРИЯ";
            default -> "УЧЕБНАЯ ГРУППА";
        });

        if (axis == ExportAxis.EDUCATOR) {
            blank(sheet, 1, 10);  // K2 «ФАКУЛЬТЕТ»
            blank(sheet, 1, 13);  // поле N2:O2 факультета
            blank(sheet, 2, 18);  // S3 «АУД.САМОСТ.РАБОТЫ»
        }
    }

    /**
     * Полностью убирает таблицу «Обозначения» (строки 83..106) — для препода и аудитории она не
     * нужна. Именно <b>удаляет строки</b>, а не чистит значения: очистка оставляла пустую таблицу
     * с границами. Снимаем объединения зоны (иначе останутся их рамки) и удаляем сами строки
     * (уносит содержимое и стили-границы ячеек). Матрица расписания заканчивается на строке 80,
     * поэтому удаление строк 83+ её не задевает; ниже легенды в бланке ничего нет.
     */
    private void deleteDisciplineLegend(Sheet sheet) {
        List<Integer> mergedToRemove = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            var reg = sheet.getMergedRegion(i);
            if (reg.getFirstRow() >= LEGEND_HEADER_FIRST_ROW && reg.getLastRow() <= LEGEND_LAST_ROW) {
                mergedToRemove.add(i);
            }
        }
        if (!mergedToRemove.isEmpty()) sheet.removeMergedRegions(mergedToRemove);

        for (int r = LEGEND_HEADER_FIRST_ROW; r <= LEGEND_LAST_ROW; r++) {
            Row row = sheet.getRow(r);
            if (row != null) sheet.removeRow(row);
        }
    }

    /**
     * Заполняет таблицу «Обозначения» бланка (только для группы): по строке на дисциплину —
     * аббревиатура (A), название (B:H), лектор(ы) (J:M), преподаватели других видов занятий (N:Q),
     * кол-во часов «лекц-нелекц» (R), отчёт — зачёты/экзамены (S) и лекционный поток (T:U). Шапка
     * (стр. 83–85) сохраняется;
     * строки данных (86–96) очищаются от стале-примера шаблона и переписываются. В бланке 11 строк
     * данных — лишние дисциплины (если их больше) отбрасываются.
     */
    private void writeDisciplineLegend(Sheet sheet, List<LegendRow> legend, CellStyler styler) {
        clearRows(sheet, LEGEND_FIRST_DATA_ROW, LEGEND_LAST_DATA_ROW); // только область данных, шапку не трогаем
        if (legend == null || legend.isEmpty()) return;

        int maxRows = LEGEND_LAST_DATA_ROW - LEGEND_FIRST_DATA_ROW + 1;
        int count = Math.min(legend.size(), maxRows);
        for (int i = 0; i < count; i++) {
            LegendRow lr = legend.get(i);
            int row = LEGEND_FIRST_DATA_ROW + i;
            setLegendCell(sheet, row, LEGEND_COL_ABBR, lr.abbr());
            setLegendCell(sheet, row, LEGEND_COL_DISCIPLINE, lr.discipline());
            setLegendCell(sheet, row, LEGEND_COL_LECTURER, lr.lecturer());
            setLegendCell(sheet, row, LEGEND_COL_OTHERS, lr.others());
            setLegendCell(sheet, row, LEGEND_COL_HOURS, lr.hours());
            setLegendCell(sheet, row, LEGEND_COL_REPORT, lr.report());
            setLegendCell(sheet, row, LEGEND_COL_STREAM, lr.lectureStream());
            // Преподаватели (лектор + другие виды) — по левому краю (требование заказчика).
            styler.apply(cell(sheet, row, LEGEND_COL_LECTURER), CellStyler.Tint.NONE, true);
            styler.apply(cell(sheet, row, LEGEND_COL_OTHERS), CellStyler.Tint.NONE, true);
        }
    }

    /** Пишет строку в ячейку легенды (пустую/`null` не пишет — не затираем стиль пустой ячейкой). */
    private void setLegendCell(Sheet sheet, int row, int col, String value) {
        cell(sheet, row, col).setCellValue(value == null ? "" : value);
    }

    /**
     * Заливка ячейки занятия по виду: контрольная (КР) — светло-серым (как ячейка ограничения);
     * зачёты (ЗО/ЗЧ) и экзамен (ЭКЗ) — темнее. Остальные виды — без заливки.
     */
    private CellStyler.Tint tintForKind(KindOfStudy kind) {
        if (kind == KindOfStudy.QUIZ) return CellStyler.Tint.LIGHT;
        if (kind == KindOfStudy.EXAM
                || kind == KindOfStudy.CREDIT_WITH_GRADE
                || kind == KindOfStudy.CREDIT_WITHOUT_GRADE) return CellStyler.Tint.DARK;
        return CellStyler.Tint.NONE;
    }

    /**
     * Ленивая деривация стилей ячеек: заливка (светло-серая — ограничение/контрольная; тёмная —
     * зачёты/экзамены) и/или выравнивание по левому краю. Мод накладывается поверх исходного стиля
     * ячейки (границы, шрифт, прочее выравнивание сохраняются), результат кэшируется по ключу
     * «исходный стиль + заливка + выравнивание» — так число новых стилей ограничено (разных стилей
     * ячеек единицы), а не растёт на каждую ячейку.
     */
    private static final class CellStyler {
        enum Tint { NONE, LIGHT, DARK }

        private final Workbook wb;
        private final Map<String, CellStyle> cache = new HashMap<>();

        CellStyler(Workbook wb) { this.wb = wb; }

        /** Заливка по виду + (опц.) выравнивание влево. Без модов — стиль не трогаем. */
        void apply(Cell cell, Tint tint, boolean alignLeft) {
            if (tint == Tint.NONE && !alignLeft) return;
            CellStyle src = cell.getCellStyle();
            String key = src.getIndex() + ":" + tint + ":" + alignLeft;
            CellStyle derived = cache.computeIfAbsent(key, k -> {
                CellStyle s = wb.createCellStyle();
                s.cloneStyleFrom(src);
                // Светлый — кастомный очень светлый серый (индексный GREY_25 путался с тёмным);
                // тёмный — заметно контрастнее.
                if (tint == Tint.LIGHT) {
                    // Средний светло-серый (#D9D9D9) — между слишком светлым #F2F2F2 и прежним #C0C0C0.
                    ((XSSFCellStyle) s).setFillForegroundColor(rgb(0xD9, 0xD9, 0xD9));
                    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                } else if (tint == Tint.DARK) {
                    ((XSSFCellStyle) s).setFillForegroundColor(rgb(0xA6, 0xA6, 0xA6));
                    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                if (alignLeft) s.setAlignment(HorizontalAlignment.LEFT);
                return s;
            });
            cell.setCellStyle(derived);
        }

        private static XSSFColor rgb(int r, int g, int b) {
            return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
        }
    }

    /** Стирает значения ячеек в строках [firstRow..lastRow] (0-индекс), сохраняя стиль/границы бланка. */
    private void clearRows(Sheet sheet, int firstRow, int lastRow) {
        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = row.getFirstCellNum(); c >= 0 && c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null) cell.setBlank();
            }
        }
    }

    /** Пишет строку в ячейку с сохранением стиля бланка (ячейка создаётся при отсутствии). */
    private void setString(Sheet sheet, int row, int col, String value) {
        cell(sheet, row, col).setCellValue(value);
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
