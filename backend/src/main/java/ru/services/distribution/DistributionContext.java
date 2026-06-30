package ru.services.distribution;

import lombok.Getter;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Контекст, содержащий общее состояние для всех компонентов распределения.
 * Заменяет поля DistributionDiscipline, передаваемые между методами.
 */
@Getter
public class DistributionContext {
    private final ScheduleWorkspace workspace;
    private List<Lesson> lessons;
    private List<Educator> educators;
    private final List<Lesson> distributedLessons;
    private final Set<Lesson> distributedLessonsSet;

    // Закреплённые (пины, Фича 2): подмножество распределённых, которое распределителю
    // запрещено двигать/снимать. «Распределено» (фазы пропускают) ≠ «закреплено»
    // (мутаторам нельзя): сгенерированное распределено, но не закреплено.
    private final Set<Lesson> lockedLessons;

    // Маппинг занятия → целевая дата (рассчитывается в фазе 1 для равномерности)
    private Map<Lesson, LocalDate> lessonToDateMap;

    private DistributionContext(ScheduleWorkspace workspace,
                                List<Lesson> lessons,
                                List<Educator> educators) {
        this.workspace = workspace;
        this.lessons = new ArrayList<>(lessons);
        this.educators = new ArrayList<>(educators);
        this.distributedLessons = new ArrayList<>();
        this.distributedLessonsSet = new HashSet<>();
        this.lockedLessons = new HashSet<>();
        this.lessonToDateMap = new HashMap<>();
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
     * Обновляет список преподавателей (используется для сортировки по приоритету).
     */
    public void setEducators(List<Educator> educators) {
        this.educators = new ArrayList<>(educators);
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

    /**
     * Помечает занятие как засеянный пин: распределённое (фазы 1–2 пропустят) И
     * закреплённое (мутаторам, например {@code LocalSearchOptimizer}, трогать нельзя).
     * Вызывается оркестратором для каждого залоченного занятия перед фазами.
     */
    public void markPrePlacedLocked(Lesson lesson) {
        addDistributedLesson(lesson);
        lockedLessons.add(lesson);
    }

    /**
     * Закреплено ли занятие (пин) — нельзя двигать/снимать при оптимизации.
     */
    public boolean isLocked(Lesson lesson) {
        return lockedLessons.contains(lesson);
    }

    /**
     * Сохраняет маппинг занятий на даты (рассчитанный в фазе 1).
     */
    public void mergeIntoLessonToDateMap(Map<Lesson, LocalDate> map) {
        if (map != null) {
            this.lessonToDateMap.putAll(map);
        }
    }

    /**
     * Получает назначенную дату для занятия.
     */
    public LocalDate getDateForLesson(Lesson lesson) {
        return lessonToDateMap.get(lesson);
    }

    /**
     * Проверяет, что для занятия назначена дата.
     */
    public boolean hasDateForLesson(Lesson lesson) {
        return lessonToDateMap.containsKey(lesson);
    }
}
