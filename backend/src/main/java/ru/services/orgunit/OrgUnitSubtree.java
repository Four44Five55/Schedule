package ru.services.orgunit;

import ru.services.orgunit.OrgUnitHierarchyRule.UnitNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Разворот подразделения в его поддерево. Чистая функция — ни Spring, ни БД (как
 * {@link OrgUnitHierarchyRule} и {@code TrackReorderStrategy}), поэтому покрывается быстрыми
 * юнит-тестами.
 *
 * <p>Отвечает на единственный вопрос: <b>какие подразделения лежат внутри данного</b>, включая
 * его само. Всё остальное — «а какие там преподаватели», «а какие группы» — принадлежит
 * {@link OrgUnitScopeResolver}, потому что требует БД.</p>
 *
 * <p><b>Почему обход в памяти, а не {@code WITH RECURSIVE}.</b> Подразделений десятки, и плоский
 * список дерева всё равно грузится целиком (его же отдаёт {@code GET /api/org-units}). Обмен —
 * один лишний маленький SELECT в обмен на то, что рекурсия остаётся тестируемой без БД: в
 * проекте нет Testcontainers, интеграционные тесты в карантине, и нативный SQL получил бы
 * нулевое покрытие. Если подразделений когда-нибудь станут тысячи, эту реализацию меняют
 * целиком, не трогая вызывающих: рекурсия живёт ровно здесь.</p>
 *
 * <p><b>Кольцо не подвешивает обход.</b> Множество посещённых узлов делает повтор невозможным —
 * то же требование, что у {@code OrgUnitHierarchyRule.isAtOrBelow}: на данных, записанных мимо
 * правила (ручной SQL, миграция), метод обязан дать ответ, а не зациклиться.</p>
 *
 * <p><b>Расформированные ({@code active = false}) из поддерева НЕ исключаются.</b> Флаг прячет
 * подразделение из выбора, но за ним остаются исторические преподаватели и группы; отбросив его,
 * «расписание факультета» молча потеряло бы часть людей. Кому нужен только действующий срез —
 * фильтрует после, зная, что именно фильтрует.</p>
 */
public class OrgUnitSubtree {

    /**
     * Идентификаторы всего поддерева {@code rootId}, включая его сам.
     *
     * <p>Функция <b>тотальна</b>: несуществующий корень (как и {@code null}) даёт пустое
     * множество, а не исключение. Ошибка «такого подразделения нет» — суждение прикладного
     * уровня, и его выносит {@link OrgUnitScopeResolver}, который единственный знает, откуда
     * взялся id.</p>
     *
     * <p>Порядок элементов <b>не является частью контракта</b>: результат — множество, и
     * связывать вызывающих порядком значило бы связать и будущую замену реализации (обход на
     * стороне БД никакого «корень первым» не обещает). Внутри порядок воспроизводим — но это
     * удобство отладки, а не гарантия.</p>
     *
     * @param rootId   корень поддерева
     * @param allUnits все существующие подразделения по id (дерево целиком, оно невелико)
     * @return id подразделений поддерева, включая сам корень; неизменяемое множество
     */
    public Set<Integer> idsOf(Integer rootId, Map<Integer, UnitNode> allUnits) {
        if (rootId == null || !allUnits.containsKey(rootId)) {
            return Set.of();
        }

        Map<Integer, List<Integer>> childrenByParent = indexChildren(allUnits);

        // Одно множество в двух ролях: и результат, и «уже посещённые» — именно вторая роль
        // делает кольцо в битых данных безопасным.
        Set<Integer> visited = new LinkedHashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(rootId);

        while (!queue.isEmpty()) {
            Integer current = queue.removeFirst();
            if (!visited.add(current)) {
                continue; // уже разворачивали — на кольце это и есть выход из обхода
            }
            queue.addAll(childrenByParent.getOrDefault(current, List.of()));
        }
        return Collections.unmodifiableSet(visited);
    }

    /**
     * Обратный индекс «родитель → дети»: в {@link UnitNode} ссылка идёт только вверх, а обход
     * идёт вниз. Строится на каждый вызов — дерево маленькое, а общий изменяемый индекс стал бы
     * ровно тем, чем уже провинился {@code CellForLessonFactory} (статический кэш, который
     * каждый вызывающий чистит под себя).
     */
    private static Map<Integer, List<Integer>> indexChildren(Map<Integer, UnitNode> allUnits) {
        Map<Integer, List<Integer>> childrenByParent = new HashMap<>();
        for (UnitNode node : allUnits.values()) {
            if (node.parentId() == null) {
                continue; // верхний уровень
            }
            // Отдельной проверки «сам себе родитель» здесь нет намеренно: такой узел попадёт
            // в собственные дети, но обход возьмёт его один раз — множество посещённых уже
            // делает эту ветку недостижимой, а недостижимой ветке в коде не место.
            childrenByParent
                    .computeIfAbsent(node.parentId(), key -> new ArrayList<>())
                    .add(node.id());
        }
        return childrenByParent;
    }
}
