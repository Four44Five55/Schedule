package ru.abstracts;

import ru.entity.CellForLesson;
import ru.entity.ConstraintsGrid;
import ru.entity.ScheduleGrid;
import ru.enums.KindOfConstraints;
import ru.inter.IMaterialEntity;

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

    public boolean isFree(ScheduleGrid scheduleGrid,CellForLesson cell){
        return this.isFreeConstraintsGrid(cell)&&scheduleGrid.getLessonsUsingEntity(this,cell).isEmpty();
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
