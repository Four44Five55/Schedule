package ru.dto.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO для сессии расписания (Command Side).
 */
public record ScheduleSessionDto(
    UUID id,
    String name,
    SessionStatusDto status,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy,
    Long version,
    int placementsCount,
    boolean hasWorkspaceSnapshot
) {
    public enum SessionStatusDto {
        INITIALIZED, GENERATING, READY_FOR_EDIT, FINAL, ARCHIVED
    }
}
