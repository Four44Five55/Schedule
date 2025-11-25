package ru.services.solver.model;

import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Priority;
import ru.enums.DayOfWeek;

/**
 * Специализированная "умная карточка" для ресурса "Преподаватель".
 *
 * <p>Расширяет {@link SchedulableResource}, добавляя логику учета
 * мягких ограничений (предпочтений), хранящихся в объекте {@link Priority}.</p>
 */
public final class EducatorResource extends SchedulableResource {

    /**
     * Объект, инкапсулирующий предпочтения преподавателя по дням и парам.
     */
    private final Priority priority;

    /**
     * Создает ресурс для преподавателя, инициализируя его предпочтения.
     *
     * @param educator JPA-сущность преподавателя, из которой берутся данные.
     */
    public EducatorResource(Educator educator) {
        super(educator.getId(), educator.getName());

        this.priority = new Priority();
        if (educator.getPreferredDays() != null) {
            this.priority.addPriorityDays(educator.getPreferredDays());
        }
        if (educator.getPreferredTimeSlots() != null) {
            this.priority.addTimeSlots(educator.getPreferredTimeSlots());
        }
    }

    /**
     * Переопределенный метод. Оценивает слот с учетом предпочтений преподавателя.
     *
     * @param cell временной слот для оценки.
     * @return Отрицательное число (штраф), если слот не соответствует предпочтениям, иначе 0.
     */
    @Override
    public int getPreferenceScore(CellForLesson cell) {
        int score = 0;
        DayOfWeek customDayOfWeek = DayOfWeek.fromJavaTimeDayOfWeek(cell.getDate().getDayOfWeek());

        if (!priority.getDayOfWeeks().isEmpty() && !priority.getDayOfWeeks().contains(customDayOfWeek)) {
            score -= 10; // Условный штраф за нежелательный день
        }

        if (!priority.getSlotPairs().isEmpty() && !priority.getSlotPairs().contains(cell.getTimeSlotPair())) {
            score -= 5;  // Условный штраф за нежелательную пару
        }

        return score;
    }
}