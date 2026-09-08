package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.OrgUnitType;
import ru.services.importing.OrgStructureReader.OrgUnitDraft;
import ru.services.importing.ParsedSheet.CutKind;
import ru.services.importing.ParsedSheet.SheetHeader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты чтения оргструктуры из шапок.
 *
 * <p>Главное, что здесь зафиксировано: <b>вид подразделения берётся из канала, а родитель — из
 * шапки того же файла</b>. Это и есть разница между «завести можно» и «завести нельзя»: по строке
 * «91» роль действительно не видна, но канал, из которого строка пришла, её называет.</p>
 */
class OrgStructureReaderTest {

    private static ParsedSheet sheet(String file, CutKind kind, String owner, String faculty, String department) {
        return new ParsedSheet(file,
                new SheetHeader(kind, owner, faculty, department, 2025, "осенний"),
                List.of(), List.of(), List.of());
    }

    private static OrgUnitDraft find(List<OrgUnitDraft> drafts, String key) {
        return drafts.stream().filter(draft -> draft.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Факультет из шапки, кафедра из шапки, родитель — тот же файл")
    void facultyAndDepartmentComeFromTheSameHeader() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("ВетровР.И.html", CutKind.EDUCATOR, "к-н Ветров Р.И.", "9Ф", "91 кафедра")));

        assertThat(find(drafts, "9ф").type()).isEqualTo(OrgUnitType.FACULTY);
        assertThat(find(drafts, "91")).satisfies(department -> {
            assertThat(department.type()).isEqualTo(OrgUnitType.DEPARTMENT);
            assertThat(department.parentName()).isEqualTo("9Ф");
            assertThat(department.name()).isEqualTo("91 кафедра");
            assertThat(department.shortName()).isEqualTo("91");
        });
    }

    @Test
    @DisplayName("Номер без кафедры (короткий курс 11 факультета) узла не заводит — и не роняет разбор")
    void groupNumberWithoutDepartmentCreatesNoUnit() {
        // «11434» разбирается (факультет 11), но кафедру его форма не кодирует. Пока null доезжал
        // до накопителя, он схлопывался в пустой ключ и ронял чтение оргструктуры на пустом имени.
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("11434.html", CutKind.GROUP, "11434", null, null)));

        assertThat(drafts).isEmpty();
    }

    @Test
    @DisplayName("Безымянный узел не заводится: пустое имя собрало бы под собой все молчащие каналы")
    void blankNameCreatesNoUnit() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("911.html", CutKind.GROUP, "911", "  ", "")));

        assertThat(drafts).extracting(OrgUnitDraft::key).containsExactly("91");
    }

    @Test
    @DisplayName("Факультет заводится раньше своей кафедры — иначе её некуда вешать")
    void facultiesComeFirst() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("ВетровР.И.html", CutKind.EDUCATOR, "Ветров Р.И.", "9Ф", "91 кафедра")));

        assertThat(drafts).extracting(OrgUnitDraft::type)
                .containsExactly(OrgUnitType.FACULTY, OrgUnitType.DEPARTMENT);
    }

    @Test
    @DisplayName("Кафедра из номера группы — тот же узел, что «91 кафедра» из шапки")
    void departmentFromGroupNumberIsTheSameUnit() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("911.html", CutKind.GROUP, "911", "9Ф", null),
                sheet("ВетровР.И.html", CutKind.EDUCATOR, "Ветров Р.И.", "9Ф", "91 кафедра")));

        // Два канала об одном: цифры номера группы дают «91», шапка — «91 кафедра».
        assertThat(drafts).filteredOn(draft -> draft.type() == OrgUnitType.DEPARTMENT).singleElement()
                .extracting(OrgUnitDraft::key, OrgUnitDraft::name)
                .containsExactly("91", "91 кафедра");
    }

    @Test
    @DisplayName("«ОАК» узлом не заводится: это метка «кафедра прямо под институтом» (И-4)")
    void oakIsNotAUnit() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", "ОАК", "53 кафедра")));

        assertThat(drafts).extracting(OrgUnitDraft::key).containsExactly("53");
        assertThat(find(drafts, "53").underInstitute()).isTrue();
        assertThat(find(drafts, "53").parentName()).isNull();
    }

    @Test
    @DisplayName("Спор каналов о родителе — находка, а не выбор наугад")
    void conflictingParentsAreReported() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", "9Ф", "91 кафедра"),
                sheet("Петров.html", CutKind.EDUCATOR, "Петров П.П.", "10Ф", "91 кафедра")));

        assertThat(find(drafts, "91").problem()).contains("разные факультеты");
        // Спор о РОДИТЕЛЕ снимается выбором человека в отчёте — значит это не спор о роли.
        assertThat(find(drafts, "91").roleDisputed()).isFalse();
    }

    @Test
    @DisplayName("Один текст и как факультет, и как кафедра — тоже находка")
    void conflictingRolesAreReported() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("a.html", CutKind.EDUCATOR, "Иванов И.И.", "51", null),
                sheet("b.html", CutKind.EDUCATOR, "Петров П.П.", "9Ф", "51")));

        assertThat(find(drafts, "51").problem()).contains("и факультетом, и кафедрой");
        // А этот спор выбором родителя не снять: пока неясно, что за узел, неясен и ранг родителя.
        assertThat(find(drafts, "51").roleDisputed()).isTrue();
    }

    @Test
    @DisplayName("Файлы называют подразделение в отчёте — их же и открывать при разборе")
    void draftsCarryTheirFiles() {
        List<OrgUnitDraft> drafts = OrgStructureReader.read(List.of(
                sheet("кафедра 91/Иванов.html", CutKind.EDUCATOR, "Иванов И.И.", "9Ф", "91 кафедра")));

        assertThat(find(drafts, "91").files()).containsExactly("кафедра 91/Иванов.html");
    }
}
