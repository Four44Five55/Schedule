package ru.dto.group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для запроса на создание новой учебной группы.
 */
public record GroupCreateDto(
        @NotBlank(message = "Название группы не может быть пустым")
        @Size(max = 255)
        String name,

        @Min(value = 1, message = "Количество студентов должно быть больше нуля")
        int size,

        // ID домашней аудитории, необязательное поле
        Integer baseAuditoriumId,

        // Год набора (поступления), необязательное поле; рамка — от опечаток, не доменное правило
        @Min(value = 1900, message = "Год набора выглядит опечаткой")
        @Max(value = 2200, message = "Год набора выглядит опечаткой")
        Integer enrollmentYear,

        // Подразделение (кафедра или факультет), необязательное поле
        Integer orgUnitId
) {
}
