package ru.utils;

import ru.entity.CellForLesson;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

// Вспомогательный класс для группировки по году и неделе
public final class YearWeek implements Comparable<YearWeek> {
    private final int year;
    private final int week;
    private final LocalDate firstDay;

    public YearWeek(LocalDate date) {
        this.year = date.get(WeekFields.ISO.weekBasedYear());
        this.week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        this.firstDay = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Возвращает хэшмапу с разделением ячеек по календарным неделям
     *
     * @param cells список ячеек
     * @return HashMap
     */
    public static Map<YearWeek, List<CellForLesson>> getWeekMap(List<CellForLesson> cells) {
        // Группируем и сортируем ячейки по дате (не по номеру недели)
        return cells.stream()
                .collect(Collectors.groupingBy(
                        cell -> new YearWeek(cell.getDate()),
                        TreeMap::new, // автоматически сортирует по ключу
                        Collectors.toList()));

    }

    @Override
    public int compareTo(YearWeek other) {
        return this.firstDay.compareTo(other.firstDay);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YearWeek yearWeek = (YearWeek) o;
        return year == yearWeek.year && week == yearWeek.week;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, week);
    }
}