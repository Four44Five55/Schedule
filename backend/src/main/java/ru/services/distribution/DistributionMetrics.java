package ru.services.distribution;

/**
 * Утилитный класс для метрик распределения.
 * Упрощённая версия.
 */
public class DistributionMetrics {
    private final DistributionContext context;

    public DistributionMetrics(DistributionContext context) {
        this.context = context;
    }

    /**
     * Возвращает количество размещённых занятий.
     */
    public int countPlaced() {
        return context.getDistributedLessons().size();
    }

    /**
     * Возвращает количество неразмещённых занятий.
     */
    public int countRemaining() {
        return context.getLessons().size() - countPlaced();
    }

    /**
     * Возвращает процент размещения.
     */
    public double getPlacementPercent() {
        int total = context.getLessons().size();
        if (total == 0) return 100.0;
        return (countPlaced() * 100.0) / total;
    }
}
