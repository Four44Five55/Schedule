package ru.entity;

import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractScheduleEntry;

import java.util.List;

public class ScheduleEntry extends AbstractScheduleEntry {

    public ScheduleEntry(AbstractLesson lesson, Educator educator, List<GroupCombination> groupCombinationList, Auditorium auditorium) {
        super(lesson, educator, groupCombinationList, auditorium);
    }
}
