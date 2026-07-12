package ru.dto.disciplineCourse;

/**
 * Предпросмотр последствий удаления курса: сколько связанных сущностей будет снесено
 * каскадом. Показывается пользователю в подтверждении удаления в планировщике.
 *
 * @param courseId       id курса
 * @param disciplineName дисциплина (для текста подтверждения)
 * @param semester       семестр курса
 * @param slots          число слотов учебного плана курса
 * @param assignments    число назначений (поток/преподаватели) курса
 * @param placedLessons  число уже размещённых занятий в расписании
 */
public record CourseDeletionImpactDto(
        Integer courseId,
        String disciplineName,
        int semester,
        int slots,
        int assignments,
        long placedLessons
) {}
