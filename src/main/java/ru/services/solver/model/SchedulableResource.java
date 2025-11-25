package ru.services.solver.model;

import lombok.Getter;
import ru.entity.CellForLesson;
import ru.entity.ConstraintsGrid;
import ru.entity.Lesson;
import ru.enums.KindOfConstraints;

import java.util.HashMap;
import java.util.Map;

/**
 * Базовый класс для любого ресурса, участвующего в расписании (например, группа или аудитория).
 *
 * <p>Этот объект создается "в памяти" на время работы алгоритма и не является JPA-сущностью.
 * Его основная задача — инкапсулировать состояние занятости ресурса и предоставлять
 * быстрые O(1) проверки доступности. Он содержит личное расписание (динамические ограничения)
 * и сетку постоянных ограничений (статические ограничения).</p>
 */
public class SchedulableResource {

    @Getter
    protected final Integer id;
    protected final String name;

    /**
     * Личное расписание ресурса: "когда" -> "чем" занят. Заполняется динамически.
     */
    protected final Map<CellForLesson, Lesson> schedule = new HashMap<>();

    /**
     * Постоянные ограничения ресурса (отпуска, ремонт и т.д.). Заполняется один раз при инициализации.
     */
    protected final ConstraintsGrid hardConstraints;

    /**
     * Создает "умную карточку" для ресурса.
     *
     * @param id   уникальный идентификатор ресурса (из JPA-сущности).
     * @param name имя ресурса для отладки и логов.
     */
    public SchedulableResource(Integer id, String name) {
        this.id = id;
        this.name = name;
        this.hardConstraints = new ConstraintsGrid(); // Инициализируем пустой сеткой
    }

    /**
     * Добавляет постоянное ограничение для ресурса.
     * Этот метод вызывается извне (например, из ResourceAvailabilityManager) для наполнения сетки ограничений.
     *
     * @param cell ячейка времени, на которую действует ограничение.
     * @param kind тип ограничения.
     */
    public void addHardConstraint(CellForLesson cell, KindOfConstraints kind) {
        this.hardConstraints.getConstraintsGridMap().put(cell, kind);
    }

    /**
     * Проверяет, свободен ли ресурс в указанном временном слоте.
     * Учитывает как постоянные ограничения, так и текущую занятость в расписании.
     *
     * @param cell временной слот для проверки.
     * @return {@code true}, если ресурс полностью свободен, иначе {@code false}.
     */
    public boolean isFree(CellForLesson cell) {
        return hardConstraints.isFreeCell(cell) && !schedule.containsKey(cell);
    }

    /**
     * Помечает ресурс как занятый в указанном слоте.
     * Должен вызываться только после успешной проверки {@link #isFree(CellForLesson)}.
     *
     * @param cell   слот, который занимается.
     * @param lesson занятие, которое занимает слот.
     */
    public void occupy(CellForLesson cell, Lesson lesson) {
        schedule.put(cell, lesson);
    }

    /**
     * Освобождает ресурс в указанном слоте.
     *
     * @param cell слот, который освобождается.
     */
    public void free(CellForLesson cell) {
        schedule.remove(cell);
    }

    /**
     * Оценивает, насколько слот "предпочтителен" для данного ресурса (мягкое ограничение).
     * Для базовых ресурсов предпочтений нет, поэтому возвращается 0.
     *
     * @param cell временной слот для оценки.
     * @return Целочисленное значение "штрафа". 0 - идеально, отрицательное значение - плохо.
     */
    public int getPreferenceScore(CellForLesson cell) {
        return 0;
    }

    public String getName() {
        return name;
    }

}
