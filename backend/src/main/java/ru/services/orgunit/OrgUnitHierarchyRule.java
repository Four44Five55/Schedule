package ru.services.orgunit;

import ru.enums.OrgUnitType;

import java.util.Map;
import java.util.Optional;

/**
 * Правило вложенности подразделений. Чистая функция — ни Spring, ни БД (как
 * {@link ru.services.order.LessonOrderRule} и {@code TrackReorderStrategy}), поэтому покрывается
 * быстрыми юнит-тестами.
 *
 * <p>Отвечает на два вопроса: <b>можно ли сделать X родителем Y</b>
 * ({@link #validateParent}) и <b>можно ли сменить вид подразделения</b>
 * ({@link #validateTypeChange}). Второй вопрос отдельный не для симметрии: смена вида меняет
 * ранг узла, а значит может обессмыслить вложенность уже существующих детей — проверка только
 * родителя смотрит вверх и этой стороны не видит.</p>
 *
 * <p><b>Глубину дерева отдельно ограничивать не нужно.</b> Ранги строго возрастают вниз
 * ({@link OrgUnitType#canContain}), поэтому длина любой ветки не может превысить число
 * различных рангов: новый уровень организации — это всегда новый вид подразделения. Числовой
 * предел глубины был бы недостижимой веткой.</p>
 *
 * <p><b>Кольцо по той же причине структурно невозможно</b>, пока все записи идут через это
 * правило: у потомка ранг строго больше, чем у предка, и «родитель внутри собственного
 * поддерева» противоречит проверке вида. Проверка всё же есть и стоит <b>раньше</b> проверки
 * вида — ради точного сообщения («получилось бы кольцо» вместо «кафедра не содержит институт»)
 * и ради данных, записанных мимо правила (ручной SQL, миграция).</p>
 *
 * <p><b>Уникальность имени правилу не принадлежит</b> — её держит схема (частичные UNIQUE-индексы
 * на {@code org_unit}), и дублировать её здесь значило бы завести второй источник правды.</p>
 */
public class OrgUnitHierarchyRule {

    /**
     * Узел дерева в виде, достаточном для проверки: без имён, связей и JPA.
     *
     * @param id       идентификатор подразделения
     * @param type     вид (задаёт ранг вложенности)
     * @param parentId текущий родитель; {@code null} — верхний уровень
     */
    public record UnitNode(Integer id, OrgUnitType type, Integer parentId) {
    }

    /**
     * Причина отказа. Бэк отдаёт семантику, формулировку для пользователя строит вызывающий
     * сервис (тот же приём, что у {@code OrderViolation.Kind}).
     */
    public enum Rejection {
        /** Родителя с таким id не существует. */
        PARENT_NOT_FOUND,
        /** Подразделение назначено родителем самому себе. */
        SELF_PARENT,
        /** Новый родитель лежит внутри поддерева перемещаемого подразделения. */
        CYCLE,
        /** Вид родителя не может содержать вид ребёнка (кафедра не содержит факультет). */
        TYPE_NOT_ALLOWED,
        /** Новый вид подразделения не может содержать уже вложенные в него подразделения. */
        TYPE_BREAKS_CHILDREN
    }

    /**
     * Проверяет, допустимо ли назначить подразделению родителя {@code newParentId}.
     *
     * @param childId     id проверяемого подразделения; {@code null} при создании нового
     * @param childType   вид проверяемого подразделения (при правке — <b>новый</b>)
     * @param newParentId предполагаемый родитель; {@code null} — верхний уровень, всегда допустим
     * @param allUnits    все существующие подразделения по id (дерево целиком, оно невелико)
     * @return причина отказа либо пустой {@code Optional}, если родитель допустим
     */
    public Optional<Rejection> validateParent(Integer childId,
                                              OrgUnitType childType,
                                              Integer newParentId,
                                              Map<Integer, UnitNode> allUnits) {
        if (newParentId == null) {
            return Optional.empty(); // верхний уровень: кафедра вне факультета — легитимный случай
        }
        if (childId != null && childId.equals(newParentId)) {
            return Optional.of(Rejection.SELF_PARENT);
        }

        UnitNode parent = allUnits.get(newParentId);
        if (parent == null) {
            return Optional.of(Rejection.PARENT_NOT_FOUND);
        }
        // Раньше проверки вида — см. описание класса (сообщение точнее, битые данные ловятся).
        if (childId != null && isAtOrBelow(newParentId, childId, allUnits)) {
            return Optional.of(Rejection.CYCLE);
        }
        if (!parent.type().canContain(childType)) {
            return Optional.of(Rejection.TYPE_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    /**
     * Проверяет, что после смены вида подразделение по-прежнему может содержать своих детей.
     *
     * <p>Нужна отдельно от {@link #validateParent}: та смотрит вверх (годится ли новый родитель),
     * а смена вида бьёт вниз. Пример: у факультета есть вложенная кафедра; смена вида факультета
     * на кафедру оставила бы дерево в состоянии, которое сама же система запрещает создавать.</p>
     *
     * @param unitId   id подразделения; {@code null} (создание) — детей ещё нет, проверять нечего
     * @param newType  новый вид
     * @param allUnits все существующие подразделения по id
     * @return {@link Rejection#TYPE_BREAKS_CHILDREN} либо пустой {@code Optional}
     */
    public Optional<Rejection> validateTypeChange(Integer unitId,
                                                  OrgUnitType newType,
                                                  Map<Integer, UnitNode> allUnits) {
        if (unitId == null) {
            return Optional.empty();
        }
        boolean breaksChild = allUnits.values().stream()
                .filter(node -> unitId.equals(node.parentId()))
                .anyMatch(child -> !newType.canContain(child.type()));

        return breaksChild ? Optional.of(Rejection.TYPE_BREAKS_CHILDREN) : Optional.empty();
    }

    /**
     * Лежит ли {@code nodeId} в поддереве {@code ancestorId} (включая сам узел).
     *
     * <p>Число шагов ограничено размером дерева: на битых данных с кольцом метод обязан дать
     * ответ, а не зациклиться.</p>
     */
    private boolean isAtOrBelow(Integer nodeId, Integer ancestorId, Map<Integer, UnitNode> allUnits) {
        Integer current = nodeId;
        for (int step = 0; current != null && step <= allUnits.size(); step++) {
            if (current.equals(ancestorId)) {
                return true;
            }
            UnitNode node = allUnits.get(current);
            if (node == null) {
                return false;
            }
            current = node.parentId();
        }
        return false;
    }
}
