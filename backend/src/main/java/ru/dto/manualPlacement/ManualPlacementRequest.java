package ru.dto.manualPlacement;

import java.time.LocalDate;

/**
 * Запрос ручной установки занятия в слот (Фича 2, Фаза B).
 *
 * @param assignmentId  что ставим (назначение из палитры)
 * @param date          дата (YYYY-MM-DD)
 * @param slot          пара (имя {@link ru.enums.TimeSlotPair}: FIRST…FOURTH)
 * @param studyPeriodId учебный период — рамки валидации/кэша ячеек
 */
public record ManualPlacementRequest(
        Integer assignmentId,
        LocalDate date,
        String slot,
        Integer studyPeriodId
) {
}
