package ru.dto.auditorium;

/**
 * Загрузка одной аудитории за период — строка отчёта (по образцу {@code EducatorScheduleQualityDto},
 * но метрики про <b>утилизацию</b>, а не компактность: у комнаты нет «окон/штрафа», у неё есть
 * «сколько занята от доступного»).
 *
 * @param auditoriumId  комната
 * @param name          имя (для показа)
 * @param capacity      мест
 * @param occupiedPairs занятых ячеек «дата×пара» (двойное бронирование считается за одну — это про время)
 * @param freePairs     свободных ячеек = доступные периода − занятые
 * @param loadPercent   загрузка по времени: занято / доступно × 100
 * @param daysUsed      дней, в которые комната использована
 * @param avgPairsPerDay среднее пар в используемый день
 * @param fourthPairs   дней с занятой 4-й парой
 * @param saturdayPairs пар в субботы
 */
public record AuditoriumLoadDto(
        Integer auditoriumId,
        String name,
        int capacity,
        int occupiedPairs,
        int freePairs,
        double loadPercent,
        int daysUsed,
        double avgPairsPerDay,
        int fourthPairs,
        int saturdayPairs
) {
}
