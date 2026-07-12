package ru.dto.manualPlacement;

/**
 * Запрос «куда можно поставить» занятие из палитры (Фича 2, Фаза B).
 *
 * <p>Зеркало {@link ru.dto.moveLesson.MoveSuggestionRequest}, но по {@code assignmentId}
 * (занятие ещё не размещено), а не по {@code placementId}.</p>
 *
 * @param assignmentId    что ставим (назначение из палитры)
 * @param rootEntityType  тип корневой сущности ('EDUCATOR' | 'GROUP' | 'AUDITORIUM')
 * @param rootEntityId    id корневой сущности (через которую открыта сетка)
 * @param studyPeriodId   учебный период — рамки ячеек
 */
public record PlacementOptionsRequest(
        Integer assignmentId,
        String rootEntityType,
        Integer rootEntityId,
        Integer studyPeriodId
) {
}
