package ru.entity.factories;

import ru.entity.CellForLesson;
import ru.enums.TimeSlotPair;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CellForLessonFactory {
    public static List<CellForLesson> createCellsForDateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "Начальная дата не может быть null");
        Objects.requireNonNull(endDate, "Конечная дата не может быть null");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Начальная дата не может быть позже конечной");
        }

        List<CellForLesson> cells = new ArrayList<>();

        // Перебираем все даты в диапазоне
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!(date.getDayOfWeek() == DayOfWeek.SUNDAY)){
                // Для каждой даты создаем объекты CellForLesson для каждого TimeSlotPair
                for (TimeSlotPair timeSlotPair : TimeSlotPair.values()) {
                    cells.add(new CellForLesson(date, timeSlotPair));
                }
            }

        }
        return cells;
    }
}
