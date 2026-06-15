package ru.dto.studyStream;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO для запроса на создание нового учебного потока.
 *
 * @param name     Название потока.
 * @param semester Номер семестра.
 * @param groupIds Список ID групп, которые нужно включить в поток.
 */
public record StudyStreamCreateDto(
        @NotBlank(message = "Название потока не может быть пустым")
        @Size(max = 255, message = "Длина названия не должна превышать 255 символов")
        String name,

        @Min(value = 1, message = "Номер семестра должен быть положительным числом")
        int semester,

        @NotEmpty(message = "Поток должен содержать как минимум одну группу")
        List<Integer> groupIds
) {
}