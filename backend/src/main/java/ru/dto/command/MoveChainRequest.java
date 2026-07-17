package ru.dto.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO для переноса цепочки занятий как единого целого.
 *
 * <p>Звенья ставятся в подряд идущие пары одного дня, начиная с {@code newStartSlot}.
 * Аудитории подбираются на бэке (как и в одиночном переносе), поэтому здесь их нет.</p>
 *
 * @param placementIds  размещения цепочки в порядке следования по времени
 * @param newStartDate  дата, на которую переносим первое звено (вся цепочка — этот же день)
 * @param newStartSlot  пара для первого звена ({@link ru.enums.TimeSlotPair}); остальные — следом
 * @param version       ожидаемая версия сессии (optimistic lock)
 * @param reorder       включать ли авто-пересортировку трека после переноса. {@code null}/{@code true}
 *                      — как обычно; {@code false} — режим «перенос без пересортировки»
 */
public record MoveChainRequest(
    List<UUID> placementIds,
    LocalDate newStartDate,
    String newStartSlot,
    Long version,
    Boolean reorder
) {
}
