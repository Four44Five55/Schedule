package ru.dto.auditorium;

import java.util.List;

/**
 * Загрузка аудиторий за период: сводка + строки по комнатам (по образцу
 * {@code PeriodScheduleQualityDto}). Показывает утилизацию: пиковые и простаивающие комнаты.
 *
 * @param totalRooms       всего комнат
 * @param roomsUsed        задействованных (загрузка &gt; 0)
 * @param idleRooms        простаивающих (загрузка = 0)
 * @param availablePairs   доступных ячеек «дата×пара» за период (знаменатель загрузки; одинаков для всех комнат)
 * @param avgLoadPercent   средняя загрузка по задействованным комнатам
 * @param auditoriums      строки по комнатам, самые загруженные первыми
 */
public record PeriodAuditoriumLoadDto(
        int totalRooms,
        int roomsUsed,
        int idleRooms,
        int availablePairs,
        double avgLoadPercent,
        List<AuditoriumLoadDto> auditoriums
) {
}
