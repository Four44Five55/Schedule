package ru.dto.group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для запроса на обновление существующей учебной группы.
 */
public record GroupUpdateDto(
        @NotBlank(message = "Название группы не может быть пустым")
        @Size(max = 255)
        String name,

        @Min(value = 1, message = "Количество студентов должно быть больше нуля")
        int size,

        Integer baseAuditoriumId,

        // Год набора (поступления); null — снять значение
        @Min(value = 1900, message = "Год набора выглядит опечаткой")
        @Max(value = 2200, message = "Год набора выглядит опечаткой")
        Integer enrollmentYear,

        // Подразделение; null — открепить от подразделения
        Integer orgUnitId
) {}
