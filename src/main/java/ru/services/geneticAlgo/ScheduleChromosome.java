package ru.services.geneticAlgo;

import ru.entity.*;
import ru.abstracts.AbstractLesson;
import java.util.*;

public class ScheduleChromosome {
    private ScheduleGrid scheduleGrid;
    private double fitness;

    public ScheduleChromosome(ScheduleGrid grid) {
        this.scheduleGrid = grid;
        this.fitness = -1; // Инициализация не вычисленным значением
    }

    public ScheduleGrid getScheduleGrid() {
        return scheduleGrid;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    // Копирование расписания для операторов ГА
    public ScheduleChromosome deepCopy() {
        ScheduleGrid newGrid = new ScheduleGrid(this.scheduleGrid.getStartDate(), this.scheduleGrid.getEndDate());
        for (CellForLesson cell : this.scheduleGrid.getScheduleGridMap().keySet()) {
            newGrid.getScheduleGridMap().get(cell).addAll(this.scheduleGrid.getListLessonInCell(cell));
        }
        return new ScheduleChromosome(newGrid);
    }
}