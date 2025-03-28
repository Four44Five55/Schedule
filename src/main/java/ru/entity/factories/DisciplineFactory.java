package ru.entity.factories;

import ru.abstracts.AbstractDiscipline;

import java.util.ArrayList;
import java.util.List;

public class DisciplineFactory {
    public static <T extends AbstractDiscipline> List<T> createDisciplines(List<String> names, Class<T> disciplineClass) {
        List<T> disciplines = new ArrayList<>();
        try {
            for (String name : names) {
                T discipline = disciplineClass.getDeclaredConstructor(String.class).newInstance(name);
                disciplines.add(discipline);
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании дисциплин", e);
        }
        return disciplines;
    }
}
