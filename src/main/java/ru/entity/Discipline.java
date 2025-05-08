package ru.entity;

import ru.abstracts.AbstractDiscipline;

public class Discipline extends AbstractDiscipline {
    public Discipline() {
    }

    public Discipline(int id, String name, String abbreviation) {
        super(id, name, abbreviation);
    }
}
