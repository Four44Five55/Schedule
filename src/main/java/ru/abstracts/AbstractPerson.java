package ru.abstracts;

import ru.inter.Person;

import java.util.Objects;

abstract public class AbstractPerson extends AbstractMaterialEntity  implements Person {

    public AbstractPerson() {
    }

    public AbstractPerson(int id, String name) {
        super(id, name);
    }

    @Override
    public String toString() {
        return
                "id: " + id + " " + name + '\'';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractPerson that = (AbstractPerson) o;
        return id == that.id && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}

