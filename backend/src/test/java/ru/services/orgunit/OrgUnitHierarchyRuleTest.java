package ru.services.orgunit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.OrgUnitType;
import ru.services.orgunit.OrgUnitHierarchyRule.Rejection;
import ru.services.orgunit.OrgUnitHierarchyRule.UnitNode;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Юнит-тесты правила вложенности подразделений: допустимость родителя (ранг вида, кольцо,
 * «сам себе родитель») и допустимость смены вида при уже вложенных подразделениях.
 *
 * <p>Фиксируются и два неочевидных решения: кольцо проверяется <b>раньше</b> вида (ради точного
 * сообщения) и верхний уровень допустим для любого вида (кафедра вне факультета — легитимный
 * случай, а не недоделанные данные).</p>
 */
class OrgUnitHierarchyRuleTest {

    private final OrgUnitHierarchyRule rule = new OrgUnitHierarchyRule();

    private static final int INSTITUTE = 1;
    private static final int FACULTY = 2;
    private static final int DEPARTMENT = 3;
    private static final int DIVISION = 4;

    /**
     * Институт → факультет → кафедра, плюс отдел на верхнем уровне.
     */
    private static Map<Integer, UnitNode> tree() {
        Map<Integer, UnitNode> units = new LinkedHashMap<>();
        units.put(INSTITUTE, new UnitNode(INSTITUTE, OrgUnitType.INSTITUTE, null));
        units.put(FACULTY, new UnitNode(FACULTY, OrgUnitType.FACULTY, INSTITUTE));
        units.put(DEPARTMENT, new UnitNode(DEPARTMENT, OrgUnitType.DEPARTMENT, FACULTY));
        units.put(DIVISION, new UnitNode(DIVISION, OrgUnitType.DIVISION, null));
        return units;
    }

    // ===================== Родитель: допустимые случаи =====================

    @Test
    @DisplayName("Верхний уровень допустим для любого вида — кафедра вне факультета легитимна")
    void rootIsAllowedForAnyType() {
        Optional<Rejection> found =
                rule.validateParent(DEPARTMENT, OrgUnitType.DEPARTMENT, null, tree());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Факультет содержит кафедру")
    void facultyContainsDepartment() {
        Optional<Rejection> found =
                rule.validateParent(DEPARTMENT, OrgUnitType.DEPARTMENT, FACULTY, tree());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Кафедра может лежать в институте напрямую, минуя факультет")
    void departmentDirectlyUnderInstitute() {
        Optional<Rejection> found =
                rule.validateParent(DEPARTMENT, OrgUnitType.DEPARTMENT, INSTITUTE, tree());

        assertThat(found).isEmpty();
    }

    // ===================== Родитель: отказы =====================

    @Test
    @DisplayName("Кафедра не может содержать факультет — ранг родителя не ниже ранга ребёнка")
    void departmentCannotContainFaculty() {
        // Создание нового факультета внутри кафедры: берём именно создание (childId = null),
        // потому что перенос СУЩЕСТВУЮЩЕГО факультета под его же кафедру — это ещё и кольцо,
        // и отказ пришёл бы с другой формулировкой (см. moveIntoOwnDescendantIsCycle).
        Optional<Rejection> found =
                rule.validateParent(null, OrgUnitType.FACULTY, DEPARTMENT, tree());

        assertThat(found).contains(Rejection.TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Отдел и факультет — соседи по этажу: друг друга не вкладывают")
    void sameRankTypesDoNotNest() {
        Optional<Rejection> found =
                rule.validateParent(DIVISION, OrgUnitType.DIVISION, FACULTY, tree());

        assertThat(found).contains(Rejection.TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Сам себе родитель")
    void selfParent() {
        Optional<Rejection> found =
                rule.validateParent(FACULTY, OrgUnitType.FACULTY, FACULTY, tree());

        assertThat(found).contains(Rejection.SELF_PARENT);
    }

    @Test
    @DisplayName("Несуществующий родитель")
    void parentNotFound() {
        Optional<Rejection> found =
                rule.validateParent(FACULTY, OrgUnitType.FACULTY, 999, tree());

        assertThat(found).contains(Rejection.PARENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Перенос узла внутрь собственного потомка — кольцо, и сообщение именно про кольцо")
    void moveIntoOwnDescendantIsCycle() {
        // Институт под свою же кафедру. Проверка вида здесь тоже отказала бы, но сказала бы
        // пользователю не о том — поэтому кольцо проверяется первым.
        Optional<Rejection> found =
                rule.validateParent(INSTITUTE, OrgUnitType.INSTITUTE, DEPARTMENT, tree());

        assertThat(found).contains(Rejection.CYCLE);
    }

    @Test
    @DisplayName("Создание нового подразделения не принимается за кольцо (id ещё нет)")
    void creationIsNotMistakenForCycle() {
        Optional<Rejection> found =
                rule.validateParent(null, OrgUnitType.DEPARTMENT, FACULTY, tree());

        assertThat(found).isEmpty();
    }

    // ===================== Смена вида (смотрит вниз, на детей) =====================

    @Test
    @DisplayName("Смена вида факультета на кафедру ломает вложенную кафедру")
    void typeChangeBreaksExistingChildren() {
        Optional<Rejection> found =
                rule.validateTypeChange(FACULTY, OrgUnitType.DEPARTMENT, tree());

        assertThat(found).contains(Rejection.TYPE_BREAKS_CHILDREN);
    }

    @Test
    @DisplayName("Смена вида у листа допустима — ломать нечего")
    void typeChangeOfLeafIsAllowed() {
        Optional<Rejection> found =
                rule.validateTypeChange(DEPARTMENT, OrgUnitType.FACULTY, tree());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Смена вида на более высокий по рангу детей не ломает")
    void typeChangeToHigherRankIsAllowed() {
        Optional<Rejection> found =
                rule.validateTypeChange(FACULTY, OrgUnitType.INSTITUTE, tree());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Создание: детей ещё нет, смена вида проверять нечего")
    void typeChangeOnCreationIsNoop() {
        Optional<Rejection> found =
                rule.validateTypeChange(null, OrgUnitType.DEPARTMENT, tree());

        assertThat(found).isEmpty();
    }

    // ===================== Битые данные =====================

    @Test
    @DisplayName("Кольцо в уже записанных данных не подвешивает правило")
    void preExistingRingDoesNotHang() {
        // Такого через правило не создать, но ручной SQL или миграция — могут.
        Map<Integer, UnitNode> broken = new LinkedHashMap<>();
        broken.put(INSTITUTE, new UnitNode(INSTITUTE, OrgUnitType.INSTITUTE, FACULTY));
        broken.put(FACULTY, new UnitNode(FACULTY, OrgUnitType.FACULTY, INSTITUTE));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                rule.validateParent(DEPARTMENT, OrgUnitType.DEPARTMENT, INSTITUTE, broken));
    }
}
