package ru.abstracts;

import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;
import ru.inter.IScheduleGrid;

import java.util.Map;

abstract public class AEntityWithScheduleGrid {
    private final ScheduleGrid scheduleGrid;

    public AEntityWithScheduleGrid() {
        this.scheduleGrid = new ScheduleGrid(IScheduleGrid.START_DATE, IScheduleGrid.END_DATE);

    }

    public void addLessonScheduleGridMap(CellForLesson cellForLesson, AbstractLesson lesson) {
        scheduleGrid.getScheduleGridMap().put(cellForLesson,lesson);
    }

    public Map<CellForLesson, AbstractLesson> getScheduleGridMap() {
        return scheduleGrid.getScheduleGridMap();
    }

    public ScheduleGrid getScheduleGrid() {
        return scheduleGrid;
    }

}
