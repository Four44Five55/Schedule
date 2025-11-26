package ru.dto.disciplineCourse;

import jakarta.validation.constraints.NotNull;

/**
 * DTO для обновления существующего учебного курса.
 *
 * @param studyPeriodId Новый учебный период.
 */
public record DisciplineCourseUpdateDto(
        @NotNull Integer studyPeriodId
) {
}