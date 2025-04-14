package ru.entity;

import ru.abstracts.AbstractPerson;

public class Educator extends AbstractPerson {
    private Priority priority;
    private ConstraintsGrid constraintsGrid;

    public Educator() {
    }

    public Educator(int id, String name) {
        super(id, name);
    }

    public boolean isFree(CellForLesson cell) {
        return constraintsGrid.isFreeCell(cell);
    }

    public void addDefaultPriority() {
        priority.addDefaultDays();
        priority.addDefaultTimeSlots();
    }
}

