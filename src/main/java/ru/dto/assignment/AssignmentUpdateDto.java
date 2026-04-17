package ru.dto.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO для обновления существующего назначения.
 *
 * @param studyStreamId Новый ID потока.
 * @param educatorIds   Новый список ID преподавателей.
 */
public record AssignmentUpdateDto(
        @NotNull(message = "ID потока не может быть пустым")
        Integer studyStreamId,

        @NotEmpty(message = "Список ID преподавателей не может быть пустым")
        List<Integer> educatorIds
) {
}
