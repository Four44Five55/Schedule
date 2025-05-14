package ru.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ru.abstracts.AbstractDiscipline;
@Entity
@Table(name = "discipline")
public class Discipline extends AbstractDiscipline {
    public Discipline() {
    }

    public Discipline(String name, String abbreviation) {
        super(name, abbreviation);
    }
}
