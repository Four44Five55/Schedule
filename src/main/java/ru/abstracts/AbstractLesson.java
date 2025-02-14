package ru.abstracts;

import org.example.entity.CellForLesson;
import org.example.entity.Discipline;
import org.example.entity.Educator;

abstract public class AbstractLesson {
    protected CellForLesson cellForLesson;
    protected Educator educator;
    protected Discipline discipline;

    public AbstractLesson(Educator educator, Discipline discipline) {
        this.educator = educator;
        this.discipline = discipline;
    }

    public void setCellForLesson(CellForLesson cellForLesson) {
        this.cellForLesson = cellForLesson;
    }

    public CellForLesson getCellForLesson() {
        return cellForLesson;
    }

    public Educator getEducator() {
        return educator;
    }

    public Discipline getDiscipline() {
        return discipline;
    }
}
