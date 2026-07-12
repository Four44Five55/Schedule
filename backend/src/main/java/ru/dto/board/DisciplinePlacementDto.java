package ru.dto.board;

import java.util.List;

/**
 * Узел «дисциплина» на доске раскладки: счётчики + занятия одной сущности по этой дисциплине.
 *
 * <p>Часть Composite-дерева {@link PlacementBoardDto} → {@link EntityPlacementDto} → этот узел →
 * {@link BoardLessonDto}. Счётчики агрегируются снизу (по {@link #lessons}).</p>
 *
 * @param courseId     курс (дисциплина в периоде)
 * @param abbreviation аббревиатура дисциплины
 * @param name         полное имя дисциплины
 * @param total        всего занятий этой дисциплины у сущности
 * @param placed       из них размещено
 * @param unplaced     из них в очереди ({@code total - placed})
 * @param lessons      все занятия (размещённые и нет), отсортированы по позиции в плане
 */
public record DisciplinePlacementDto(
        Integer courseId,
        String abbreviation,
        String name,
        int total,
        int placed,
        int unplaced,
        List<BoardLessonDto> lessons
) {
}
