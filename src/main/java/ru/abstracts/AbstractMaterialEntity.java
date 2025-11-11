package ru.abstracts;

import ru.entity.CellForLesson;
import ru.entity.ConstraintsGrid;
import ru.entity.ScheduleGrid;
import ru.enums.KindOfConstraints;
import ru.inter.IMaterialEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;

public class AbstractMaterialEntity implements IMaterialEntity {
    protected int id;
    protected String name;
    protected ConstraintsGrid constraintsGrid = new ConstraintsGrid();

    public AbstractMaterialEntity() {
    }

    public AbstractMaterialEntity(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Проверяет нет ли ограничений на указанную ячейку
     *
     * @param cell временной слот для занятия
     * @return boolean
     */
    public boolean isFreeConstraintsGrid(CellForLesson cell) {
        return constraintsGrid.isFreeCell(cell);
    }


    /**
     * Проверка на наличие ограничения и на занятость в занятиях
     *
     * @param scheduleGrid расписание занятий
     * @param cell         проверяемая ячейка
     * @return boolean
     */
    public boolean isFree(ScheduleGrid scheduleGrid, CellForLesson cell) {
        return this.isFreeConstraintsGrid(cell) && scheduleGrid.getLessonsUsingEntity(this, cell).isEmpty();
    }

    /**
     * Проверяет наличие ограничения в указанной ячейке
     *
     * @return boolean
     */
    public boolean hasConstraint(CellForLesson cell) {
        return constraintsGrid.getConstraintsGridMap().containsKey(cell);
    }

    public KindOfConstraints getConstraint(CellForLesson cell) {
        return constraintsGrid.getConstraint(cell);
    }

    /**
     * Добавляет ограничения в карту с ограничениями в указанный диапазон
     *
     * @param startDate  начальная дата
     * @param endDate    последняя дата
     * @param constraint вид ограничения
     */
    public void addConstraint(LocalDate startDate, LocalDate endDate, KindOfConstraints constraint) {
        constraintsGrid.fillConstraintInRangeForGrid(startDate, endDate, constraint);
    }

    /**
     * Добавляет ограничения в карту с ограничениями в указанный диапазон
     *
     * @param startDate  начальная дата
     * @param endDate    последняя дата
     * @param constraint вид ограничения
     * @param dayOfWeek  день недели в котором определяется ограничение
     */
    public void addConstraint(LocalDate startDate, LocalDate endDate, KindOfConstraints constraint, DayOfWeek dayOfWeek) {
        constraintsGrid.fillConstraintInRangeForGrid(startDate, endDate, constraint, dayOfWeek);
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}
