package ru.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import ru.abstracts.AbstractDiscipline;

@Entity
@NoArgsConstructor
@Table(name = "discipline")
public class Discipline extends AbstractDiscipline {

    public Discipline(String name, String abbreviation) {
        super(name, abbreviation);
    }

}
