package ru.services.exporting;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Расшифровка аббревиатур в бланке: проверяется <b>раскладка</b>, а не содержание.
 *
 * <p>Повод — находка заказчика: условные обозначения приезжали в файл «с переносом текста» и
 * читались только после ручной правки форматирования. Причина не в данных: стиль блока
 * <b>клонируется с ячейки бланка</b> ({@code cloneStyleFrom}), а у неё стоит перенос по словам —
 * ячейки таблицы «Обозначения» размечены под свои узкие колонки. Название вида занятия пишется в
 * столбец {@code B} шириной 3.9 знака, поэтому с переносом оно складывается в вертикальный столбик.</p>
 *
 * <p>Блок намеренно <b>не объединяется</b>: текст должен перетекать вправо по пустым ячейкам. Значит
 * у этой раскладки два условия, и оба проверяются здесь — <b>нет переноса</b> (и «ужать текст») и
 * <b>справа пусто</b>. Первое молча ломается правкой бланка, второе — правкой кода, который начнёт
 * что-нибудь писать в соседние столбцы.</p>
 *
 * <p>Тест характеризующий: он идёт против настоящего {@code templates/template.xlsx} и потому
 * заодно сторожит геометрию блока — заголовки ищутся по тексту, а не по номеру строки, чтобы
 * падение указывало на реальную причину («заголовка нет»), а не на съехавший номер.</p>
 */
class ScheduleWorkbookRendererTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    private static final String KIND_TITLE = "Обозначения видов занятий:";
    private static final String OTHER_TITLE = "Другие обозначения:";

    private final ScheduleWorkbookRenderer renderer = new ScheduleWorkbookRenderer();

    @Test
    @DisplayName("Расшифровка пишется без переноса — иначе название в узком столбце нечитаемо")
    void abbreviationMarksAreWrittenWithoutWrap() throws IOException {
        Sheet sheet = renderGroupSheet(
                List.of(new ScheduleWorkbookRenderer.Mark("ПЗ", "Практическое занятие")),
                List.of(new ScheduleWorkbookRenderer.Mark("Ком", "Командировка")));

        Cell kindTitle = findCell(sheet, KIND_TITLE);
        Cell otherTitle = findCell(sheet, OTHER_TITLE);
        assertThat(kindTitle).as("заголовок расшифровки видов занятий").isNotNull();
        assertThat(otherTitle).as("заголовок прочих обозначений").isNotNull();

        // Строка списка — сразу под заголовком, в тех же столбцах.
        Cell kindAbbr = cellAt(sheet, kindTitle.getRowIndex() + 1, kindTitle.getColumnIndex());
        Cell kindName = cellAt(sheet, kindTitle.getRowIndex() + 1, kindTitle.getColumnIndex() + 1);
        Cell otherAbbr = cellAt(sheet, otherTitle.getRowIndex() + 1, otherTitle.getColumnIndex());
        Cell otherName = cellAt(sheet, otherTitle.getRowIndex() + 1, otherTitle.getColumnIndex() + 1);

        assertThat(kindAbbr.getStringCellValue()).isEqualTo("ПЗ");
        assertThat(kindName.getStringCellValue()).isEqualTo("Практическое занятие");
        assertThat(otherAbbr.getStringCellValue()).isEqualTo("Ком");
        assertThat(otherName.getStringCellValue()).isEqualTo("Командировка");

        for (Cell cell : List.of(kindTitle, otherTitle, kindAbbr, kindName, otherAbbr, otherName)) {
            assertThat(cell.getCellStyle().getWrapText())
                    .as("перенос по словам в блоке расшифровки (%s)", cell.getAddress())
                    .isFalse();
            assertThat(cell.getCellStyle().getShrinkToFit())
                    .as("«ужать текст» в блоке расшифровки (%s)", cell.getAddress())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Справа от расшифровки пусто — только так текст перетекает вместо обрезки")
    void nothingIsWrittenRightOfTheMarkName() throws IOException {
        Sheet sheet = renderGroupSheet(
                List.of(new ScheduleWorkbookRenderer.Mark("ИКС",
                        "Индивидуальное контрольное собеседование")),
                List.of());

        Cell kindTitle = findCell(sheet, KIND_TITLE);
        assertThat(kindTitle).isNotNull();
        int nameCol = kindTitle.getColumnIndex() + 1;
        Row row = sheet.getRow(kindTitle.getRowIndex() + 1);

        // Достаточно ближайшего соседа: первая же непустая ячейка обрезает перетекание.
        Cell neighbour = row.getCell(nameCol + 1);
        assertThat(neighbour == null || neighbour.getCellType() == CellType.BLANK)
                .as("ячейка справа от названия должна остаться пустой")
                .isTrue();
    }

    @Test
    @DisplayName("Подпись встаёт справа внизу и не налезает на расшифровку")
    void signatureIsWrittenAtTheRightEdge() throws IOException {
        Sheet sheet = renderGroupSheet(
                List.of(new ScheduleWorkbookRenderer.Mark("Л", "Лекция")),
                List.of(),
                new ScheduleWorkbookRenderer.Signature(
                        "Начальник учебного отдела", "полковник", "Иванов И.И."));

        Cell position = findCell(sheet, "Начальник учебного отдела");
        Cell name = findCell(sheet, "Иванов И.И.");
        assertThat(position).as("строка должности").isNotNull();
        assertThat(name).as("фамилия подписанта").isNotNull();

        // Блок стоит правее расшифровки — иначе два текста рисуются друг по другу.
        assertThat(position.getColumnIndex()).isGreaterThan(findCell(sheet, KIND_TITLE).getColumnIndex());
        // Фамилия — у самого правого края листа, должность — от левого края блока.
        assertThat(name.getRowIndex()).isEqualTo(position.getRowIndex() + 1);
        assertThat(name.getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.RIGHT);
        assertThat(position.getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.LEFT);

        // Объединение — граница, о которую обрезается перетекание расшифровки.
        assertThat(sheet.getMergedRegions())
                .as("строка должности объединена до правого края")
                .anyMatch(r -> r.getFirstRow() == position.getRowIndex()
                        && r.getFirstColumn() == position.getColumnIndex());
    }

    @Test
    @DisplayName("Между регалиями и фамилией остаётся пустое место — там расписываются от руки")
    void signatureLeavesRoomForTheHandwrittenPart() throws IOException {
        Sheet sheet = renderGroupSheet(List.of(), List.of(),
                new ScheduleWorkbookRenderer.Signature(
                        "Начальник учебного отдела", "полковник", "Иванов И.И."));

        Cell credentials = findCell(sheet, "полковник");
        Cell name = findCell(sheet, "Иванов И.И.");
        assertThat(credentials).as("регалии отдельной ячейкой, а не в одной строке с фамилией").isNotNull();
        assertThat(name).isNotNull();
        assertThat(credentials.getRowIndex()).as("обе части — одна строка").isEqualTo(name.getRowIndex());

        // Промежуток — пустые столбцы, а не пробелы: ширину пробелов бланк не удержал бы.
        int credentialsEnd = sheet.getMergedRegions().stream()
                .filter(r -> r.getFirstRow() == credentials.getRowIndex()
                        && r.getFirstColumn() == credentials.getColumnIndex())
                .mapToInt(CellRangeAddress::getLastColumn)
                .max().orElse(credentials.getColumnIndex());
        assertThat(name.getColumnIndex() - credentialsEnd - 1)
                .as("пустых столбцов между регалиями и фамилией")
                .isGreaterThanOrEqualTo(3);

        // И ни одна из частей не залезает на другую — пересечение объединений уронило бы выгрузку.
        assertThat(credentialsEnd).isLessThan(name.getColumnIndex());
    }

    @Test
    @DisplayName("Подписи нет — блок не рисуется, лишних строк в бланке не появляется")
    void emptySignatureDrawsNothing() throws IOException {
        Sheet sheet = renderGroupSheet(List.of(new ScheduleWorkbookRenderer.Mark("Л", "Лекция")),
                List.of(), ScheduleWorkbookRenderer.Signature.EMPTY);

        Cell kindTitle = findCell(sheet, KIND_TITLE);
        Row titleRow = sheet.getRow(kindTitle.getRowIndex());
        assertThat(titleRow.getLastCellNum())
                .as("в строке заголовка расшифровки не должно быть ячеек блока подписи")
                .isEqualTo((short) (kindTitle.getColumnIndex() + 1));
    }

    @Test
    @DisplayName("Факультет и курс встают напротив своих слов в шапке бланка группы")
    void facultyAndCourseAreWrittenNextToTheirLabels() throws IOException {
        Sheet sheet = renderSheet(sheetData("911", "9Ф", 5), ExportAxis.GROUP);

        Cell facultyLabel = findCell(sheet, "ФАКУЛЬТЕТ");
        Cell courseLabel = findCell(sheet, "КУРС");
        assertThat(facultyLabel).as("графа «ФАКУЛЬТЕТ» бланка").isNotNull();
        assertThat(courseLabel).as("графа «КУРС» бланка").isNotNull();

        assertThat(textsInRow(sheet, facultyLabel.getRowIndex()))
                .as("значение стоит в той же строке, что и слово")
                .contains("9Ф");
        assertThat(textsInRow(sheet, courseLabel.getRowIndex())).contains("5");
    }

    @Test
    @DisplayName("Курса нет — графа остаётся пустой, а не получает ноль")
    void unknownCourseLeavesGraphEmpty() throws IOException {
        Sheet sheet = renderSheet(sheetData("1152", null, null), ExportAxis.GROUP);

        Cell courseLabel = findCell(sheet, "КУРС");
        assertThat(textsInRow(sheet, courseLabel.getRowIndex()))
                .as("в строке курса не появляется ни нуля, ни иного числа — только подписи бланка")
                .noneMatch(text -> text.matches("\\d+"));
    }

    @Test
    @DisplayName("В бланке преподавателя групповых граф нет вовсе — ни слова, ни значения")
    void educatorBlankHasNoGroupGraphs() throws IOException {
        Sheet sheet = renderSheet(sheetData("Иванов И.И.", "9Ф", 5), ExportAxis.EDUCATOR);

        assertThat(findCell(sheet, "ФАКУЛЬТЕТ")).as("факультета у преподавателя нет").isNull();
        assertThat(findCell(sheet, "КУРС")).as("курс — свойство группы, а не преподавателя").isNull();
        assertThat(findCell(sheet, "9Ф")).isNull();
    }

    /** Лист одной сущности с реквизитами шапки и без занятий. */
    private ScheduleWorkbookRenderer.SheetData sheetData(String name, String faculty, Integer course) {
        return new ScheduleWorkbookRenderer.SheetData(
                name, faculty, course, Map.of(), Map.of(), List.of(), List.of(), List.of());
    }

    private Sheet renderSheet(ScheduleWorkbookRenderer.SheetData data, ExportAxis axis) throws IOException {
        byte[] bytes = renderer.render(List.of(data), START, END, null, axis,
                ScheduleWorkbookRenderer.Signature.EMPTY);
        return new XSSFWorkbook(new ByteArrayInputStream(bytes)).getSheetAt(0);
    }

    /** Непустые тексты строки — «что стоит напротив этого слова». */
    private List<String> textsInRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        assertThat(row).as("строка %s шапки", rowIndex).isNotNull();
        List<String> texts = new ArrayList<>();
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING && !cell.getStringCellValue().isBlank()) {
                texts.add(cell.getStringCellValue());
            }
        }
        return texts;
    }

    /** Лист группы с расшифровками, но без занятий: проверяем раскладку блока, а не сетку. */
    private Sheet renderGroupSheet(List<ScheduleWorkbookRenderer.Mark> kindMarks,
                                   List<ScheduleWorkbookRenderer.Mark> otherMarks) throws IOException {
        return renderGroupSheet(kindMarks, otherMarks, ScheduleWorkbookRenderer.Signature.EMPTY);
    }

    private Sheet renderGroupSheet(List<ScheduleWorkbookRenderer.Mark> kindMarks,
                                   List<ScheduleWorkbookRenderer.Mark> otherMarks,
                                   ScheduleWorkbookRenderer.Signature signature) throws IOException {
        ScheduleWorkbookRenderer.SheetData data = new ScheduleWorkbookRenderer.SheetData(
                "911", null, null, Map.of(), Map.of(), List.of(), kindMarks, otherMarks);
        byte[] bytes = renderer.render(List.of(data), START, END, null, ExportAxis.GROUP, signature);
        return new XSSFWorkbook(new ByteArrayInputStream(bytes)).getSheetAt(0);
    }

    /** Первая ячейка листа с таким текстом; {@code null} — такой на листе нет. */
    private Cell findCell(Sheet sheet, String text) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && text.equals(cell.getStringCellValue())) {
                    return cell;
                }
            }
        }
        return null;
    }

    private Cell cellAt(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        assertThat(r).as("строка %s блока расшифровки", row).isNotNull();
        Cell c = r.getCell(col);
        assertThat(c).as("ячейка (%s, %s) блока расшифровки", row, col).isNotNull();
        return c;
    }
}
