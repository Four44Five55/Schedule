package ru.entity;

import ru.abstracts.AbstractGrid;
import ru.entity.constraints.ConstraintKindRef;
import ru.services.factories.CellForLessonFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstraintsGrid extends AbstractGrid {
    private final Map<CellForLesson, ConstraintKindRef> constraintsGridMap = new HashMap<>();

    public ConstraintsGrid() {
        super();
    }

    public ConstraintsGrid(LocalDate startDate, LocalDate endDate) {
        super(startDate, endDate);
    }

    /**
     * Получает карту с ограничениями
     *
     * @return Map<CellForLesson, ConstraintKindRef>
     */
    public Map<CellForLesson, ConstraintKindRef> getConstraintsGridMap() {
        return constraintsGridMap;
    }

    /**
     * Получает карту ограничение
     *
     * @param cell ячейка
     * @return ConstraintKindRef
     */
    public ConstraintKindRef getConstraint(CellForLesson cell) {
        return constraintsGridMap.get(cell);
    }

    /**
     * Заполняет сетку ограничениями
     *
     * @param startDate  начальная дата
     * @param endDate    конечная дата
     * @param constraint тип ограничения
     */
    public void fillConstraintInRangeForGrid(LocalDate startDate, LocalDate endDate, ConstraintKindRef constraint) {
        List<CellForLesson> cellForLessons = CellForLessonFactory.getCellsInRange(startDate, endDate);
        for (CellForLesson cellForLesson : cellForLessons) {
            constraintsGridMap.put(cellForLesson, constraint);
        }
    }

    /**
     * Заполняет сетку ограничениями только для указанного дня недели
     *
     * @param startDate  начальная дата
     * @param endDate    конечная дата
     * @param constraint тип ограничения
     * @param dayOfWeek  день недели, для которого применяется ограничение
     */
    public void fillConstraintInRangeForGrid(LocalDate startDate, LocalDate endDate,
                                             ConstraintKindRef constraint, DayOfWeek dayOfWeek) {
        List<CellForLesson> cellForLessons = CellForLessonFactory.getCellsInRange(startDate, endDate);
        for (CellForLesson cellForLesson : cellForLessons) {
            if (cellForLesson.getDate().getDayOfWeek() == dayOfWeek) {
                constraintsGridMap.put(cellForLesson, constraint);
            }
        }
    }

    /**
     * Заполняет сетку ограничением в заданную дату
     *
     * @param date       дата ограничения
     * @param constraint тип ограничения
     */
    private void fillConstraintInDateForGrid(LocalDate date, ConstraintKindRef constraint) {
        List<CellForLesson> cellForLessons = CellForLessonFactory.getCellsForDate(date);
        for (CellForLesson cellForLesson : cellForLessons) {
            constraintsGridMap.put(cellForLesson, constraint);
        }
    }

    /**
     * Проверяет на пустоту ячейку
     *
     * @param cell проверяемая ячейка
     * @return boolean
     */
    public boolean isFreeCell(CellForLesson cell) {
        return !constraintsGridMap.containsKey(cell);
    }

}
