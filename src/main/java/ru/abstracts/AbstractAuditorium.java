package ru.abstracts;

import java.util.Objects;

public class AbstractAuditorium extends AEntityWithScheduleGrid{
    private String name;
    private int capacity;

    public AbstractAuditorium() {
    }

    public AbstractAuditorium(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
