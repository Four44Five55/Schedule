package ru.entity;

import ru.abstracts.AbstractLesson;
import ru.enums.KindOfStudy;

import java.util.List;

public class Lesson extends AbstractLesson {
    public Lesson() {

    }

    public Lesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator, List<GroupCombination> groupCombinations) {
        super(discipline, kindOfStudy, educator,groupCombinations);
    }

    public Lesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator, GroupCombination groupCombinations) {
        super(discipline, kindOfStudy, educator, groupCombinations);
    }
}
