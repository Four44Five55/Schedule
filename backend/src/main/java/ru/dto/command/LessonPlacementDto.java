package ru.dto.command;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTO для размещения занятия.
 */
public record LessonPlacementDto(
    UUID id,
    UUID sessionId,
    Integer assignmentId,
    LocalDate scheduledDate,
    String scheduledSlot,
    Set<Integer> auditoriumIds,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy
) {
}
