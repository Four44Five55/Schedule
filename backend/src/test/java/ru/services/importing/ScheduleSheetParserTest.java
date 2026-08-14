package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты разбора полотна чужой выгрузки.
 *
 * <p>Фикстуры собраны по живым файлам (2025/2026, осенний семестр), но <b>синтетические</b>: тесты
 * обязаны идти на чистой машине, а каталог с образцами лежит в {@code .gitignore}. Проверка на
 * настоящих файлах — отдельным характеризующим тестом в {@code ScheduleSheetParserSamplesTest},
 * который сам себя пропускает, если образцов нет.</p>
 *
 * <p>Главное, что здесь зафиксировано: <b>дата берётся из числа месяца и дня недели</b>, а не из
 * номера недельной колонки и не из строки «Месяц». Оба «очевидных» пути в живых файлах врут, и
 * тесты на вырезанные недели это стерегут.</p>
 */
class ScheduleSheetParserTest {

    // =======================================================================
    // Сборка фикстур
    // =======================================================================

    private static String cell(String... lines) {
        StringBuilder sb = new StringBuilder("<td><table>");
        for (String line : lines) {
            sb.append("<tr><td><p><font size=1>").append(line).append("</font></p></td></tr>");
        }
        return sb.append("</table></td>").toString();
    }

    private static String datesRow(int... daysOfMonth) {
        StringBuilder sb = new StringBuilder("<tr><td></td><td></td><td><strong>Даты</strong></td>");
        for (int d : daysOfMonth) {
            sb.append("<td><p>").append(d).append("</p></td>");
        }
        return sb.append("</tr>").toString();
    }

    private static String dayRow(String day, String slot, String time, String... cells) {
        return "<tr><td rowspan=4>" + day + "</td><td>" + slot + "</td><td>" + time + "</td>"
                + String.join("", cells) + "</tr>";
    }

    private static String slotRow(String slot, String time, String... cells) {
        return "<tr><td>" + slot + "</td><td>" + time + "</td>" + String.join("", cells) + "</tr>";
    }

    private static String sheet(String headerCell, String body) {
        return """
                <!DOCTYPE html><html><head><meta charset='utf-8'></head><body>
                <table><tr><td>%s</td><td>Факультет 9Ф</td></tr>
                       <tr><td>2025/2026 учебный год</td><td>Кафедра: 91 кафедра</td></tr></table>
                <table border=1>
                  <tr><td>День недели</td><td></td><td>Уч. недели</td><td>1</td><td>2</td><td>3</td></tr>
                  <tr><td></td><td></td><td>Месяц</td><td colspan=3>Сентябрь</td></tr>
                  %s
                </table></body></html>
                """.formatted(headerCell, body);
    }

    private static String groupSheet(String body) {
        return sheet("Расписание учебных занятий на осенний семестр | Учебная группа 911", body);
    }

    private static Optional<SheetCell> cellAt(ParsedSheet sheet, DayOfWeek day, TimeSlotPair slot, int column) {
        return sheet.cells().stream()
                .filter(c -> c.day() == day && c.slot() == slot && c.weekColumn() == column)
                .findFirst();
    }

    // =======================================================================

    @Nested
    @DisplayName("Шапка")
    class Header {

