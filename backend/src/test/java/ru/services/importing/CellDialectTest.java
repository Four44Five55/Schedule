package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;
import ru.services.importing.CellDialect.LessonEntry;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetCell;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты интерпретации ячейки по разрезу.
 *
 * <p>Все примеры — из живых файлов. Главное, что здесь зафиксировано: <b>недостающее не
 * выдумывается</b>. В преподавательской ячейке нет вида занятия, в аудиторной — темы, и поля
 * остаются пустыми, а не заполняются «самым вероятным» значением.</p>
 */
class CellDialectTest {

    private static SheetCell cell(String... lines) {
        return new SheetCell(DayOfWeek.MONDAY, TimeSlotPair.FIRST, 0,
                LocalDate.of(2025, 9, 1), List.of(lines), false);
    }

    private static SheetHeader header(CutKind kind, String owner) {
        return new SheetHeader(kind, owner, "9Ф", "91 кафедра", 2025, "осенний");
    }

    @Test
    @DisplayName("Группа: «Л/Т.4 · УПМВ · 416-3» — вид, тема, дисциплина, аудитория; группа из шапки")
    void groupCell() {
        LessonEntry entry = CellDialect.GROUP.read(
                cell("Л/Т.4", "УПМВ", "416-3"), header(CutKind.GROUP, "911"));

        assertThat(entry.kind()).isEqualTo("Л");
        assertThat(entry.theme()).isEqualTo("Т.4");
        assertThat(entry.discipline()).isEqualTo("УПМВ");
        assertThat(entry.rooms()).containsExactly("416-3");
        assertThat(entry.groups()).containsExactly("911");
        assertThat(entry.educator()).isNull();
    }

    @Test
    @DisplayName("Аудитория: «П · 911 · НИР» — вид, группа, дисциплина; комната из шапки, темы нет")
    void auditoriumCell() {
        LessonEntry entry = CellDialect.AUDITORIUM.read(
                cell("П", "911", "НИР"), header(CutKind.AUDITORIUM, "252-3"));

        assertThat(entry.kind()).isEqualTo("П");
        assertThat(entry.groups()).containsExactly("911");
        assertThat(entry.discipline()).isEqualTo("НИР");
        assertThat(entry.rooms()).containsExactly("252-3");
        assertThat(entry.theme()).isNull();
    }

    @Test
    @DisplayName("Преподаватель: «252-3 · 911 · АСКС» — вида занятия в ячейке НЕТ")
    void educatorCell() {
        LessonEntry entry = CellDialect.EDUCATOR.read(
                cell("252-3", "911", "АСКС"), header(CutKind.EDUCATOR, "к-н Ветров Р.И. ктн"));

        assertThat(entry.rooms()).containsExactly("252-3");
        assertThat(entry.groups()).containsExactly("911");
        assertThat(entry.discipline()).isEqualTo("АСКС");
        assertThat(entry.educator()).isEqualTo("к-н Ветров Р.И. ктн");
        assertThat(entry.kind()).isNull();
        assertThat(entry.theme()).isNull();
    }

    @Test
    @DisplayName("Полное название дисциплины вместо аббревиатуры не ломает разбор")
    void fullDisciplineName() {
        LessonEntry entry = CellDialect.GROUP.read(
                cell("П/Т.10", "Математическое обеспечение АССН", "252-3"), header(CutKind.GROUP, "911"));

        assertThat(entry.discipline()).isEqualTo("Математическое обеспечение АССН");
        assertThat(entry.theme()).isEqualTo("Т.10");
    }

    @Test
    @DisplayName("Вид без темы: «П/П» у физподготовки и вид без разделителя")
    void kindWithoutTheme() {
        assertThat(CellDialect.GROUP.read(cell("П/П", "ФП", "Сп. зал"), header(CutKind.GROUP, "911")).theme())
                .isEqualTo("П");
        assertThat(CellDialect.GROUP.read(cell("КП", "АСКС", "252-3"), header(CutKind.GROUP, "911")).theme())
                .isNull();
    }

    @Test
    @DisplayName("Короткая ячейка не роняет разбор — недостающих строк просто нет")
    void shortCellIsTolerated() {
        LessonEntry entry = CellDialect.GROUP.read(cell("Л/Т.4", "УПМВ"), header(CutKind.GROUP, "911"));

        assertThat(entry.discipline()).isEqualTo("УПМВ");
        assertThat(entry.rooms()).isEmpty();
    }

