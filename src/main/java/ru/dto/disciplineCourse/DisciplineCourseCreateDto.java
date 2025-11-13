package ru.dto.disciplineCourse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * DTO для создания нового учебного курса.
 *
 * @param disciplineId ID существующей дисциплины, к которой привязывается курс. Не может быть null.
 * @param semester     Номер семестра. Должен быть в разумных пределах (например, от 1 до 12).
 */

public record DisciplineCourseCreateDto(
        @Getter
        @NotNull(message = "ID дисциплины не может быть пустым")
        Integer disciplineId,
        @Getter
        @Min(value = 1, message = "Номер семестра должен быть не меньше 1")
        @Max(value = 12, message = "Номер семестра должен быть не больше 12")
        int semester
) {
}
