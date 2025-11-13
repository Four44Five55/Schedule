package ru.dto.disciplineCourse;
/**
 * Краткое DTO для отображения основной информации о дисциплине.
 * Используется как вложенный объект в других DTO.
 *
 * @param id           Уникальный идентификатор дисциплины.
 * @param name         Название дисциплины.
 * @param abbreviation Аббревиатура дисциплины
 */
public record DisciplineBriefDto(
        Integer id,
        String name,
        String abbreviation
) {
}