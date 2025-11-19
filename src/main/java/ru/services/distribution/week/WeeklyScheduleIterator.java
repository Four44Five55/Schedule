package ru.services.distribution.week;

import ru.services.solver.model.ScheduleGrid;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Итератор для последовательного обхода расписания по неделям
 */
public class WeeklyScheduleIterator implements Iterator<WeekSchedule> {
    private final LocalDate endDate;
    private LocalDate currentDate;
    private final ScheduleGrid schedule;

    public WeeklyScheduleIterator(LocalDate startDate, LocalDate endDate, ScheduleGrid schedule) {
        this.currentDate = startDate;
        this.endDate = endDate;
        this.schedule = schedule;
    }

    @Override
    public boolean hasNext() {
        return !currentDate.isAfter(endDate);
    }

    @Override
    public WeekSchedule next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        WeekSchedule week = new WeekSchedule(currentDate, schedule);
        currentDate = currentDate.plusWeeks(1);
        return week;
    }
} 