package ru.services.constraints;

import ru.entity.constraints.ConstraintData;

import java.util.List;
import java.util.Map;

/**
 * DTO для хранения всех загруженных из БД ограничений,
 * сгруппированных по типу и ID ресурса.
 *
 * @param educatorConstraints   Карта: ID преподавателя -> Список его ограничений.
 * @param groupConstraints      Карта: ID группы -> Список ее ограничений.
 * @param auditoriumConstraints Карта: ID аудитории -> Список ее ограничений.
 */
public record AllConstraints(
        Map<Integer, List<ConstraintData>> educatorConstraints,
        Map<Integer, List<ConstraintData>> groupConstraints,
        Map<Integer, List<ConstraintData>> auditoriumConstraints
) {
}
