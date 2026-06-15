package ru.dto.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTO для creating a schedule session.
 */
public record CreateScheduleSessionRequest(
    String name,
    List<Integer> courseIds
) {
}
