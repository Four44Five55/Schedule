package ru.entity;

import ru.abstracts.AbstractGrid;
import ru.abstracts.AbstractLesson;
import ru.entity.factories.CellForLessonFactory;
import ru.inter.IGrid;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleGrid extends AbstractGrid {
    private final Map<CellForLesson, List<AbstractLesson>> scheduleGridMap = new HashMap<>();

    public ScheduleGrid() {
        super();
        fillBlankCellLessonForSchedule();
    }

    public ScheduleGrid(LocalDate startDate, LocalDate endDate) {
        super(startDate, endDate);
        fillBlankCellLessonForSchedule();
    }

    /**
     * Заполняет расписание занятий днями(дата и пара)
     */
    private void fillBlankCellLessonForSchedule() {
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellsForDateRange(this.getStartDate(), this.getEndDate());
        for (CellForLesson cellForLesson : cellForLessons) {
            scheduleGridMap.put(cellForLesson, new ArrayList<>());
        }
    }

    public Map<CellForLesson, List<AbstractLesson>> getScheduleGridMap() {
        return scheduleGridMap;
    }

    public List<AbstractLesson> getListLessonInCell(CellForLesson cell) {
        return scheduleGridMap.get(cell);
    }

}
