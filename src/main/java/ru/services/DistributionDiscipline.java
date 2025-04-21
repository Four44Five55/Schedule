package ru.services;

import ru.abstracts.AbstractLesson;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;

import java.util.List;
import java.util.ListIterator;

public class DistributionDiscipline {
    ScheduleGrid scheduleGrid;
    List<Lesson> lessons;

    public DistributionDiscipline(ScheduleGrid scheduleGrid, List<Lesson> lessons) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;
    }

    private void distributeLessons() {
        ListIterator<Lesson> lessonIterator = lessons.listIterator();
        while (lessonIterator.hasNext()) {
            Lesson lesson = lessonIterator.next();


        }
    }


}
