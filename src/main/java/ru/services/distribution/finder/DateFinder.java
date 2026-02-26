package ru.services.distribution.finder;

import ru.entity.Educator;
import ru.entity.Lesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Стратегия поиска даты для размещения занятия.
 */
public interface DateFinder {
    /**
     * Находит доступную дату для размещения практики/цепочки.
     *
     * @param lesson        занятие для размещения
     * @param minDate       минимально допустимая дата
     * @param allDates      все доступные даты
     * @param educator      преподаватель
     * @param lectureDates  даты с лекциями (приоритетные)
     * @param semesterEnd   конец семестра
     * @return найденная дата или null
     */
    LocalDate findDate(Lesson lesson, LocalDate minDate, List<LocalDate> allDates,
                      Educator educator, Set<LocalDate> lectureDates, LocalDate semesterEnd);
}
