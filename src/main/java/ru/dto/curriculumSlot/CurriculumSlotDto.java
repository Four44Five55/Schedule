package ru.dto.curriculumSlot;

import ru.enums.KindOfStudy;

/**
 * DTO для отображения информации о слоте учебного плана.
 *
 * @param id                    ID слота.
 * @param disciplineCourseId    ID курса, к которому относится слот.
 * @param position              Порядковый номер слота в курсе.
 * @param kindOfStudy           Вид занятия (лекция, практика и т.д.).
 * @param themeLesson           Краткая информация о теме (если есть).
 * @param requiredAuditorium    Краткая информация о жестко требуемой аудитории (если есть).
 * @param priorityAuditorium    Краткая информация о приоритетной аудитории (если есть).
 * @param allowedAuditoriumPool Краткая информация о пуле допустимых аудиторий (если есть).
 */
public record CurriculumSlotDto(
        Integer id,
        Integer disciplineCourseId,
        Integer position,
        KindOfStudy kindOfStudy,
        ThemeLessonBriefDto themeLesson,
        AuditoriumBriefDto requiredAuditorium,
        AuditoriumBriefDto priorityAuditorium,
        AuditoriumPoolBriefDto allowedAuditoriumPool
) {
    // Вспомогательные DTO для краткой информации.
    // Их можно вынести в отдельные файлы или оставить здесь как вложенные.

    public record ThemeLessonBriefDto(Integer id, String themeNumber, String title) {
    }

    public record AuditoriumBriefDto(Integer id, String name) {
    }

    public record AuditoriumPoolBriefDto(Integer id, String name) {
    }
}
