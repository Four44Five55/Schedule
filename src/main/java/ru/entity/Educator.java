package ru.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.abstracts.AbstractPerson;

@Getter
@NoArgsConstructor
public class Educator extends AbstractPerson {
    private final Priority priority = new Priority();

    public Educator(String name) {
        super(name);
    }

    public void addDefaultPriority() {
        priority.addDefaultDays();
        priority.addDefaultTimeSlots();
    }

    @Override
    public Integer getId() {
        return super.getId();
    }

    @Override
    public String getName() {
        return super.getName();
    }
}

