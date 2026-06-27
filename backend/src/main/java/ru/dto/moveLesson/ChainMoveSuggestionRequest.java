package ru.dto.moveLesson;

import java.util.List;
import java.util.UUID;

/**
 * Запрос на поиск вариантов переноса цепочки занятий.
 *
 * <p>Цепочку (какие звенья, в каком порядке, с учётом временного размыкания)
 * определяет фронт и присылает упорядоченный список размещений. Бэк ищет стартовые
 * ячейки, куда вся цепочка помещается подряд.</p>
 *
 * @param placementIds размещения цепочки в порядке следования по времени (сверху вниз)
 */
public record ChainMoveSuggestionRequest(List<UUID> placementIds) {
}
