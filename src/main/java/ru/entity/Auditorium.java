package ru.entity;

import ru.abstracts.AbstractAuditorium;

public class Auditorium extends AbstractAuditorium {
    public Auditorium(int capacity) {
        super(capacity);
    }

    public Auditorium(String name, int capacity) {
        super(name, capacity);
    }


}
