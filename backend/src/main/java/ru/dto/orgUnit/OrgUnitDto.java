package ru.dto.orgUnit;

import ru.enums.OrgUnitType;

/**
 * Подразделение в плоском виде: дерево из списка таких DTO собирает фронт.
 *
 * <p>Лейбл вида ({@code «Кафедра»}, {@code «Каф.»}) здесь <b>намеренно не дублируется</b> —
 * фронт берёт его из {@code GET /api/enums/org-unit-type}, где лежат все справочные значения
 * ровно по одному разу.</p>
 *
 * @param parentId   родитель; {@code null} — верхний уровень
 * @param parentName имя родителя, чтобы список читался без склейки на клиенте
 * @param active     действующее подразделение (расформированные скрываются из выбора)
 */
public record OrgUnitDto(
        Integer id,
        String name,
        String shortName,
        OrgUnitType type,
        Integer parentId,
        String parentName,
        boolean active
) {}
