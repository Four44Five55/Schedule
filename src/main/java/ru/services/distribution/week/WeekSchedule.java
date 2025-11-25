package ru.services.distribution.week;

import ru.entity.CellForLesson;
import ru.services.solver.model.ScheduleGrid;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Класс для работы с расписанием одной недели
 */
public class WeekSchedule {
    public final LocalDate weekStart;
    public final LocalDate weekEnd;
    public final Map<LocalDate, List<CellForLesson>> dailySchedule;

    public WeekSchedule(LocalDate start, ScheduleGrid schedule) {
        this.weekStart = start;
        this.weekEnd = start.plusDays(6);
        this.dailySchedule = extractDailySchedule(schedule);
    }

    private Map<LocalDate, List<CellForLesson>> extractDailySchedule(ScheduleGrid schedule) {
        return schedule.getGridMap().entrySet().stream()
                .filter(entry -> isDateInWeek(entry.getKey().getDate()))
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getDate(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));
    }

    private boolean isDateInWeek(LocalDate date) {
        return !date.isBefore(weekStart) && !date.isAfter(weekEnd);
    }
} 