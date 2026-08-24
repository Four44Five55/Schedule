package ru.dto.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO для обновления существующего назначения.
 *
 * @param studyStreamId      Новый ID потока.
 * @param educatorIds        Новый список ID ведущих преподавателей.
 * @param reserveEducatorIds Новый список запасных (И-22): числятся, но не ведут.
 *                           {@code null}/пусто → запасных не остаётся. Пересечение с
 *                           {@code educatorIds} запрещено: роль у человека на занятии одна.
 */
public record AssignmentUpdateDto(
        @NotNull(message = "ID потока не может быть пустым")
        Integer studyStreamId,

        @NotEmpty(message = "Список ID преподавателей не может быть пустым")
        List<Integer> educatorIds,

        List<Integer> reserveEducatorIds
) {
}
