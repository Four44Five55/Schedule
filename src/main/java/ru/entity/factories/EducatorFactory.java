package ru.entity.factories;

import ru.entity.Educator;

import java.util.ArrayList;
import java.util.List;

public class EducatorFactory {
    private static int nextId=1;
    public static <T extends Educator> List<T> createEducators(List<String> names, Class<T> educatorClass) {
        if (names == null || educatorClass == null) {
            throw new IllegalArgumentException("Список имен и класс преподавателя не могут быть null");
        }

        List<T> educators = new ArrayList<>();

        try {
            for (String name : names) {
                T educator = educatorClass.getDeclaredConstructor(int.class, String.class).newInstance(nextId++,name);
                educators.add(educator);
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании преподавателя", e);
        }
        return educators;
    }
}
