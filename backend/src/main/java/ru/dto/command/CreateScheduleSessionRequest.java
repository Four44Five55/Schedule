package ru.dto.command;

import java.util.List;

/**
 * DTO для запроса генерации/создания сессии расписания.
 *
 * @param name          название сессии/расписания
 * @param studyPeriodId учебный период, для которого генерируем расписание — источник
 *                      календарных дат (start/end) и набора курсов.
 * @param courseIds     опциональный поднабор курсов периода; если пуст/{@code null} —
 *                      берутся все курсы выбранного периода.
 */
public record CreateScheduleSessionRequest(
    String name,
    Integer studyPeriodId,
    List<Integer> courseIds
) {
}
