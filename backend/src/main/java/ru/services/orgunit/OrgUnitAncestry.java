package ru.services.orgunit;

import ru.entity.OrgUnit;
import ru.enums.OrgUnitType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Подъём по дереву подразделений: ближайшее вверх подразделение заданного вида.
 *
 * <p>Встречное направление к {@link OrgUnitSubtree} (тот разворачивает вниз) и такая же чистая
 * функция — ни Spring, ни БД, поэтому покрывается быстрыми юнит-тестами.</p>
 *
 * <p>Отвечает на единственный вопрос: <b>какой факультет (институт, отдел) стоит над этим узлом</b>.
 * Появилось для шапки бланка: группа закреплена за кафедрой, а в графе «ФАКУЛЬТЕТ» бланка нужен
 * факультет, и вывести его можно только подъёмом по дереву.</p>
 *
 * <p><b>Почему сущности, а не {@code UnitNode}.</b> Правилам вложенности хватает плоского узла
 * (id · вид · родитель), а вызывающему здесь нужно <b>имя</b> найденного подразделения — то есть
 * сама строка. Отдавать id, чтобы вызывающий шёл за именем вторым проходом, значило бы отдать ему и
 * сборку карты, которую он уже сделал.</p>
 *
 * <p><b>Кольцо не подвешивает подъём</b> — множество посещённых, ровно как в
 * {@link OrgUnitSubtree}: на данных, записанных мимо {@link OrgUnitHierarchyRule} (ручной SQL,
 * миграция), метод обязан дать ответ, а не зациклиться.</p>
 */
public final class OrgUnitAncestry {

    private OrgUnitAncestry() {
    }

    /**
     * Ближайшее вверх подразделение вида {@code wanted}, считая от самого {@code startId}.
     *
     * <p>Функция <b>тотальна</b>: {@code null}, неизвестный id и обрыв цепочки дают {@code null}, а
     * не исключение. «Такого подразделения нет» — суждение прикладного уровня; здесь оно означало бы,
     * что у группы вне факультета бланк не выгружается вовсе.</p>
     *
     * <p>Сам узел считается своим предком: группа, закреплённая прямо за факультетом, получает
     * этот же факультет. Иначе пришлось бы городить проверку вида у вызывающего.</p>
     *
     * @param startId откуда подниматься (подразделение группы либо преподавателя)
     * @param wanted  искомый вид
     * @param allById все подразделения по id — дерево целиком, оно невелико (тот же список, что
     *                отдаёт {@code GET /api/org-units})
     * @return найденное подразделение либо {@code null}, если такого вида над узлом нет
     */
    public static OrgUnit nearestOfType(Integer startId, OrgUnitType wanted, Map<Integer, OrgUnit> allById) {
        if (startId == null || wanted == null || allById == null) {
            return null;
        }
        Set<Integer> visited = new HashSet<>();
        Integer currentId = startId;
        while (currentId != null && visited.add(currentId)) {
            OrgUnit current = allById.get(currentId);
            if (current == null) {
                return null; // цепочка оборвалась — родителя нет в карте
            }
            if (current.getType() == wanted) {
                return current;
            }
            currentId = OrgUnitNodes.parentIdOf(current);
        }
        return null;
    }
}
