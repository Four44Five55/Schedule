package ru.abstracts;

abstract public class AbstractDiscipline {
    protected String name;

    public AbstractDiscipline() {
    }

    public AbstractDiscipline(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
