package ru.dto.auditoriumPurpose;

/**
 * DTO для представления информации о назначении аудитории.
 *
 * @param id   Уникальный идентификатор.
 * @param name Название назначения (например, "Лекционная").
 */
public record AuditoriumPurposeDto(
        Integer id,
        String name
) {}