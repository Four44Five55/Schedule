package ru.dto.board;

import java.util.List;

/**
 * Узел «сущность» (группа или преподаватель) на доске раскладки: счётчики + разбивка по дисциплинам.
 *
 * <p>Ось (группа/преподаватель) выбирается стратегией {@link ru.services.board.BoardAxis}. Сущность
 * присутствует, даже если все её занятия размещены (в т.ч. сгенерированы) — доска строится из полного
 * набора назначений курсов, а не из «оставшегося». Счётчики агрегируются из {@link #disciplines}.</p>
 *
 * @param id          id сущности (группы/преподавателя) — нужен фронту для загрузки ограничений
 * @param name        имя сущности
 * @param total       всего занятий сущности
 * @param placed      из них размещено
 * @param unplaced    из них в очереди
 * @param disciplines разбивка по дисциплинам (отсортирована по аббревиатуре)
 */
public record EntityPlacementDto(
        Integer id,
        String name,
        int total,
        int placed,
        int unplaced,
        List<DisciplinePlacementDto> disciplines
) {
}
