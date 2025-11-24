package ru.dto.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO для создания одного или нескольких назначений для ОДНОГО слота учебного плана.
 * Позволяет реализовать все сценарии: "один-ко-многим", "многие-ко-многим" и "деление группы".
 *
 * @param curriculumSlotId ID слота учебного плана, для которого создаются назначения.
 * @param assignments      Список назначений, где каждое описывает поток и преподавателей.
 */
public record AssignmentCreateDto(
        @NotNull(message = "ID слота учебного плана не может быть пустым")
        Integer curriculumSlotId,

        @NotEmpty(message = "Список назначений не может быть пустым")
        List<AssignmentDetail> assignments
) {
    /**
     * Описывает одно конкретное назначение: для какого потока и с какими преподавателями.
     *
     * @param studyStreamId ID потока/группы.
     * @param educatorIds   Список ID преподавателей. Может быть один или несколько.
     */
    public record AssignmentDetail(
            @NotNull(message = "ID потока не может быть пустым")
            Integer studyStreamId,

            @NotEmpty(message = "Список ID преподавателей не может быть пустым")
            List<Integer> educatorIds
    ) {
    }
}
