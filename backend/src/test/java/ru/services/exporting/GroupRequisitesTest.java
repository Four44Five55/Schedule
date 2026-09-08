package ru.services.exporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.Group;
import ru.entity.OrgUnit;
import ru.enums.OrgUnitType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Реквизиты шапки бланка группы: графы «ФАКУЛЬТЕТ» и «КУРС».
 *
 * <p>Курс в бланке — <b>вычисленное</b> значение: в выгрузке сторонней программы графа «Курс»
 * напечатана пустой, и взять его оттуда нельзя. Поэтому проверяется само правило («учебный год
 * минус год набора, плюс один») и, главное, случаи, где вычислять <b>нечего</b>: короткие курсы
 * 11 факультета и испорченные данные. Печать догадки в документе хуже пустой графы, и молча это
 * не увидеть — бланк уходит наружу.</p>
 */
class GroupRequisitesTest {

    private static final int STUDY_YEAR = 2025; // учебный год 2025/2026

    @Test
    @DisplayName("Курс — от года набора группы: набор 2023 в 2025/2026 — третий")
    void courseComesFromEnrollmentYear() {
        Group group = group("911", 2023);

        assertThat(GroupRequisites.of(group, null, STUDY_YEAR).course()).isEqualTo(3);
    }

    @Test
    @DisplayName("Года набора нет — выводится из номера группы (цифра набора в «911» — 2021)")
    void enrollmentYearFallsBackToGroupNumber() {
        Group group = group("911", null);

        assertThat(GroupRequisites.of(group, null, STUDY_YEAR).course())
                .as("2025 − 2021 + 1")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Своё поле важнее номера: заполненный год набора номер не переспорит")
    void ownEnrollmentYearWinsOverNumber() {
        Group group = group("911", 2024); // номер дал бы 2021, то есть пятый курс

        assertThat(GroupRequisites.of(group, null, STUDY_YEAR).course()).isEqualTo(2);
    }

    @Test
    @DisplayName("Короткий курс 11 факультета — года набора не существует, графа остаётся пустой")
    void shortCourseHasNoCourseNumber() {
        Group group = group("1152", null);

        assertThat(GroupRequisites.of(group, null, STUDY_YEAR).course()).isNull();
    }

    @Test
    @DisplayName("Номер не разобран — курс не выдумывается")
    void unrecognizedNumberGivesNoCourse() {
        Group group = group("ДС.1.О", null);

        assertThat(GroupRequisites.of(group, null, STUDY_YEAR).course()).isNull();
    }

    @Test
    @DisplayName("Набор позже учебного года (испорченные данные) — «0 курс» не печатается")
    void nonPositiveCourseIsNotPrinted() {
        Group group = group("911", 2026);

        assertThat(GroupRequisites.of(group, null, STUDY_YEAR).course()).isNull();
    }

    @Test
    @DisplayName("Факультет: краткое имя, а при его отсутствии — полное")
    void facultyLabelPrefersShortName() {
        Group group = group("911", 2023);
        OrgUnit faculty = new OrgUnit("9 факультет", OrgUnitType.FACULTY);

        assertThat(GroupRequisites.of(group, faculty, STUDY_YEAR).faculty())
                .as("краткого нет — печатается полное")
                .isEqualTo("9 факультет");

        faculty.setShortName("9Ф");
        assertThat(GroupRequisites.of(group, faculty, STUDY_YEAR).faculty())
                .as("графа узкая, и «9Ф» — ровно то написание, что в выгрузке")
                .isEqualTo("9Ф");
    }

    @Test
    @DisplayName("Факультет не найден — графа пуста, а не заполнена заглушкой")
    void missingFacultyLeavesGraphEmpty() {
        assertThat(GroupRequisites.of(group("911", 2023), null, STUDY_YEAR).faculty()).isNull();
        assertThat(GroupRequisites.of(null, null, STUDY_YEAR)).isEqualTo(GroupRequisites.EMPTY);
    }

    private static Group group(String name, Integer enrollmentYear) {
        Group group = new Group(name, 25);
        group.setEnrollmentYear(enrollmentYear);
        return group;
    }
}
