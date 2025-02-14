package ru.entity;

import java.time.LocalDate;
import java.util.TreeMap;

public class ScheduleGrid {
    private static LocalDate startLocalDate;
    private static LocalDate endLocalDate;
    TreeMap<CellForLesson, Lesson> scheduleGrid = new TreeMap<>();

    public ScheduleGrid() {
    }

    public ScheduleGrid(LocalDate startLocalDate,LocalDate endLocalDate) {
        ScheduleGrid.startLocalDate=startLocalDate;
        ScheduleGrid.endLocalDate=endLocalDate;
    }

    public TreeMap<CellForLesson, Lesson> getScheduleGrid() {
        return scheduleGrid;
    }

    public static LocalDate getStartLocalDate() {
        return startLocalDate;
    }

    public static LocalDate getEndLocalDate() {
        return endLocalDate;
    }
}
