package ru.entity.factories;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.entity.Discipline;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class LessonFactory {



    public static <T extends AbstractLesson> List<T> createLogicStudySchemaDiscipline(Discipline discipline, Class<T> lessonClass, List<KindOfStudy> kindOfStudies,
                                                                                      Educator educator){
        List<T> logicStudySchemaDiscipline = new ArrayList<>();
        try {
                      for (KindOfStudy kindOfStudy:kindOfStudies){
                        Constructor<T> constructor = lessonClass.getDeclaredConstructor(Discipline.class, KindOfStudy.class, Educator.class);
                        T lesson = constructor.newInstance(discipline, kindOfStudy, educator);
                        logicStudySchemaDiscipline.add(lesson);
                    }

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании дисциплины", e);
        }
        return logicStudySchemaDiscipline;
    }
    public static <T extends AbstractLesson> List<T> createLessons(List<Discipline> disciplinesNames, Class<T> lessonClass, KindOfStudy kindOfStudy,
                                                                   List<Educator> educatorsNames) {
        List<T> lessons = new ArrayList<>();
        try {
            for (Discipline discipline : disciplinesNames) {
                for (Educator educator : educatorsNames) {
                    Constructor<T> constructor = lessonClass.getDeclaredConstructor(Discipline.class, KindOfStudy.class, Educator.class);
                    T lesson = constructor.newInstance(discipline, kindOfStudy, educator);
                    lessons.add(lesson);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании дисциплины", e);
        }
        return lessons;
    }

    public static Lesson createLesson(Discipline discipline, KindOfStudy kindOfStudy, Educator educator) {
        try {
            Constructor<Lesson> constructor = Lesson.class.getDeclaredConstructor(
                    Discipline.class, KindOfStudy.class, Educator.class);
            return constructor.newInstance(discipline, kindOfStudy, educator);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании занятия", e);
        }
    }
}
