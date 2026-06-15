package ru.services;

import ru.entity.CellForLesson;

import java.time.LocalDate;
import java.util.Comparator;

public class CellForLessonComparator implements Comparator<CellForLesson> {
    @Override
    public int compare(CellForLesson o1, CellForLesson o2) {
        // Сначала сравниваем даты
        int dateComparison = o1.getDate().compareTo(o2.getDate());
        if (dateComparison != 0) {
            return dateComparison;
        }
        // Если даты равны, сравниваем временные слоты
        return o1.getTimeSlotPair().compareTo(o2.getTimeSlotPair());
    }
}