        @Test
        @DisplayName("Групповой разрез: номер группы, факультет, учебный год")
        void groupHeader() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1, 8, 15) + dayRow("Пн", "1-2", "9.00-10.35", cell("Л/Т.4", "УПМВ", "416-3"))));

            assertThat(sheet.header().kind()).isEqualTo(CutKind.GROUP);
            assertThat(sheet.header().owner()).isEqualTo("911");
            assertThat(sheet.header().faculty()).isEqualTo("9Ф");
            assertThat(sheet.header().startYear()).isEqualTo(2025);
            assertThat(sheet.header().semester()).isEqualTo("осенний");
            assertThat(sheet.header().department()).isEqualTo("91 кафедра");
        }

        @Test
        @DisplayName("Преподавательский разрез: звание и степень остаются частью подписи")
        void educatorHeader() {
            ParsedSheet sheet = ScheduleSheetParser.parse(sheet(
                    "Преподаватель: к-н Горяинов Р.И. ктн Семестр: осенний",
                    datesRow(1) + dayRow("Пн", "1-2", "9.00-10.35", cell("252-3", "911", "АСКС"))));

            assertThat(sheet.header().kind()).isEqualTo(CutKind.EDUCATOR);
            assertThat(sheet.header().owner()).isEqualTo("к-н Горяинов Р.И. ктн");
        }

        @Test
        @DisplayName("Аудиторный разрез: номер комнаты вместе с суффиксом")
        void auditoriumHeader() {
            ParsedSheet sheet = ScheduleSheetParser.parse(sheet(
                    "Загрузка учебной аудитории 252-3 на осенний семестр",
                    datesRow(1) + dayRow("Пн", "1-2", "9.00-10.35", cell("П", "911", "НИР"))));

            assertThat(sheet.header().kind()).isEqualTo(CutKind.AUDITORIUM);
            assertThat(sheet.header().owner()).isEqualTo("252-3");
        }

        @Test
        @DisplayName("Неопознанная шапка — замечание, а не исключение")
        void unknownHeaderIsReported() {
            ParsedSheet sheet = ScheduleSheetParser.parse(sheet("Нечто постороннее", datesRow(1)));

            assertThat(sheet.header().kind()).isEqualTo(CutKind.UNKNOWN);
            assertThat(sheet.problems()).anyMatch(p -> p.contains("шапка не опознана"));
        }
    }

    @Nested
    @DisplayName("Даты")
    class Dates {

        @Test
        @DisplayName("Дата собирается из числа месяца и дня недели")
        void dateFromDayOfMonthAndWeekday() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1, 8, 15)
                            + dayRow("Пн", "1-2", "9.00-10.35",
                            cell("Л/Т.4", "УПМВ", "416-3"), cell("П/Т1", "НИР", "252-3"), cell("Л/Т.3", "АСКС", "252-3"))));

            assertThat(cellAt(sheet, DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0)).get()
                    .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 9, 1));
            assertThat(cellAt(sheet, DayOfWeek.MONDAY, TimeSlotPair.FIRST, 2)).get()
                    .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 9, 15));
        }

        @Test
        @DisplayName("У каждого дня своя строка «Даты»: вторник — это 2 сентября, а не 1")
        void everyDayHasItsOwnDatesRow() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1) + dayRow("Пн", "1-2", "9.00-10.35", cell("Л/Т.4", "УПМВ", "416-3"))
                            + datesRow(2) + dayRow("Вт", "1-2", "9.00-10.35", cell("Л/Т.3", "АСКС", "252-3"))));

            assertThat(cellAt(sheet, DayOfWeek.TUESDAY, TimeSlotPair.FIRST, 0)).get()
                    .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 9, 2));
        }

        @Test
        @DisplayName("Вырезанная неделя не сдвигает даты: колонка «1» может быть 8 сентября")
        void cutOutWeeksDoNotShiftDates() {
            // Так выглядит преподавательский файл: первая учебная неделя пустая и выброшена,
            // а нумерация колонок осталась сплошной.
            ParsedSheet sheet = ScheduleSheetParser.parse(sheet(
                    "Преподаватель: к-н Горяинов Р.И. ктн Семестр: осенний",
                    datesRow(8, 15) + dayRow("Пн", "1-2", "9.00-10.35",
                            cell("252-3", "911", "АСКС"), cell("252-3", "911", "АСКС"))));

            assertThat(cellAt(sheet, DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0)).get()
                    .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 9, 8));
        }

        @Test
        @DisplayName("Дыра в середине тоже не сдвигает: 1 → 22 сентября")
        void gapInTheMiddleIsTolerated() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1, 22) + dayRow("Пн", "1-2", "9.00-10.35",
                            cell("Л/Т.4", "УПМВ", "416-3"), cell("Л/Т.5", "УПМВ", "416-3"))));

            assertThat(cellAt(sheet, DayOfWeek.MONDAY, TimeSlotPair.FIRST, 1)).get()
                    .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 9, 22));
        }

        @Test
        @DisplayName("Переход через месяц и через год — без строки «Месяц»")
        void monthAndYearRollover() {
            assertThat(ScheduleSheetParser.nextDate(LocalDate.of(2025, 9, 29), DayOfWeek.MONDAY, 6))
                    .isEqualTo(LocalDate.of(2025, 10, 6));
            assertThat(ScheduleSheetParser.nextDate(LocalDate.of(2025, 12, 30), DayOfWeek.MONDAY, 5))
                    .isEqualTo(LocalDate.of(2026, 1, 5));
        }

        @Test
        @DisplayName("Дыра в несколько недель подряд (каникулы, отпуск) ещё разбирается")
        void longButLegitimateGapIsResolved() {
            // 1 сентября → 13 октября: шесть недель без занятий.
            assertThat(ScheduleSheetParser.nextDate(LocalDate.of(2025, 9, 2), DayOfWeek.MONDAY, 13))
                    .isEqualTo(LocalDate.of(2025, 10, 13));
        }

        @Test
        @DisplayName("Разрыв больше окна поиска не угадывается: дата остаётся пустой, колонка — в замечаниях")
        void gapBeyondSearchWindowIsNotGuessed() {
            // Ближайший понедельник первого числа после 2 сентября — только 1 декабря, это
            // тринадцатая неделя. Молча прыгнуть туда опаснее, чем признать, что дата не выведена.
            assertThat(ScheduleSheetParser.nextDate(LocalDate.of(2025, 9, 2), DayOfWeek.MONDAY, 1)).isNull();
        }
    }

    @Nested
    @DisplayName("Ячейки")
    class Cells {

        @Test
        @DisplayName("Три строки ячейки отдаются как есть, без интерпретации")
        void linesAreReturnedRaw() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1) + dayRow("Пн", "1-2", "9.00-10.35", cell("Л/Т.4", "УПМВ", "416-3"))));

            assertThat(sheet.cells()).singleElement()
                    .extracting(SheetCell::lines, org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("Л/Т.4", "УПМВ", "416-3");
        }

        @Test
        @DisplayName("Одна строка — это маркер занятости («ЭкзС»), а не занятие")
        void singleLineCellIsMarker() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1, 8) + dayRow("Пн", "1-2", "9.00-10.35",
                            cell("Л/Т.4", "УПМВ", "416-3"), cell("ЭкзС"))));

            assertThat(cellAt(sheet, DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0)).get()
                    .matches(c -> !c.isMarker());
            assertThat(cellAt(sheet, DayOfWeek.MONDAY, TimeSlotPair.FIRST, 1)).get()
                    .matches(SheetCell::isMarker);
        }

        @Test
        @DisplayName("Пустые ячейки не выдаются вовсе")
        void emptyCellsAreSkipped() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1, 8) + dayRow("Пн", "1-2", "9.00-10.35",
                            cell("Л/Т.4", "УПМВ", "416-3"), "<td></td>")));

            assertThat(sheet.cells()).hasSize(1);
        }

        @Test
        @DisplayName("Строки без ячейки дня продолжают тот же день: пары 3-4, 5-6, 7-8")
        void slotRowsContinueTheSameDay() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1)
                            + dayRow("Пн", "1-2", "9.00-10.35", cell("Л/Т.4", "УПМВ", "416-3"))
                            + slotRow("3-4", "10.55-12.30", cell("Л/Т.3", "АСКС", "252-3"))
                            + slotRow("7-8", "16.20-17.55", cell("П/Т.5", "УПМВ", "252-3"))));

            assertThat(sheet.cells()).extracting(SheetCell::day).containsOnly(DayOfWeek.MONDAY);
            assertThat(sheet.cells()).extracting(SheetCell::slot)
                    .containsExactly(TimeSlotPair.FIRST, TimeSlotPair.SECOND, TimeSlotPair.FOURTH);
        }

        @Test
        @DisplayName("Заливка ячейки сохраняется — ею в файле помечены недели маркеров")
        void shadingIsKept() {
            String shaded = "<td style='background-color:#cccccc'><table><tr><td>ЭкзС</td></tr></table></td>";
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1) + dayRow("Пн", "1-2", "9.00-10.35", shaded)));

            assertThat(sheet.cells()).singleElement().matches(SheetCell::shaded);
        }
    }

    @Nested
    @DisplayName("Тотальность")
    class Totality {

        @Test
        @DisplayName("null, пустая строка и мусор дают ответ с замечанием, а не исключение")
        void parserIsTotal() {
            assertThat(ScheduleSheetParser.parse((String) null).problems()).isNotEmpty();
            assertThat(ScheduleSheetParser.parse("").problems()).isNotEmpty();
            assertThat(ScheduleSheetParser.parse((byte[]) null).problems()).isNotEmpty();

            ParsedSheet junk = ScheduleSheetParser.parse("<html><body>ничего похожего</body></html>");
            assertThat(junk.cells()).isEmpty();
            assertThat(junk.problems()).isNotEmpty();
        }

        @Test
        @DisplayName("Обрезанный по байтам символ не роняет разбор")
        void brokenUtf8IsTolerated() {
            // В живом файле «СР» записано тремя байтами вместо четырёх — строгий декодер падает.
            byte[] valid = groupSheet(datesRow(1)
                    + dayRow("Пн", "1-2", "9.00-10.35", cell("МАРКЕР"))).getBytes(StandardCharsets.UTF_8);
            byte[] broken = new byte[valid.length + 1];
            System.arraycopy(valid, 0, broken, 0, valid.length);
            broken[valid.length] = (byte) 0xD0; // начало символа без продолжения

            ParsedSheet sheet = ScheduleSheetParser.parse(broken);

            assertThat(sheet.header().kind()).isEqualTo(CutKind.GROUP);
            assertThat(sheet.cells()).hasSize(1);
        }

        @Test
        @DisplayName("Неизвестная пара пропускается с замечанием, остальные строки разбираются")
        void unknownSlotIsReported() {
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1)
                            + dayRow("Пн", "9-0", "19.00-20.35", cell("Л/Т.4", "УПМВ", "416-3"))
                            + slotRow("3-4", "10.55-12.30", cell("Л/Т.3", "АСКС", "252-3"))));

            assertThat(sheet.problems()).anyMatch(p -> p.contains("неизвестная пара"));
            assertThat(sheet.cells()).singleElement()
                    .extracting(SheetCell::slot).isEqualTo(TimeSlotPair.SECOND);
        }

        @Test
        @DisplayName("Замечание о дате называет причину: число, окно поиска и рассинхрон колонок")
        void dateFailureExplainsItself() {
            // Чисел в строке «Даты» одно, а недельных колонок две — вторая колонка остаётся без
            // числа. Прежний текст назвал бы только место, и проверить его глазами было нельзя:
            // в файле числа на месте, а какое из них не подошло — не видно.
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(1)
                            + dayRow("Пн", "1-2", "9.00-10.35",
                            cell("Л/Т.4", "УПМВ", "416-3") + cell("Л/Т.5", "АСКС", "252-3"))));

            assertThat(sheet.problems())
                    .anyMatch(p -> p.contains("в строке «Даты» для этой колонки числа нет"))
                    .anyMatch(p -> p.contains("геометрия") && p.contains("не на свои колонки"));
        }

        @Test
        @DisplayName("Занятия начинаются в конце ноября: первая же колонка дальше квартала от якоря")
        void firstColumnMayBeFarFromAnchor() {
            // Живой случай (2026-08-15): у преподавателя вырезаны пустые недели, и первая колонка
            // файла — 25 ноября, тринадцатый вторник от 1 сентября. Прежнее окно в 12 недель
            // промахивалось мимо него ровно на неделю.
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(25) + dayRow("Вт", "1-2", "9.00-10.35", cell("Л/Т.1", "УПМВ", "416-3"))));

            assertThat(sheet.problems()).noneMatch(p -> p.contains("не удалось определить дату"));
            assertThat(sheet.cells()).singleElement()
                    .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 11, 25));
        }

        @Test
        @DisplayName("Осенний семестр уходит в январь: разрыв в пятнадцать недель законен")
        void semesterCrossesIntoJanuary() {
            // Второй живой случай: 30 сентября → 13 января. Год меняется сам (поиск шагает
            // неделями по календарю), а вот прежнее окно до января не дотягивалось.
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(30, 13) + dayRow("Вт", "1-2", "9.00-10.35",
                            cell("Л/Т.1", "УПМВ", "416-3") + cell("ЗО", "ФЛ", "341"))));

            assertThat(sheet.cells()).extracting(SheetCell::date)
                    .containsExactly(LocalDate.of(2025, 9, 30), LocalDate.of(2026, 1, 13));
        }

        @Test
        @DisplayName("Число, которому не нашлось дня недели, показывается вместе с окном поиска")
        void impossibleDayOfMonthIsExplained() {
            // 31 сентября не бывает: понедельника с таким числом в окне не найдётся.
            ParsedSheet sheet = ScheduleSheetParser.parse(groupSheet(
                    datesRow(31) + dayRow("Пн", "1-2", "9.00-10.35", cell("Л/Т.4", "УПМВ", "416-3"))));

            assertThat(sheet.problems())
                    .anyMatch(p -> p.contains("напечатано число 31") && p.contains("недель от "));
        }
    }

    @Nested
    @DisplayName("Разрезы отличаются только смыслом строк, но не геометрией")
    class Cuts {

        @Test
        @DisplayName("Один и тот же разбор годится для группы, преподавателя и аудитории")
        void geometryIsSharedAcrossCuts() {
            List<String> owners = List.of(
                    "Расписание учебных занятий на осенний семестр | Учебная группа 911",
                    "Преподаватель: к-н Горяинов Р.И. ктн Семестр: осенний",
                    "Загрузка учебной аудитории 252-3 на осенний семестр");

            for (String owner : owners) {
                ParsedSheet sheet = ScheduleSheetParser.parse(sheet(owner,
                        datesRow(1) + dayRow("Пн", "1-2", "9.00-10.35", cell("а", "б", "в"))));

                assertThat(sheet.cells()).singleElement()
                        .extracting(SheetCell::date).isEqualTo(LocalDate.of(2025, 9, 1));
            }
        }
    }
}
