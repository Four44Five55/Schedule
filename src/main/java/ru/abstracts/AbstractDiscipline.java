package ru.abstracts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
abstract public class AbstractDiscipline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;
    @Column(name = "name")
    protected String name;
    @Column(name = "abbreviation")
    protected String abbreviation;
    @Column(name = "semester")
    protected Integer semester;

    public AbstractDiscipline() {
    }

    public AbstractDiscipline(String name, String abbreviation, Integer semester) {
        this.name = name;
        this.abbreviation = abbreviation;
        this.semester = semester;
    }

    @Override
    public String toString() {
        return abbreviation;
    }
}
