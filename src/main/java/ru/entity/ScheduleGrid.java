package ru.entity;

import ru.entity.factories.CellForLessonFactory;
import ru.inter.IScheduleGrid;
import ru.inter.IScheduleEntry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleGrid implements IScheduleGrid {
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Map<CellForLesson, List<IScheduleEntry>> scheduleGridMap = new HashMap<>();

    public ScheduleGrid() {
        this.startDate = START_DATE;
        this.endDate = END_DATE;
        this.fillBlankCellLessonForSchedule();
    }

    public ScheduleGrid(LocalDate startLocalDate, LocalDate endLocalDate) {
        this.startDate = startLocalDate;
        this.endDate = endLocalDate;
        this.fillBlankCellLessonForSchedule();
    }
/**
 * Заполняет расписание занятий днями(дата и пара)*/
    private void fillBlankCellLessonForSchedule() {
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellsForDateRange(startDate, endDate);
        for (CellForLesson cellForLesson : cellForLessons) {
            scheduleGridMap.put(cellForLesson, new ArrayList<>());
        }
    }

    public Map<CellForLesson, List<IScheduleEntry>> getScheduleGridMap() {
        return scheduleGridMap;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }
}
