package ru.services;

import ru.enums.BlockedDaysAndSlots;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.Map;

public class ScheduleDaysSlotsConfig {
    //мапа используемая для полного ограничения создания ячеек для занятий
    private static final Map<DayOfWeek, BlockedDaysAndSlots> DEFAULT_RULES = Map.of(
            DayOfWeek.MONDAY, BlockedDaysAndSlots.WEEKDAY,// ограничений нет
            DayOfWeek.TUESDAY, BlockedDaysAndSlots.WEEKDAY,// ограничений нет
            DayOfWeek.WEDNESDAY, BlockedDaysAndSlots.WEEKDAY,// ограничений нет
            DayOfWeek.THURSDAY, BlockedDaysAndSlots.WEEKDAY,// ограничений нет
            DayOfWeek.FRIDAY, BlockedDaysAndSlots.WEEKDAY, // ограничений нет
            DayOfWeek.SATURDAY, BlockedDaysAndSlots.SATURDAY,// исключаем 4-ю пару в субботу
            DayOfWeek.SUNDAY, BlockedDaysAndSlots.SUNDAY // полностью исключаем воскресенье
    );

    /**
     * Проверяет отсутствие даты и ячейки в списке ограничений для сетки расписания
     *
     * @param date дата проведения занятия
     * @param slot слот для учебного занятия
     * @return boolean
     */
    public static boolean isSlotAvailable(LocalDate date, TimeSlotPair slot) {
        DayOfWeek checkedDay=DayOfWeek.fromJavaTimeDayOfWeek(date.getDayOfWeek());
        BlockedDaysAndSlots rule = DEFAULT_RULES.get(checkedDay);
        return rule == null || !rule.getExcludedSlots(date).contains(slot);
    }
}
