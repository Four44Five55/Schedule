package ru.dto.feature;

/**
 * DTO для представления информации об оснащении.
 *
 * @param id   Уникальный идентификатор.
 * @param name Полное название (например, "Интерактивная доска").
 * @param code Короткий код для использования в системе (например, "INTERACTIVE_BOARD").
 */
public record FeatureDto(
        Integer id,
        String name,
        String code
) {}
