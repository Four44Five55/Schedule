package ru.entity.constraints;

import ru.entity.CellForLesson;

/**
 * Одно ограничение, развёрнутое на конкретную ячейку (день + пара).
 *
 * <p>Вид ограничения приходит снимком {@link ConstraintKindRef}, а не сущностью справочника:
 * дальше эти данные живут в памяти решателя, которому БД знать не положено.</p>
 */
public record ConstraintData(
        CellForLesson cell,
        ConstraintKindRef kind
) {
}
