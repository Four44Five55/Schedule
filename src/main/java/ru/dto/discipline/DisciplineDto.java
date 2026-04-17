package ru.dto.discipline;

import ru.dto.disciplineCourse.DisciplineCourseDto;

import java.util.List;

/**
 * DTO для полного представления информации о дисциплине.
 * Используется для ответов API, когда требуется полная информация.
 *
 * @param id           Уникальный идентификатор.
 * @param name         Полное название дисциплины.
 * @param abbreviation Краткое название (аббревиатура).
 * @param courses      Список курсов (по семестрам), принадлежащих этой дисциплине.
 */
public record DisciplineDto(
        Integer id,
        String name,
        String abbreviation,
        List<DisciplineCourseDto> courses
) {}
