package ru.entity;

import ru.abstracts.AbstractAuditorium;

public class Auditorium extends AbstractAuditorium {
    public Auditorium(int capacity) {
        super(capacity);
    }

    public Auditorium(int id, String name, int capacity) {
        super(id, name, capacity);
    }
}
