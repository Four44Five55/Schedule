package ru.services.auditorium;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Занятие в конкретной комнате и ячейке — вход правила {@link AuditoriumUsageRule}.
 *
 * <p>Одна запись = одна пара «размещение × аудитория». Занятие с несколькими комнатами даёт
 * несколько записей: правило судит каждую комнату отдельно, потому что и занята, и мала она
 * тоже по отдельности.</p>
 *
 * <p><b>Имени комнаты здесь намеренно нет.</b> Правило сравнивает вместимость с числом людей и
 * ячейку с ячейкой; имя нужно только для показа, и живёт в сборочном слое
 * ({@link AuditoriumHealthService}). Так правило остаётся чистой функцией над числами.</p>
 *
 * @param placementId   размещение (чтобы вернуть находку наружу)
 * @param date          дата размещения
 * @param slot          пара размещения
 * @param auditoriumId  комната
 * @param capacity      мест в комнате
 * @param headcount     сколько человек придёт — суммарный размер групп потока
 */
public record RoomedLesson(
        UUID placementId,
        LocalDate date,
        TimeSlotPair slot,
        Integer auditoriumId,
        int capacity,
        int headcount
) {
}
