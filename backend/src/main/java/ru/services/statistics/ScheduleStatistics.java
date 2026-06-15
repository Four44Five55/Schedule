package ru.services.statistics;

import ru.services.solver.model.ScheduleGrid;

/**
 * Интерфейс для работы со статистикой расписания
 * Single Responsibility Principle - отвечает только за статистику
 * Open/Closed Principle - можно расширять, создавая новые имплементации
 */
public interface ScheduleStatistics {
    /**
     * Обновляет статистику для указанной дисциплины
     */
    void updateStatistics(String disciplineName, ScheduleGrid originalSchedule);

    /**
     * Получает общее количество занятий для дисциплины
     */
    int getTotalLessons(String disciplineName);

    /**
     * Получает количество успешно добавленных занятий для дисциплины
     */
    int getAddedLessons(String disciplineName);

    /**
     * Выводит статистику в консоль
     */
    void printStatistics();

    /**
     * Регистрирует, что занятие для указанной дисциплины было добавлено
     * в результате перестановки.
     *
     * @param disciplineName Название дисциплины
     */
    void recordSwap(String disciplineName);
} 