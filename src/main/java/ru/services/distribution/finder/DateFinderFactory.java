package ru.services.distribution.finder;

import ru.entity.Educator;
import ru.services.distribution.DistributionContext;

/**
 * Фабрика для выбора стратегии поиска дат.
 */
public class DateFinderFactory {

    /**
     * Создаёт стратегию поиска дат на основе настроек преподавателя.
     *
     * @param educator преподаватель
     * @param context  контекст распределения
     * @return стратегия поиска дат
     */
    public static DateFinder createFinder(Educator educator, DistributionContext context) {
        if (educator != null && educator.isCompactSchedule()) {
            return new SlidingWindowDateFinder(context);
        } else {
            return new SlidingWindowDateFinder(context);
        }
    }
}
