package ru.services.solver.model;

import lombok.Getter;
import ru.entity.CellForLesson;
import ru.entity.ConstraintsGrid;
import ru.entity.Lesson;
import ru.enums.KindOfConstraints;

import java.util.HashMap;
import java.util.Map;

/**
 * Базовый класс для любого ресурса, участвующего в расписании (преподаватель, группа, аудитория).
 * Хранит "в памяти" сетку занятости и постоянных ограничений для быстрого доступа.
 */
public class SchedulableResource {

    protected final Object underlyingEntity; // Ссылка на исходную JPA-сущность (Group, Auditorium и т.д.)
    @Getter
    protected final Integer id;
    @Getter
    protected final String name;

    // Личное расписание ресурса: "когда" -> "чем" занят
    protected final Map<CellForLesson, Lesson> schedule = new HashMap<>();

    // Постоянные ограничения ресурса: "когда" недоступен в принципе
    @lombok.Getter
    protected final ConstraintsGrid hardConstraints;

    /**
     * Конструктор для создания ресурса.
     *
     * @param entity          JPA-сущность (например, Group или Auditorium)
     * @param id              ID сущности
     * @param name            Имя сущности для отладки
     * @param hardConstraints Ограничения
     */
    public SchedulableResource(Object entity, Integer id, String name, ConstraintsGrid hardConstraints) {
        this.underlyingEntity = entity;
        this.id = id;
        this.name = name;
        this.hardConstraints = hardConstraints;
    }

    /**
     * Проверяет, свободен ли ресурс в указанной ячейке.
     *
     * @param cell Временной слот для проверки.
     * @return true, если ресурс полностью свободен.
     */
    public boolean isFree(CellForLesson cell) {
        // Проверяем и постоянные ограничения, и динамическое расписание
        return hardConstraints.isFreeCell(cell) && !schedule.containsKey(cell);
    }

    /**
     * Возвращает причину недоступности, если ресурс занят.
     *
     * @param cell Временной слот.
     * @return Строку с описанием причины или null, если свободен.
     */
    public String getBusyReason(CellForLesson cell) {
        if (!hardConstraints.isFreeCell(cell)) {
            KindOfConstraints reason = hardConstraints.getConstraint(cell);
            return "Постоянное ограничение: " + reason.getFullName();
        }
        if (schedule.containsKey(cell)) {
            Lesson lesson = schedule.get(cell);
            return "Занят в занятии: " + lesson.getDiscipline().getAbbreviation();
        }
        return null; // Свободен
    }

    // Методы occupy() и free() остаются без изменений
    public void occupy(CellForLesson cell, Lesson lesson) {
        schedule.put(cell, lesson);
    }

    public void free(CellForLesson cell) {
        schedule.remove(cell);
    }

    /**
     * Оценивает, насколько слот "предпочтителен" для данного ресурса.
     * Для базовых ресурсов (группы, аудитории) предпочтений нет, поэтому штраф всегда 0.
     *
     * @param cell Временной слот для оценки.
     * @return Целочисленное значение "штрафа". 0 - идеально, отрицательное значение - плохо.
     */
    public int getPreferenceScore(CellForLesson cell) {
        return 0;
    }

}
