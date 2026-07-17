package ru.dto.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTO для переноса занятия.
 *
 * @param reorder включать ли авто-пересортировку трека в порядок плана после переноса.
 *                {@code null}/{@code true} — как обычно (требование заказчика); {@code false} —
 *                режим «перенос без пересортировки»: двигаем только это занятие, соседей не трогаем
 *                (перенос всё равно валидируется и комнату подбирает бэк).
 */
public record MoveLessonRequest(
    UUID placementId,
    LocalDate newDate,
    String newSlot,
    Set<Integer> newAuditoriumIds,
    Long version,
    Boolean reorder
) {
}
