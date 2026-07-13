package ru.dto.curriculumSlot;

/**
 * Предпросмотр последствий удаления занятия учебного плана (слота).
 *
 * <p>Удаление слота каскадом сносит его назначения, а вместе с ними — <b>размещения в расписании,
 * включая закреплённые вручную</b> ({@code curriculum_slot → assignment → lesson_placement},
 * везде {@code ON DELETE CASCADE}). До этой проверки предупреждения не было вовсе: у назначений
 * ({@code /assignments/{id}/delete-impact}) и у курса предпросмотр есть, а у слота — не было,
 * хотя теряется ровно то же самое.</p>
 *
 * @param slotId        id слота
 * @param position      номер занятия в плане (для текста подтверждения)
 * @param kindOfStudy   вид занятия
 * @param assignments   назначений будет снесено (поток + преподаватели)
 * @param placedLessons размещённых занятий уйдёт из расписания
 * @param lockedLessons из них закреплено вручную — потеряется ручная раскладка
 */
public record SlotDeletionImpactDto(
        Integer slotId,
        Integer position,
        String kindOfStudy,
        long assignments,
        long placedLessons,
        long lockedLessons
) {}
