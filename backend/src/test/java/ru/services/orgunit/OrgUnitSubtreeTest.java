package ru.services.orgunit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.enums.OrgUnitType;
import ru.services.orgunit.OrgUnitHierarchyRule.UnitNode;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Юнит-тесты разворота подразделения в поддерево — рекурсии, которой пользуется охват
 * («расписание кафедры»).
 *
 * <p>Фиксируются решения, которые иначе пришлось бы выводить из кода: функция <b>тотальна</b>
 * (неизвестный корень — пустое множество, а не исключение: суждение «так нельзя» выносит
 * {@link OrgUnitScopeResolver}, который один знает, откуда взялся id), корень входит в
 * собственное поддерево, а битые данные обязаны заканчиваться ответом, а не зависанием.</p>
 *
 * <p>Флага {@code active} в {@link UnitNode} нет вовсе — поэтому «расформированное подразделение
 * не выпадает из охвата» здесь не проверяется: это гарантировано структурно, функции нечем
 * отличить действующее от расформированного.</p>
 */
class OrgUnitSubtreeTest {

    private final OrgUnitSubtree subtree = new OrgUnitSubtree();

    private static final int INSTITUTE = 1;
    private static final int FACULTY = 2;
    private static final int DEPARTMENT = 3;
    private static final int OTHER_DEPARTMENT = 4;
    private static final int DIVISION = 5;

    /**
     * Институт → факультет → {кафедра, вторая кафедра}, плюс отдел отдельным корнем.
     */
    private static Map<Integer, UnitNode> tree() {
        Map<Integer, UnitNode> units = new LinkedHashMap<>();
        units.put(INSTITUTE, new UnitNode(INSTITUTE, OrgUnitType.INSTITUTE, null));
        units.put(FACULTY, new UnitNode(FACULTY, OrgUnitType.FACULTY, INSTITUTE));
        units.put(DEPARTMENT, new UnitNode(DEPARTMENT, OrgUnitType.DEPARTMENT, FACULTY));
        units.put(OTHER_DEPARTMENT, new UnitNode(OTHER_DEPARTMENT, OrgUnitType.DEPARTMENT, FACULTY));
        units.put(DIVISION, new UnitNode(DIVISION, OrgUnitType.DIVISION, null));
        return units;
    }

    // ===================== Обычные случаи =====================

    @Test
    @DisplayName("Поддерево факультета — он сам и обе его кафедры")
    void facultySubtreeIncludesItsDepartments() {
        Set<Integer> found = subtree.idsOf(FACULTY, tree());

        assertThat(found).containsExactlyInAnyOrder(FACULTY, DEPARTMENT, OTHER_DEPARTMENT);
    }

    @Test
    @DisplayName("Глубина произвольная: поддерево института доходит до кафедр через факультет")
    void subtreeReachesAnyDepth() {
        Set<Integer> found = subtree.idsOf(INSTITUTE, tree());

        assertThat(found)
                .containsExactlyInAnyOrder(INSTITUTE, FACULTY, DEPARTMENT, OTHER_DEPARTMENT);
    }

    @Test
    @DisplayName("Ветви разной глубины: кафедра прямо под институтом (случай «ОАК») в охвате наравне с факультетскими")
    void branchesOfDifferentDepthAreAllInScope() {
        // Реальная форма из выгрузки: часть кафедр подчинена институту напрямую, минуя факультет
        // (в файлах это и означает «ОАК»). Ранг допускает пропуск уровня: институт 10 < кафедра 30.
        Map<Integer, UnitNode> units = tree();
        int directDepartment = 6;
        units.put(directDepartment, new UnitNode(directDepartment, OrgUnitType.DEPARTMENT, INSTITUTE));

        Set<Integer> found = subtree.idsOf(INSTITUTE, units);

        assertThat(found).containsExactlyInAnyOrder(
                INSTITUTE, FACULTY, DEPARTMENT, OTHER_DEPARTMENT, directDepartment);
    }

