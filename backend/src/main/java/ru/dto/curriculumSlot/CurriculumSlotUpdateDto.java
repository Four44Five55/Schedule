package ru.dto.curriculumSlot;

import jakarta.validation.constraints.NotNull;
import ru.enums.AssessmentWindow;
import ru.enums.KindOfStudy;

/**
 * DTO для обновления существующего слота в учебном плане.
 * Содержит только те поля, которые разрешено изменять.
 *
 * @param kindOfStudy             Новый вид занятия.
 * @param themeLessonId           Новый ID темы занятия (может быть null).
 * @param requiredAuditoriumId    Новый ID жестко требуемой аудитории (может быть null).
 * @param priorityAuditoriumId    Новый ID приоритетной аудитории (может быть null).
 * @param allowedAuditoriumPoolId Новый ID пула допустимых аудиторий (может быть null).
 */
public record CurriculumSlotUpdateDto(
        @NotNull(message = "Вид занятия не может быть пустым")
        KindOfStudy kindOfStudy,

        /** Где сдаётся аттестация; {@code null} → {@code STUDY_TIME} (см. Create-DTO). */
        AssessmentWindow assessmentWindow,

        Integer themeLessonId,
        Integer requiredAuditoriumId,
        Integer priorityAuditoriumId,
        Integer allowedAuditoriumPoolId
) {
}