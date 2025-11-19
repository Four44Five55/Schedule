package ru.services.solver.model;

import ru.abstracts.AbstractGrid;
import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;

import java.time.LocalDate;
import java.util.*;

public class ScheduleGrid extends AbstractGrid {
    private final Map<CellForLesson, List<AbstractLesson>> scheduleGridMap = new HashMap<>();

    /**
     * Конструктор по умолчанию.
     * Использует startDate и endDate из интерфейса IGrid.
     */
    public ScheduleGrid() {
        super();
    }

    /**
     * Конструктор с явным указанием временного диапазона.
     *
     * @param startDate дата начала периода.
     * @param endDate   дата окончания периода.
     */
    public ScheduleGrid(LocalDate startDate, LocalDate endDate) {
        super(startDate, endDate);
    }

    /**
     * Получает неизменяемый список занятий в указанной ячейке.
     */
    public List<AbstractLesson> getLessonsIn(CellForLesson cell) {
        return scheduleGridMap.getOrDefault(cell, Collections.emptyList());
    }

    /**
     * Добавляет занятие в указанную ячейку.
     */
    public void add(CellForLesson cell, AbstractLesson lesson) {
        scheduleGridMap.computeIfAbsent(cell, k -> new ArrayList<>()).add(lesson);
    }

    /**
     * Удаляет занятие из указанной ячейки.
     */
    public void remove(CellForLesson cell, AbstractLesson lesson) {
        List<AbstractLesson> lessons = scheduleGridMap.get(cell);
        if (lessons != null) {
            lessons.remove(lesson);
            if (lessons.isEmpty()) {
                scheduleGridMap.remove(cell);
            }
        }
    }

    /**
     * Возвращает всю карту расписания для итерации или отладки.
     */
    public Map<CellForLesson, List<AbstractLesson>> getGridMap() {
        return Collections.unmodifiableMap(scheduleGridMap);
    }

}
