package ru.entity;

import ru.abstracts.AbstractPerson;
import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public class Educator extends AbstractPerson {
    private final Priority priority=new Priority();
    public Educator() {
    }

    public Educator(int id, String name) {
        super(id, name);
    }

    public void addDefaultPriority() {
        priority.addDefaultDays();
        priority.addDefaultTimeSlots();
    }


}

