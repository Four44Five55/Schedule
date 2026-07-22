package ru.services.orgunit;

import ru.entity.OrgUnit;
import ru.services.orgunit.OrgUnitHierarchyRule.UnitNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сборка входа для правил подразделений: JPA-сущности → плоские {@link UnitNode} по id.
 *
 * <p>Существует затем, чтобы этой сборки не было в двух местах. Правило вложенности
 * ({@link OrgUnitHierarchyRule}) и разворот поддерева ({@link OrgUnitSubtree}) — обе чистые
 * функции над одной и той же формой входа, и без общего сборщика каждый вызывающий завёл бы
 * свой {@code toNode}. Ровно этот дефект аудит зафиксировал у датчиков аудиторий: правило общее,
 * а сборка входа для него — нет.</p>
 *
 * <p>Пакетная видимость намеренна: {@link UnitNode} — язык правил подразделений, наружу пакета
 * ему незачем.</p>
 */
final class OrgUnitNodes {

    private OrgUnitNodes() {
    }

    /**
     * Все подразделения в виде, который понимают правила: без имён, связей и JPA.
     *
     * <p>{@code LinkedHashMap} — чтобы порядок обхода дерева был воспроизводим (в тестах и
     * логах), раз входной список уже отсортирован репозиторием.</p>
     */
    static Map<Integer, UnitNode> byId(Collection<OrgUnit> units) {
        Map<Integer, UnitNode> nodes = new LinkedHashMap<>();
        for (OrgUnit unit : units) {
            nodes.put(unit.getId(), new UnitNode(unit.getId(), unit.getType(), parentIdOf(unit)));
        }
        return nodes;
    }

    /**
     * Id вышестоящего подразделения; {@code null} — верхний уровень.
     */
    static Integer parentIdOf(OrgUnit unit) {
        return unit.getParent() == null ? null : unit.getParent().getId();
    }
}
