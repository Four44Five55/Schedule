package ru.dto.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Назначить поток + преподавателей на ВСЕ занятия курса разом.
 *
 * @param courseId      курс, по слотам которого проставляем назначение
 * @param studyStreamId поток/подгруппа
 * @param educatorIds   преподаватели (минимум один)
 * @param overwrite     false → пропускать слоты, где назначение этого потока уже есть
 *                      (по умолчанию, ручные исключения не трогаются); true → заменять
 *                      состав преподавателей у существующих
 * @param slotIds       охват: {@code null}/пусто → все слоты курса (прежнее поведение);
 *                      иначе — только перечисленные слоты этого курса (выбор по видам/
 *                      конкретным занятиям разворачивается во фронте в набор id)
 */
public record ApplyAssignmentToCourseDto(
        @NotNull(message = "ID курса не может быть пустым")
        Integer courseId,

        @NotNull(message = "ID потока не может быть пустым")
        Integer studyStreamId,

        @NotEmpty(message = "Список ID преподавателей не может быть пустым")
        List<Integer> educatorIds,

        boolean overwrite,

        List<Integer> slotIds
) {
}
