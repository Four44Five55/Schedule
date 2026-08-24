package ru.dto.assignment;

import java.util.List;

/**
 * DTO для отображения полной информации о "Назначении".
 *
 * @param id             Уникальный идентификатор назначения.
 * @param curriculumSlot Краткая информация о слоте учебного плана.
 * @param studyStream    Краткая информация о потоке/группе.
 * @param educators        Список преподавателей, назначенных на это занятие (ведущие).
 * @param reserveEducators Запасные: числятся за дисциплиной, но занятий не ведут. В расписании
 *                         не участвуют — ни занятости, ни нагрузки, ни сетки; печатаются только
 *                         в подвале бланка группы после ведущих (решение И-22).
 */
public record AssignmentDto(
        Integer id,
        CurriculumSlotBriefDto curriculumSlot,
        StudyStreamBriefDto studyStream,
        List<EducatorBriefDto> educators,
        List<EducatorBriefDto> reserveEducators
) {

    public record CurriculumSlotBriefDto(Integer id, Integer position, String kindOfStudyName) {
    }

    public record StudyStreamBriefDto(Integer id, String name) {
    }

    public record EducatorBriefDto(Integer id, String name) {
    }
}
