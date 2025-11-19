package ru.services.statistics;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.services.solver.model.ScheduleGrid;

import java.util.*;

/**
 * Реализация статистики расписания по умолчанию с учетом перестановок.
 */
public class DefaultScheduleStatistics implements ScheduleStatistics {
    // Хранит общее количество запланированных занятий
    private final Map<String, Integer> totalLessons = new HashMap<>();
    // Хранит количество занятий, добавленных НАПРЯМУЮ (в свой исходный слот)
    private final Map<String, Integer> directlyAddedLessons = new HashMap<>();
    // Хранит количество занятий, добавленных через ПЕРЕСТАНОВКУ
    private final Map<String, Integer> swappedLessons = new HashMap<>();

    private final ScheduleGrid unifiedSchedule;
    private final Map<String, ScheduleGrid> disciplineSchedules;

    public DefaultScheduleStatistics(ScheduleGrid unifiedSchedule, Map<String, ScheduleGrid> disciplineSchedules) {
        this.unifiedSchedule = unifiedSchedule;
        this.disciplineSchedules = disciplineSchedules;
    }

    /**
     * Регистрирует, что для дисциплины было добавлено занятие через перестановку.
     * @param disciplineName Название дисциплины, чье занятие было добавлено.
     */
    @Override
    public void recordSwap(String disciplineName) {
        swappedLessons.merge(disciplineName, 1, Integer::sum);
    }

    @Override
    public void updateStatistics(String disciplineName, ScheduleGrid originalSchedule) {
        // 1. Считаем общее количество размещений занятий (исправленная логика)
        totalLessons.put(disciplineName, countTotalLessonPlacements(originalSchedule));

        // 2. Считаем количество занятий, добавленных напрямую в исходные ячейки
        int addedDirectly = countDirectlyAddedLessons(originalSchedule);
        directlyAddedLessons.put(disciplineName, addedDirectly);
    }

    /**
     * Возвращает ОБЩЕЕ количество добавленных занятий (напрямую + через перестановку).
     */
    @Override
    public int getAddedLessons(String disciplineName) {
        int directly = directlyAddedLessons.getOrDefault(disciplineName, 0);
        int swapped = swappedLessons.getOrDefault(disciplineName, 0);
        return directly + swapped;
    }

    @Override
    public int getTotalLessons(String disciplineName) {
        return totalLessons.getOrDefault(disciplineName, 0);
    }

    /**
     * Исправленный метод: считает не уникальные объекты, а все размещения.
     */
    private int countTotalLessonPlacements(ScheduleGrid originalSchedule) {
        return (int) originalSchedule.getScheduleGridMap().values().stream()
                .mapToLong(List::size)
                .sum();
    }

    /**
     * Новый метод: считает только те занятия, которые оказались в итоговом расписании
     * в той же самой ячейке, что и в исходном.
     */
    private int countDirectlyAddedLessons(ScheduleGrid originalSchedule) {
        long count = 0;
        for (Map.Entry<CellForLesson, List<AbstractLesson>> entry : originalSchedule.getScheduleGridMap().entrySet()) {
            CellForLesson originalCell = entry.getKey();
            List<AbstractLesson> originalLessonsInCell = entry.getValue();

            // Получаем список занятий в этой же ячейке в итоговом расписании
            List<AbstractLesson> unifiedLessonsInCell = unifiedSchedule.getListLessonInCell(originalCell);

            // Считаем, сколько занятий из исходного списка присутствует в итоговом для ДАННОЙ ячейки
            count += originalLessonsInCell.stream()
                    .filter(unifiedLessonsInCell::contains)
                    .count();
        }
        return (int) count;
    }

    @Override
    public void printStatistics() {
        System.out.println("\n=== Статистика распределения занятий ===");
        int totalSum = 0;
        int addedSum = 0;
        int swappedSum = 0;
        int directlyAddedSum = 0;

        List<String> disciplineNames = new ArrayList<>(disciplineSchedules.keySet());
        Collections.sort(disciplineNames); // Сортируем для стабильного вывода

        for (String disciplineName : disciplineNames) {
            int total = getTotalLessons(disciplineName);
            if (total == 0) continue; // Не выводим дисциплины без занятий

            int swapped = swappedLessons.getOrDefault(disciplineName, 0);
            int directlyAdded = directlyAddedLessons.getOrDefault(disciplineName, 0);
            int totalAdded = swapped + directlyAdded; // Общее число добавленных
            int notAdded = total - totalAdded;

            double percent = (totalAdded * 100.0) / total;

            totalSum += total;
            addedSum += totalAdded;
            swappedSum += swapped;
            directlyAddedSum += directlyAdded;

            System.out.printf("%s:\n", disciplineName);
            System.out.printf("  - Всего запланировано: %d\n", total);
            System.out.printf("  - Успешно добавлено: %d (%.2f%%)\n", totalAdded, percent);
            System.out.printf("    - Напрямую: %d\n", directlyAdded);
            System.out.printf("    - Через перестановку: %d\n", swapped);
            System.out.printf("  - Не добавлено: %d\n", notAdded);
        }

        double totalPercent = totalSum > 0 ? (addedSum * 100.0) / totalSum : 0;
        System.out.println("\n--- Общая статистика ---");
        System.out.printf("Всего запланировано: %d\n", totalSum);
        System.out.printf("Успешно добавлено: %d (%.2f%%)\n", addedSum, totalPercent);
        System.out.printf("  - Напрямую: %d\n", directlyAddedSum);
        System.out.printf("  - Через перестановку: %d\n", swappedSum);
        System.out.printf("Не добавлено: %d\n", totalSum - addedSum);
        System.out.println("=====================================\n");
    }
}