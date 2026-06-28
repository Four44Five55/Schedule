package ru.dto.disciplineCourse;

import ru.dto.studyPeriod.StudyPeriodDto;

/**
 * DTO для отображения информации об учебном курсе дисциплины.
 *
 * @param id           Уникальный идентификатор курса.
 * @param semester     Порядковый семестр программы (1..12), на котором изучается дисциплина.
 * @param discipline   Вложенный DTO с краткой информацией о дисциплине.
 * @param studyPeriod  Учебный период (календарные рамки), к которому привязан курс.
 */
public record DisciplineCourseDto(
        Integer id,
        int semester,
        DisciplineBriefDto discipline,
        StudyPeriodDto studyPeriod
) {

}