    @Test
    @DisplayName("Поток в преподавательской ячейке: три группы между аудиторией и дисциплиной")
    void educatorCellWithStream() {
        // Живая ячейка (2026-08-15). Прежний разбор брал строку 2 как дисциплину и получал
        // «1155-2»: в сверку уезжал мусор, а поток выглядел одногрупповым.
        LessonEntry entry = CellDialect.EDUCATOR.read(
                cell("338-7", "1155-1", "1155-2", "1155-3", "ОВО"),
                header(CutKind.EDUCATOR, "п-к Иванов Т.В. дин"));

        assertThat(entry.rooms()).containsExactly("338-7");
        assertThat(entry.groups()).containsExactly("1155-1", "1155-2", "1155-3");
        assertThat(entry.discipline()).isEqualTo("ОВО");
    }

    @Test
    @DisplayName("Поток в аудиторной ячейке: вид, группы, дисциплина последней")
    void auditoriumCellWithStream() {
        LessonEntry entry = CellDialect.AUDITORIUM.read(
                cell("Л", "911", "912", "АСКС"), header(CutKind.AUDITORIUM, "252-3"));

        assertThat(entry.kind()).isEqualTo("Л");
        assertThat(entry.groups()).containsExactly("911", "912");
        assertThat(entry.discipline()).isEqualTo("АСКС");
    }

    @Test
    @DisplayName("Несколько аудиторий у группового занятия: делится по кабинетам")
    void groupCellWithSeveralRooms() {
        LessonEntry entry = CellDialect.GROUP.read(
                cell("ЛР/Т.4", "АСКС", "252-3", "253-3"), header(CutKind.GROUP, "911"));

        assertThat(entry.discipline()).isEqualTo("АСКС");
        assertThat(entry.rooms()).containsExactly("252-3", "253-3");
    }

    @Test
    @DisplayName("Две строки в аудиторной ячейке: групп нет, дисциплина последняя")
    void auditoriumCellWithoutGroup() {
        LessonEntry entry = CellDialect.AUDITORIUM.read(cell("Л", "АСКС"), header(CutKind.AUDITORIUM, "252-3"));

        assertThat(entry.kind()).isEqualTo("Л");
        assertThat(entry.groups()).isEmpty();
        assertThat(entry.discipline()).isEqualTo("АСКС");
    }

    @Test
    @DisplayName("Объединённые группы в одной строке: «10073/19, 10073/22» — это две группы")
    void jointGroupsInOneLine() {
        // Живой случай (2026-08-15). Без разбиения строка доезжала до справочника целиком:
        // декодер отвечал «больше одного „/“ в номере», сверка — «в базе нет», а заведение
        // создало бы третью группу с запятой в имени. На деле это поток из двух групп.
        LessonEntry fromCell = CellDialect.EDUCATOR.read(
                cell("338-7", "10073/19, 10073/22", "ОВО"), header(CutKind.EDUCATOR, "п-к Иванов Т.В. дин"));
        assertThat(fromCell.groups()).containsExactly("10073/19", "10073/22");

        LessonEntry fromHeader = CellDialect.GROUP.read(
                cell("Л/Т.4", "УПМВ", "416-3"), header(CutKind.GROUP, "10073/19, 10073/22"));
        assertThat(fromHeader.groups()).containsExactly("10073/19", "10073/22");
    }

    @Test
    @DisplayName("Одиночная группа перечнем не становится: разделителей нет — строка как есть")
    void singleGroupIsUntouched() {
        assertThat(CellDialect.AUDITORIUM.read(cell("Л", "1155-1", "ОВО"),
                header(CutKind.AUDITORIUM, "338-7")).groups()).containsExactly("1155-1");
    }

    @Test
    @DisplayName("Неопознанный разрез диалекта не получает — «самый вероятный» не подставляется")
    void unknownCutHasNoDialect() {
        assertThat(CellDialect.of(CutKind.UNKNOWN)).isNull();
        assertThat(CellDialect.of(CutKind.GROUP)).isEqualTo(CellDialect.GROUP);
    }
}
