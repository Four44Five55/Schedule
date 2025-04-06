package ru.entity;

import ru.abstracts.AEntityWithScheduleGrid;
import ru.abstracts.AbstractAuditorium;
import ru.abstracts.AbstractLesson;

import java.util.Objects;

public class Group extends AEntityWithScheduleGrid {

    private String name;
    private int size;
    private AbstractAuditorium auditorium;

    public Group() {
    }

    public Group(String name, int size, AbstractAuditorium auditorium) {
        super();
        this.name = name;
        this.size = size;
        this.auditorium = auditorium;

    }

    @Override
    public void addLessonScheduleGridMap(CellForLesson cellForLesson, AbstractLesson lesson) {
        this.getScheduleGridMap().put(cellForLesson, lesson);
        //lesson.addGroup(this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
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
}
