package ru.abstracts;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.CellForLesson;
import ru.entity.ConstraintsGrid;
import ru.entity.ScheduleGrid;
import ru.enums.KindOfConstraints;
import ru.inter.IMaterialEntity;

import java.time.LocalDate;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
public class AbstractMaterialEntity implements IMaterialEntity {
    protected Integer id;
    protected String name;
    protected ConstraintsGrid constraintsGrid = new ConstraintsGrid();

    public AbstractMaterialEntity(String name) {
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AbstractMaterialEntity that = (AbstractMaterialEntity) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(constraintsGrid, that.constraintsGrid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, constraintsGrid);
    }
}
