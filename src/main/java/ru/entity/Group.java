package ru.entity;

import ru.abstracts.AbstractAuditorium;
import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractMaterialEntity;

import java.util.Objects;

public class Group extends AbstractMaterialEntity {

    private int size;
    private AbstractAuditorium auditorium;

    public Group(int size, AbstractAuditorium auditorium) {
        this.size = size;
        this.auditorium = auditorium;
    }

    public Group(int id, String name, int size, AbstractAuditorium auditorium) {
        super(id, name);
        this.size = size;
        this.auditorium = auditorium;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public AbstractAuditorium getAuditorium() {
        return auditorium;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return size == group.size && Objects.equals(name, group.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, size);
    }

    @Override
    public String toString() {
        return name;
    }
}
