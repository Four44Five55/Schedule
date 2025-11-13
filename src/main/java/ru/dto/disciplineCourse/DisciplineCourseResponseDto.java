package ru.dto.disciplineCourse;

/**
 * DTO для отображения информации об учебном курсе дисциплины.
 *
 * @param id         Уникальный идентификатор курса.
 * @param semester   Номер семестра, в котором читается курс.
 * @param discipline Вложенный DTO с краткой информацией о дисциплине.
 */
public record DisciplineCourseResponseDto(
        Integer id,
        int semester,
        DisciplineBriefDto discipline
) {

}
