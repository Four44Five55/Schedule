package ru.services.orgunit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.entity.OrgUnit;
import ru.enums.OrgUnitType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подъём по дереву подразделений: «какой факультет над этим узлом».
 *
 * <p>Проверяется то, что легко потерять правкой: сам узел считается своим предком, ветвь без
 * искомого вида даёт пустой ответ (а не «первое попавшееся сверху»), и кольцо в битых данных не
 * подвешивает обход. Последнее — то же требование, что у {@link OrgUnitSubtree}: данные могли быть
 * записаны мимо {@link OrgUnitHierarchyRule} (ручной SQL, миграция).</p>
 */
class OrgUnitAncestryTest {

    @Test
    @DisplayName("Кафедра под факультетом — факультет находится")
    void departmentUnderFacultyFindsIt() {
        OrgUnit faculty = unit(1, "9 факультет", OrgUnitType.FACULTY, null);
        OrgUnit department = unit(2, "91 кафедра", OrgUnitType.DEPARTMENT, faculty);

        OrgUnit found = OrgUnitAncestry.nearestOfType(department.getId(), OrgUnitType.FACULTY, byId(faculty, department));

        assertThat(found).isSameAs(faculty);
    }

    @Test
    @DisplayName("Ветвь любой глубины: кафедра → факультет → институт")
    void climbsThroughSeveralLevels() {
        OrgUnit institute = unit(1, "Институт", OrgUnitType.INSTITUTE, null);
        OrgUnit faculty = unit(2, "9 факультет", OrgUnitType.FACULTY, institute);
        OrgUnit department = unit(3, "91 кафедра", OrgUnitType.DEPARTMENT, faculty);
        Map<Integer, OrgUnit> all = byId(institute, faculty, department);

        assertThat(OrgUnitAncestry.nearestOfType(department.getId(), OrgUnitType.INSTITUTE, all))
                .as("институт лежит через этаж — подъём не останавливается на первом родителе")
                .isSameAs(institute);
        assertThat(OrgUnitAncestry.nearestOfType(department.getId(), OrgUnitType.FACULTY, all))
                .as("ближайший факультет, а не самый верхний узел")
                .isSameAs(faculty);
    }

    @Test
    @DisplayName("Сам узел — свой предок: группа, закреплённая прямо за факультетом")
    void unitIsItsOwnAncestor() {
        OrgUnit faculty = unit(1, "9 факультет", OrgUnitType.FACULTY, null);

        assertThat(OrgUnitAncestry.nearestOfType(faculty.getId(), OrgUnitType.FACULTY, byId(faculty)))
                .isSameAs(faculty);
    }

    @Test
    @DisplayName("Кафедра прямо под институтом (случай «ОАК») — факультета над ней нет")
    void departmentUnderInstituteHasNoFaculty() {
        OrgUnit institute = unit(1, "Институт", OrgUnitType.INSTITUTE, null);
        OrgUnit department = unit(2, "ОАК", OrgUnitType.DEPARTMENT, institute);

        assertThat(OrgUnitAncestry.nearestOfType(department.getId(), OrgUnitType.FACULTY, byId(institute, department)))
                .as("вместо факультета нельзя подставить институт — графа бланка называется иначе")
                .isNull();
    }

    @Test
    @DisplayName("Кольцо в битых данных завершает подъём, а не подвешивает его")
    void cycleTerminates() {
        OrgUnit first = unit(1, "A", OrgUnitType.DEPARTMENT, null);
        OrgUnit second = unit(2, "B", OrgUnitType.DEPARTMENT, first);
        first.setParent(second); // так дерево записать нельзя — но в базе оно может оказаться

        assertThat(OrgUnitAncestry.nearestOfType(first.getId(), OrgUnitType.FACULTY, byId(first, second)))
                .isNull();
    }

    @Test
    @DisplayName("Тотальность: null, неизвестный id и обрыв цепочки дают пустой ответ")
    void isTotal() {
        OrgUnit faculty = unit(1, "9 факультет", OrgUnitType.FACULTY, null);
        OrgUnit orphan = unit(2, "Кафедра без родителя в карте", OrgUnitType.DEPARTMENT,
                unit(99, "потерянный", OrgUnitType.FACULTY, null));
        Map<Integer, OrgUnit> all = byId(faculty, orphan);

        assertThat(OrgUnitAncestry.nearestOfType(null, OrgUnitType.FACULTY, all)).isNull();
        assertThat(OrgUnitAncestry.nearestOfType(777, OrgUnitType.FACULTY, all)).isNull();
        assertThat(OrgUnitAncestry.nearestOfType(orphan.getId(), OrgUnitType.FACULTY, all))
                .as("родителя нет в карте — ответ пустой, а не исключение")
                .isNull();
        assertThat(OrgUnitAncestry.nearestOfType(faculty.getId(), null, all)).isNull();
        assertThat(OrgUnitAncestry.nearestOfType(faculty.getId(), OrgUnitType.FACULTY, null)).isNull();
    }

    private static OrgUnit unit(int id, String name, OrgUnitType type, OrgUnit parent) {
        OrgUnit unit = new OrgUnit(name, type);
        unit.setId(id);
        unit.setParent(parent);
        return unit;
    }

    private static Map<Integer, OrgUnit> byId(OrgUnit... units) {
        Map<Integer, OrgUnit> map = new HashMap<>();
        List.of(units).forEach(unit -> map.put(unit.getId(), unit));
        return map;
    }
}
