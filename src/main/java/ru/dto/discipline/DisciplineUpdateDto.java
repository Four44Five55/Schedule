package ru.dto.discipline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для запроса на обновление существующей дисциплины.
 *
 * @param name         Новое название дисциплины.
 * @param abbreviation Новая аббревиатура.
 */
public record DisciplineUpdateDto(
        @NotBlank(message = "Название дисциплины не может быть пустым")
        @Size(min = 2, max = 255, message = "Длина названия должна быть от 2 до 255 символов")
        String name,

        @Size(max = 50, message = "Длина аббревиатуры не должна превышать 50 символов")
        String abbreviation
) {
}
