package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.OrgUnit;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты вывода подразделения — общего правила для сверки и заведения.
 *
 * <p>Главное, что здесь зафиксировано: <b>у каждого свой канал</b>. Преподаватель — шапка его
 * собственного файла, аудитория — шапка её файла, группа — цифры номера. Перепутать каналы значит
 * приписать человека к кафедре дисциплины, которую он ведёт, — ошибка тихая и в расписании не
 * видная.</p>
 *
 * <p>И второе: <b>где канал молчит, там {@code null}</b>, а не «самое вероятное». Именно эти строки
 * получают в отчёте ручной выбор кафедры.</p>
 */
class OrgUnitHintsTest {

    private static ParsedSheet sheet(String file, CutKind kind, String owner, String department) {
        return new ParsedSheet(file,
                new SheetHeader(kind, owner, "9Ф", department, 2025, "осенний"),
                List.of(), List.of(), List.of());
    }

    private static OrgUnit unit(int id, String name, String shortName) {
        OrgUnit unit = new OrgUnit();
        unit.setId(id);
        unit.setName(name);
        unit.setShortName(shortName);
        return unit;
    }

    @Test
    @DisplayName("Преподаватель: кафедра из шапки ЕГО файла, ключ — фамилия с инициалами")
    void educatorDepartmentComesFromOwnFile() {
        List<ParsedSheet> sheets = List.of(
                sheet("ВетровР.И..html", CutKind.EDUCATOR, "к-н Ветров Р.И. ктн", "91 кафедра"),
                // Групповой файл кафедру тоже несёт, но она относится к дисциплине, а не к человеку.
                sheet("911.html", CutKind.GROUP, "911", "42 кафедра"));

        assertThat(OrgUnitHints.byEducator(sheets, List.of("к-н")))
                .containsExactly(org.assertj.core.api.Assertions.entry("Ветров Р.И.", "91 кафедра"));
    }

    @Test
    @DisplayName("Преподаватель без своего файла — подсказки нет, и это строка для ручной простановки")
    void educatorWithoutOwnFileHasNoHint() {
        List<ParsedSheet> sheets = List.of(sheet("911.html", CutKind.GROUP, "911", "91 кафедра"));

        assertThat(OrgUnitHints.byEducator(sheets, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Аудитория: кафедра-владелец из шапки её собственного файла (И-24)")
    void roomOwnerComesFromOwnFile() {
        List<ParsedSheet> sheets = List.of(
                sheet("252-3.html", CutKind.AUDITORIUM, "252-3", "91 кафедра"),
                sheet("911.html", CutKind.GROUP, "911", "42 кафедра"));

        assertThat(OrgUnitHints.byRoom(sheets))
                .containsExactly(org.assertj.core.api.Assertions.entry("252-3", "91 кафедра"));
    }

    @Test
    @DisplayName("Группа: кафедра из номера — «951» это ф. 9, каф. 91 (склейка, §4)")
    void groupDepartmentComesFromNumber() {
        assertThat(OrgUnitHints.byGroupNumber("951")).isEqualTo("91");
    }

    @Test
    @DisplayName("Номер не по стандарту — подсказки нет: угадывать кафедру нечем")
    void unrecognizedGroupNumberHasNoHint() {
        assertThat(OrgUnitHints.byGroupNumber("ОВО")).isNull();
    }

    @Test
    @DisplayName("«91» и «91 кафедра» — одно подразделение: сравнение идёт по ключу")
    void nameVariantsResolveToSameUnit() {
        List<OrgUnit> units = List.of(unit(1, "91 кафедра", "91"));

        assertThat(OrgUnitHints.unique(units, "91")).contains(units.get(0));
        assertThat(OrgUnitHints.unique(units, "Каф. 91")).contains(units.get(0));
    }

    @Test
    @DisplayName("Одноимённых несколько — не выбираем: назвать чужую кафедру хуже, чем никакой")
    void ambiguousNameResolvesToNothing() {
        List<OrgUnit> units = List.of(unit(1, "91 кафедра", "91"), unit(2, "91", "91"));

        assertThat(OrgUnitHints.unique(units, "91")).isEmpty();
    }

    @Test
    @DisplayName("Имени нет вовсе — пусто, без падений")
    void blankNameResolvesToNothing() {
        assertThat(OrgUnitHints.unique(List.of(unit(1, "91 кафедра", "91")), null)).isEmpty();
        assertThat(OrgUnitHints.unique(List.of(unit(1, "91 кафедра", "91")), "  ")).isEmpty();
    }
}
