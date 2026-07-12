package ru.services.reindex;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Переезд размещения в новую ячейку. Меняется только дата/пара — содержание
 * ({@code assignment}) и аудитория не трогаются. Применение — на слое персистентности.
 *
 * @param placementId размещение
 * @param date        новая дата
 * @param slot        новая пара
 */
public record CellMove(UUID placementId, LocalDate date, TimeSlotPair slot) {
}
