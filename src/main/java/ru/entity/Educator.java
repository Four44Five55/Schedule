package ru.entity;

import lombok.Getter;
import ru.abstracts.AbstractPerson;
@Getter
public class Educator extends AbstractPerson {
    private final Priority priority = new Priority();

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

