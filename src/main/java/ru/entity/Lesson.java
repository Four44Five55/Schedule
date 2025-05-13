package ru.entity;

import lombok.NoArgsConstructor;
import ru.abstracts.AbstractLesson;
import ru.entity.logicSchema.CurriculumSlot;
import ru.enums.KindOfStudy;

import java.util.List;

@NoArgsConstructor
public class Lesson extends AbstractLesson {

    public Lesson(Discipline discipline, CurriculumSlot curriculumSlot, Educator educator, List<GroupCombination> groupCombinations) {
        super(discipline, curriculumSlot, educator, groupCombinations);
    }

    public Lesson(Discipline discipline, CurriculumSlot curriculumSlot, Educator educator, GroupCombination groupCombinations) {
        super(discipline, curriculumSlot, educator, groupCombinations);
    }

}