    @Test
    @DisplayName("Лист — это поддерево из самого себя, а не пустое множество")
    void leafIsItsOwnSubtree() {
        Set<Integer> found = subtree.idsOf(DEPARTMENT, tree());

        assertThat(found).containsExactly(DEPARTMENT);
    }

    @Test
    @DisplayName("Соседняя ветка в охват не попадает")
    void siblingBranchIsNotIncluded() {
        Set<Integer> found = subtree.idsOf(DIVISION, tree());

        assertThat(found).containsExactly(DIVISION);
    }

    // Теста «корень идёт первым» здесь намеренно нет: порядок в множестве не контракт, и
    // зафиксировав его, мы связали бы будущую замену обхода (см. javadoc OrgUnitSubtree).

    // ===================== Тотальность =====================

    @Test
    @DisplayName("Несуществующий корень — пустое множество, а не исключение")
    void unknownRootYieldsEmptySet() {
        Set<Integer> found = subtree.idsOf(42, tree());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("null-корень — пустое множество")
    void nullRootYieldsEmptySet() {
        Set<Integer> found = subtree.idsOf(null, tree());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Пустое дерево — пустое множество")
    void emptyTreeYieldsEmptySet() {
        Set<Integer> found = subtree.idsOf(FACULTY, Map.of());

        assertThat(found).isEmpty();
    }

    // ===================== Битые данные =====================

    @Test
    @DisplayName("Кольцо в данных не подвешивает обход — каждый узел разворачивается один раз")
    void cycleInBrokenDataTerminates() {
        // Кольцо мимо правила (ручной SQL/миграция): факультет ↔ кафедра ссылаются друг на друга.
        Map<Integer, UnitNode> broken = new LinkedHashMap<>();
        broken.put(FACULTY, new UnitNode(FACULTY, OrgUnitType.FACULTY, DEPARTMENT));
        broken.put(DEPARTMENT, new UnitNode(DEPARTMENT, OrgUnitType.DEPARTMENT, FACULTY));

        Set<Integer> found = assertTimeoutPreemptively(
                Duration.ofSeconds(1), () -> subtree.idsOf(FACULTY, broken));

        assertThat(found).containsExactlyInAnyOrder(FACULTY, DEPARTMENT);
    }

    @Test
    @DisplayName("«Сам себе родитель» не подвешивает и не задваивает узел")
    void selfParentTerminates() {
        Map<Integer, UnitNode> broken = new LinkedHashMap<>();
        broken.put(FACULTY, new UnitNode(FACULTY, OrgUnitType.FACULTY, FACULTY));

        Set<Integer> found = assertTimeoutPreemptively(
                Duration.ofSeconds(1), () -> subtree.idsOf(FACULTY, broken));

        assertThat(found).containsExactly(FACULTY);
    }

    @Test
    @DisplayName("Ссылка на несуществующего родителя не роняет обход — узел просто не чей-то ребёнок")
    void danglingParentIsIgnored() {
        Map<Integer, UnitNode> broken = new LinkedHashMap<>();
        broken.put(FACULTY, new UnitNode(FACULTY, OrgUnitType.FACULTY, null));
        broken.put(DEPARTMENT, new UnitNode(DEPARTMENT, OrgUnitType.DEPARTMENT, 999));

        assertThat(subtree.idsOf(FACULTY, broken)).containsExactly(FACULTY);
        assertThat(subtree.idsOf(DEPARTMENT, broken)).containsExactly(DEPARTMENT);
    }

    // ===================== Контракт результата =====================

    @Test
    @DisplayName("Результат неизменяем — охват не должен дорабатываться вызывающим на месте")
    void resultIsUnmodifiable() {
        Set<Integer> found = subtree.idsOf(FACULTY, tree());

        assertThat(found).isUnmodifiable();
    }
}
