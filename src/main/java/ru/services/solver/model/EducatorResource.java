package ru.services.solver.model;

import ru.entity.Educator;
import ru.entity.Priority;
import ru.entity.CellForLesson;
import ru.enums.DayOfWeek;
/**
 * Специализированная "умная карточка" для ресурса "Преподаватель".
 * Добавляет логику учета мягких ограничений (предпочтений).
 */
public class EducatorResource extends SchedulableResource {

    // Поле для хранения предпочтений. Существует только у этого класса.
    private final Priority priority;

    public EducatorResource(Educator educator) {
        super(educator, educator.getId(), educator.getName());

        // Инициализируем объект Priority данными из JPA-сущности
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
     */
    @Override
    public int getPreferenceScore(CellForLesson cell) {
        int score = 0;

        // Конвертируем java.time.DayOfWeek в ваш кастомный enum
        DayOfWeek customDayOfWeek = DayOfWeek.fromJavaTimeDayOfWeek(cell.getDate().getDayOfWeek());

        // Проверяем, есть ли день в списке предпочтений
        if (!priority.getDayOfWeeks().contains(customDayOfWeek)) {
            score -= 10; // Условный штраф за работу в "нелюбимый" день
        }

        // Проверяем, есть ли пара в списке предпочтений
        if (!priority.getSlotPairs().contains(cell.getTimeSlotPair())) {
            score -= 5; // Условный штраф поменьше за "нелюбимую" пару
        }

        return score;
    }
}