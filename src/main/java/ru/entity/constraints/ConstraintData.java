package ru.entity.constraints;

import ru.entity.CellForLesson;
import ru.enums.KindOfConstraints;

/**
 * Простой DTO для передачи данных об одном ограничении.
 */
public record ConstraintData(
        CellForLesson cell,
        KindOfConstraints kind
) {
}
