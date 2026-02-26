package ru.services.distribution.core;

import lombok.Getter;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.solver.ScheduleWorkspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Контекст, содержащий общее состояние для всех компонентов распределения.
 * Заменяет поля DistributionDiscipline, передаваемые между методами.
 */
@Getter
public class DistributionContext {
    private final ScheduleWorkspace workspace;
    private List<Lesson> lessons;
    private final List<Educator> educators;
    private final List<Lesson> distributedLessons;
    private final Set<Lesson> distributedLessonsSet; // для быстрой проверки O(1)

    private DistributionContext(ScheduleWorkspace workspace,
                                List<Lesson> lessons,
                                List<Educator> educators) {
        this.workspace = workspace;
        this.lessons = new ArrayList<>(lessons);
        this.educators = new ArrayList<>(educators);
        this.distributedLessons = new ArrayList<>();
        this.distributedLessonsSet = new HashSet<>();
    }

    /**
     * Factory-метод для создания контекста.
     */
    public static DistributionContext of(ScheduleWorkspace workspace,
                                         List<Lesson> lessons,
                                         List<Educator> educators) {
        return new DistributionContext(workspace, lessons, educators);
    }

    /**
     * Обновляет список занятий (используется для фильтрации экзаменов).
     */
    public void setLessons(List<Lesson> lessons) {
        this.lessons = new ArrayList<>(lessons);
    }

    /**
     * Добавляет занятие в список распределённых.
     */
    public void addDistributedLesson(Lesson lesson) {
        this.distributedLessons.add(lesson);
        this.distributedLessonsSet.add(lesson);
    }

    /**
     * Удаляет занятие из списка распределённых.
     */
    public void removeDistributedLesson(Lesson lesson) {
        this.distributedLessons.remove(lesson);
        this.distributedLessonsSet.remove(lesson);
    }

    /**
     * Проверяет, занятие уже распределено.
     */
    public boolean isLessonDistributed(Lesson lesson) {
        return distributedLessonsSet.contains(lesson);
    }
}
