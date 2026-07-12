package ru.services.reindex;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;

/**
 * Ячейка расписания — «дата × пара». Цель броска и позиции переупаковки задаются в
 * терминах ячеек.
 */
public record Cell(LocalDate date, TimeSlotPair slot) {
}
