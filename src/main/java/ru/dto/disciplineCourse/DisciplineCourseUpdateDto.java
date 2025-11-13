package ru.dto.disciplineCourse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * DTO для обновления существующего учебного курса.
 *
 * @param semester Новый номер семестра.
 */
public record DisciplineCourseUpdateDto(
        @Min(value = 1, message = "Номер семестра должен быть не меньше 1")
        @Max(value = 12, message = "Номер семестра должен быть не больше 12")
        int semester
) {
}