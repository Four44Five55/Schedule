package ru.dto.educator;

import java.util.Set;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

/**
 * DTO для полного представления информации о преподавателе.
 *
 * @param id                 Уникальный идентификатор.
 * @param name               ФИО преподавателя.
 * @param preferredDays      Набор предпочитаемых дней недели.
 * @param preferredTimeSlots Набор предпочитаемых пар.
 * @param compactSchedule    Флаг компактности расписания.
 * @param orgUnitId          Подразделение (кафедра или отдел); {@code null} — не распределён.
 * @param orgUnitName        Название подразделения — чтобы список читался без склейки на клиенте.
 */
public record EducatorDto(
        Integer id,
        String name,
        Set<DayOfWeek> preferredDays,
        Set<TimeSlotPair> preferredTimeSlots,
        boolean compactSchedule,
        Integer orgUnitId,
        String orgUnitName
) {}
