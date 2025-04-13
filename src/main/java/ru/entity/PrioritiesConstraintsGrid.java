package ru.entity;

import ru.abstracts.AbstractGrid;
import ru.entity.factories.CellForLessonFactory;
import ru.enums.KindOfConstraints;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO разделить на два класса приоритеты и ограничения
public class PrioritiesConstraintsGrid extends AbstractGrid {
    private final Map<CellForLesson, KindOfConstraints> constraintsGridMap = new HashMap<>();

    public PrioritiesConstraintsGrid() {
        super();
    }

    public PrioritiesConstraintsGrid(LocalDate startDate, LocalDate endDate) {
        super(startDate, endDate);
    }

    /**
     * Заполняет сетку ограничениями
     *
     * @param startDate  начальная дата
     * @param endDate    конечная дата
     * @param constraint тип ограничения
     */
    private void fillConstraintInRangeForGrid(LocalDate startDate, LocalDate endDate, KindOfConstraints constraint) {
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellsForDateRange(startDate, endDate);
        for (CellForLesson cellForLesson : cellForLessons) {
            constraintsGridMap.put(cellForLesson, constraint);
        }
    }

    /**
     * Заполняет сетку ограничением в заданную дату
     *
     * @param date       дата ограничения
     * @param constraint тип ограничения
     */
    private void fillConstraintInDateForGrid(LocalDate date, KindOfConstraints constraint) {
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellForDate(date);
        for (CellForLesson cellForLesson : cellForLessons) {
            constraintsGridMap.put(cellForLesson, constraint);
        }
    }

}
