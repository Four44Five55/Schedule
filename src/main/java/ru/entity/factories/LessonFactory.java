package ru.entity.factories;

import ru.abstracts.AbstractLesson;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.GroupCombination;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.enums.KindOfStudy;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LessonFactory {



    public static <T extends AbstractLesson> List<T> createLessonsDiscipline(
            Discipline discipline,
            Class<T> lessonClass,
            DisciplineCurriculum disciplineCurriculum,
            List<GroupCombination> groupCombinations,
            Educator lecturer,
            Map<GroupCombination, Educator> groupCombinationEducatorMap) {
        List<T> logicStudySchemaDiscipline = new ArrayList<>();
        try {
            for (CurriculumSlot curriculumSlot : disciplineCurriculum.getCurriculumSlots()) {
                if (curriculumSlot.getKindOfStudy() == KindOfStudy.LECTURE) {
                    Constructor<T> constructor = lessonClass.getDeclaredConstructor(Discipline.class, CurriculumSlot.class, Educator.class, List.class);
                    //GroupCombination allGroupsCombination = groupCombinations.keySet().iterator().next();
                    T lesson = constructor.newInstance(discipline, curriculumSlot, lecturer, groupCombinations);
                    logicStudySchemaDiscipline.add(lesson);
                } else {
                    // Для других видов занятий создаем занятие для каждой комбинации групп
                    for (GroupCombination groupCombination : groupCombinations) {
                        Constructor<T> constructor = lessonClass.getDeclaredConstructor(Discipline.class, CurriculumSlot.class, Educator.class, GroupCombination.class);
                        Educator educator = groupCombinationEducatorMap.get(groupCombination);
                        T lesson = constructor.newInstance(discipline, curriculumSlot, educator, groupCombination);
                        logicStudySchemaDiscipline.add(lesson);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании дисциплины", e);
        }
        return logicStudySchemaDiscipline;
    }



}
