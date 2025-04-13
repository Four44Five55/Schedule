package ru.entity;

import ru.abstracts.AbstractLesson;
import ru.enums.KindOfStudy;

public class Lesson extends AbstractLesson {
    public Lesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator,GroupCombination groupCombinations) {
        super(discipline, kindOfStudy, educator,groupCombinations);
    }
}
