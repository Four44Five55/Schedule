package ru.dto.group;

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
        Integer baseAuditoriumId
) {
}
