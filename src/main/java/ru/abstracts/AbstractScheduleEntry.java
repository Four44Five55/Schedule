package ru.abstracts;

import ru.entity.Auditorium;
import ru.entity.Educator;
import ru.entity.GroupCombination;
import ru.inter.IScheduleEntry;

import java.util.List;

public class AbstractScheduleEntry implements IScheduleEntry {
    private final AbstractLesson lesson;
    private final Educator educator;
    private final List<GroupCombination> targetGroups;
    private final Auditorium auditorium;


    public AbstractScheduleEntry(AbstractLesson lesson, Educator educator, List<GroupCombination> groupCombinationList, Auditorium auditorium) {
        this.lesson = lesson;
        this.educator = educator;
        this.targetGroups = groupCombinationList;
        this.auditorium = auditorium;
    }
}
