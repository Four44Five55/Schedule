package ru.services.solver;

import ru.entity.Auditorium;
import ru.entity.CellForLesson;
import ru.entity.Lesson;

import java.util.Collections;
import java.util.List;

public record PlacementOption(
        Lesson lessonToPlace,
        CellForLesson targetCell,
        List<Auditorium> assignedAuditoriums, // Какие конкретно аудитории были подобраны
        boolean isPossible,
        int score, // Оценка "качества" этого размещения (учитывает приоритеты)
        String failureReason
) {
    /**
     * Фабричный метод для создания успешной опции размещения.
     */
    public static PlacementOption available(Lesson lesson, CellForLesson cell, List<Auditorium> auditoriums, int score) {
        return new PlacementOption(lesson, cell, auditoriums, true, score, null);
    }

    /**
     * Фабричный метод для создания "неуспешной" опции.
     */
    public static PlacementOption unavailable(Lesson lesson, CellForLesson cell, String reason) {
        return new PlacementOption(lesson, cell, Collections.emptyList(), false, -9999, reason);
    }
}