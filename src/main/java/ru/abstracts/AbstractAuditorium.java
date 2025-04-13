package ru.abstracts;

import java.util.Objects;

public class AbstractAuditorium extends AbstractMaterialEntity {
    private int capacity;

    public AbstractAuditorium(int capacity) {
        this.capacity = capacity;
    }

    public AbstractAuditorium(int id, String name, int capacity) {
        super(id, name);
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractAuditorium that = (AbstractAuditorium) o;
        return capacity == that.capacity && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, capacity);
    }
}
