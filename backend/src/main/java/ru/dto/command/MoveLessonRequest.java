package ru.dto.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTO для переноса занятия.
 */
public record MoveLessonRequest(
    UUID placementId,
    LocalDate newDate,
    String newSlot,
    Set<Integer> newAuditoriumIds,
    Long version
) {
}
