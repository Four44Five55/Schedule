package ru.dto;

import java.util.List;

/**
 * Сводка по качеству расписания преподавателей за период — для дашборда.
 *
 * <p>Метрики компактности ({@code wellPacked}, {@code avgPenalty}, {@code totalSinglePairDays},
 * {@code totalWindowSlots}) считаются по преподавателям с флагом {@code compact_schedule}.
 * {@code avgSaturday} — базовое среднее субботних пар по ВСЕМ ведущим преподавателям (для
 * оценки отклонений). Список {@code educators} включает всех ведущих (компактные — первыми,
 * затем по убыванию штрафа).</p>
 *
 * @param compactEducators    сколько преподавателей с флагом компактности ведут занятия
 * @param wellPacked          из них «плотно уложены» (0 окон и 0 одиночных дней)
 * @param avgPenalty          средний штраф компактности по флаговым преподавателям
 * @param totalSinglePairDays суммарно дней-одиночек у флаговых преподавателей
 * @param totalWindowSlots    суммарно окон-слотов у флаговых преподавателей
 * @param avgSaturday         среднее субботних пар по всем ведущим преподавателям
 * @param educators           детализация (компактные первыми, по штрафу)
 */
public record PeriodScheduleQualityDto(
        int compactEducators,
        int wellPacked,
        double avgPenalty,
        int totalSinglePairDays,
        int totalWindowSlots,
        double avgSaturday,
        List<EducatorScheduleQualityDto> educators
) {}
