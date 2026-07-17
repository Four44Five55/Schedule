package ru.services.reindex;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Решение политики: какие комнаты у занятий после пересортировки и что пометить под флаг.
 *
 * <p>Комнаты возвращаются <b>только для переехавших</b> занятий (остальные ничего не меняют).
 * Проблемы — {@link ReorderProblem.Reason#AUDITORIUM_CONFLICT}: жёсткая комната была занята на
 * целевой ячейке, занятие посажено в запасную (базовую) — перенос не блокируем, диспетчер
 * разбирает вручную.</p>
 *
 * @param roomsByPlacement комнаты по размещению (id аудиторий); ключи — только переехавшие
 * @param problems         размещения под флагом с причиной
 */
public record ReorderRoomPlan(
        Map<UUID, Set<Integer>> roomsByPlacement,
        List<ReorderProblem> problems
) {
    public ReorderRoomPlan {
        roomsByPlacement = Map.copyOf(roomsByPlacement);
        problems = List.copyOf(problems);
    }
}
