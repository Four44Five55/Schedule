package ru.dto.curriculumSlot;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.enums.AssessmentWindow;
import ru.enums.KindOfStudy;

/**
 * DTO для создания нового слота в учебном плане.
 *
 * @param disciplineCourseId    ID курса, в который добавляется слот.
 * @param position              Позиция, на которую вставляется слот.
 * @param kindOfStudy           Вид занятия.
 * @param themeLessonId         ID темы занятия (может быть null).
 * @param requiredAuditoriumId  ID жестко требуемой аудитории (может быть null).
 * @param priorityAuditoriumId  ID приоритетной аудитории (может быть null).
 * @param allowedAuditoriumPoolId ID пула допустимых аудиторий (может быть null).
 */
public record CurriculumSlotCreateDto(
        @NotNull(message = "ID курса не может быть пустым")
        Integer disciplineCourseId,

        @NotNull(message = "Позиция слота не может быть пустой")
        @Min(value = 0, message = "Позиция должна быть 0 или больше")
        Integer position,

        @NotNull(message = "Вид занятия не может быть пустым")
        KindOfStudy kindOfStudy,

        /**
         * Где сдаётся аттестация; {@code null} → {@code STUDY_TIME}. Необязательное намеренно:
         * подавляющее большинство слотов — обычные занятия, и требовать поле от каждого значило бы
         * ломать существующих клиентов ради поля, которое у них всегда одно и то же.
         */
        AssessmentWindow assessmentWindow,

        Integer themeLessonId,
        Integer requiredAuditoriumId,
        Integer priorityAuditoriumId,
        Integer allowedAuditoriumPoolId
) {}
