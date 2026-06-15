package ru.services.distribution.strategy;

import lombok.extern.slf4j.Slf4j;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.LessonSortingService;
import ru.services.distribution.LessonDateFinder;

import java.time.LocalDate;
import java.util.List;

/**
 * Выбирает оптимальную стратегию слотов для преподавателя ДО начала распределения.
 *
 * Логика:
 * 1. Считаем сколько дней нужно для практик (через LessonSortingService)
 * 2. Считаем сколько дней доступно со стандартной стратегией
 * 3. Если не хватает — эскалируем стратегию
 */
@Slf4j
public class PracticeSlotStrategySelector {

    private final LessonDateFinder dateFinder;
    private final LessonSortingService lessonSortingService;

    public PracticeSlotStrategySelector(LessonDateFinder dateFinder,
                                        LessonSortingService lessonSortingService) {
        this.dateFinder = dateFinder;
        this.lessonSortingService = lessonSortingService;
    }

    public PracticeSlotStrategy selectFor(Educator educator,
                                          List<Lesson> practices,
                                          LocalDate semesterEnd) {
        if (practices.isEmpty()) return new StandardPracticeStrategy();

        // Сколько дней реально нужно для этих практик
        int required = lessonSortingService.calculateDaysForListLessons(practices);

        PracticeSlotStrategy strategy = new StandardPracticeStrategy();

        while (true) {
            int available = dateFinder.countAvailableDays(practices, semesterEnd,
                    strategy.getSkipPairs());

            log.info("  [стратегия '{}'] {}: нужно дней={}, доступно={}",
                    strategy.getName(), educator.getName(), required, available);

            if (available >= required) {
                return strategy;
            }

            PracticeSlotStrategy next = strategy.escalate();
            if (next == strategy) {
                log.warn("⚠ Потолок стратегий для {}: нужно={}, доступно={}",
                        educator.getName(), required, available);
                return strategy;
            }
            strategy = next;
        }
    }
}
