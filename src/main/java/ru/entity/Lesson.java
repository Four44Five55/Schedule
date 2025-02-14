package ru.entity;

import org.example.abstr.AbstractLesson;

public class Lesson extends AbstractLesson {
    public Lesson(CellForLesson cellForLesson, Educator educator, Discipline discipline) {
        super(educator, discipline);
    }
}
