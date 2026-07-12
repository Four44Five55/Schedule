package ru.dto.manualPlacement;

import java.util.List;

/**
 * Неразмещённое занятие для палитры ручной раскладки (Фича 2, Фаза B).
 *
 * <p>Это {@link ru.entity.Assignment} выбранного курса, у которого ещё нет
 * {@link ru.entity.write.LessonPlacement} в текущей сессии. Поля денормализованы,
 * чтобы фронт сгруппировал палитру по группам/дисциплинам/преподавателям без
 * дополнительных запросов.</p>
 *
 * @param assignmentId          назначение (что ставим вручную)
 * @param curriculumSlotId      слот учебного плана
 * @param courseId              курс (для группировки)
 * @param disciplineName        дисциплина (полное имя)
 * @param disciplineAbbreviation дисциплина (аббревиатура)
 * @param kindOfStudy           вид занятия (enum name: LECTURE, …)
 * @param kindOfStudyName       вид (полное имя)
 * @param kindOfStudyAbbr       вид (аббревиатура: Л, ПЗ, …)
 * @param position              позиция занятия в плане
 * @param themeNumber           номер темы (или null)
 * @param themeTitle            название темы (или null)
 * @param studyStreamId         поток
 * @param streamName            имя потока
 * @param groupIds              id групп потока
 * @param groupNames            имена групп потока
 * @param educatorIds           id преподавателей
 * @param educatorNames         имена преподавателей
 */
public record UnplacedLessonDto(
        Integer assignmentId,
        Integer curriculumSlotId,
        Integer courseId,
        String disciplineName,
        String disciplineAbbreviation,
        String kindOfStudy,
        String kindOfStudyName,
        String kindOfStudyAbbr,
        Integer position,
        String themeNumber,
        String themeTitle,
        Integer studyStreamId,
        String streamName,
        List<Integer> groupIds,
        List<String> groupNames,
        List<Integer> educatorIds,
        List<String> educatorNames
) {
}
