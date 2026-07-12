package ru.dto.assignment;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Массово снять «однотипные» назначения — зеркало {@link ApplyAssignmentToCourseDto}.
 *
 * <p>Удаляются назначения курса, у которых совпадает и поток, и состав преподавателей
 * с выбранным «вариантом», в пределах охвата {@code slotIds}. То есть ровно те строки,
 * что раньше могли быть проставлены сквозным назначением.</p>
 *
 * @param courseId      курс, по слотам которого снимаем назначения
 * @param studyStreamId поток/подгруппа выбранного варианта
 * @param educatorIds   состав преподавателей варианта (сравнивается как множество;
 *                      пусто → совпадение с назначениями без преподавателей)
 * @param slotIds       охват: {@code null}/пусто → все слоты курса; иначе — только
 *                      перечисленные (по умолчанию фронт кладёт сюда выбранные занятия)
 */
public record RemoveAssignmentsFromCourseDto(
        @NotNull(message = "ID курса не может быть пустым")
        Integer courseId,

        @NotNull(message = "ID потока не может быть пустым")
        Integer studyStreamId,

        List<Integer> educatorIds,

        List<Integer> slotIds
) {
}
