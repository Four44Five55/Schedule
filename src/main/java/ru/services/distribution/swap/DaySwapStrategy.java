package ru.services.distribution.swap;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;
import ru.services.SlotChainService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация стратегии перестановок в рамках одного дня
 */
public class DaySwapStrategy implements SwapStrategy {
    private final SlotChainService slotChainService;

    public DaySwapStrategy(SlotChainService slotChainService) {
        this.slotChainService = slotChainService;
    }

    @Override
    public Optional<SwapOption> findSwapOption(ScheduleGrid schedule, CellForLesson conflictCell, AbstractLesson lesson) {
        List<SwapOption> possibleSwaps = findPossibleSwapsInDay(schedule, conflictCell, lesson);
        return chooseBestSwapOption(possibleSwaps);
    }

    /**
     * Ищет возможные перестановки в рамках дня
     */
    private List<SwapOption> findPossibleSwapsInDay(ScheduleGrid schedule, CellForLesson conflictCell, AbstractLesson newLesson) {
        List<SwapOption> possibleSwaps = new ArrayList<>();
        LocalDate date = conflictCell.getDate();

        // Получаем все ячейки на этот день
        List<CellForLesson> dayCells = schedule.getScheduleGridMap().keySet().stream()
                .filter(cell -> cell.getDate().equals(date))
                .collect(Collectors.toList());

        // Для каждой ячейки в этом дне
        for (CellForLesson targetCell : dayCells) {
            if (targetCell.equals(conflictCell)) continue;

            // Проверяем каждое занятие в целевой ячейке
            for (AbstractLesson targetLesson : schedule.getListLessonInCell(targetCell)) {
                // Проверяем базовые условия для перестановки
                if (!isSwapPossible(schedule, conflictCell, newLesson, targetCell, targetLesson)) {
                    continue;
                }

                possibleSwaps.add(new SwapOption(conflictCell, newLesson, targetCell, targetLesson));
            }
        }

        return possibleSwaps;
    }

    /**
     * Проверяет возможность перестановки
     */
    private boolean isSwapPossible(ScheduleGrid schedule, CellForLesson cell1, AbstractLesson lesson1,
                                 CellForLesson cell2, AbstractLesson lesson2) {
        // Проверяем, что это разные ячейки
        if (cell1.equals(cell2)) return false;

        // Проверяем, что оба занятия - практики или оба - лекции
        if (!lesson1.getCurriculumSlot().getKindOfStudy().equals(
            lesson2.getCurriculumSlot().getKindOfStudy())) return false;

        // Проверяем доступность преподавателей
        if (!areEducatorsAvailable(lesson1, lesson2, cell1, cell2)) return false;

        // Проверяем доступность групп
        if (!areGroupsAvailable(lesson1, lesson2, cell1, cell2)) return false;

        // Проверяем цепочки занятий
        return !hasChainConflicts(schedule, lesson1, lesson2);
    }

    /**
     * Проверяет доступность преподавателей для перестановки
     */
    private boolean areEducatorsAvailable(AbstractLesson lesson1, AbstractLesson lesson2,
                                        CellForLesson cell1, CellForLesson cell2) {
        return lesson1.getEducators().stream().allMatch(educator ->
                educator.isFreeConstraintsGrid(cell2)) &&
                lesson2.getEducators().stream().allMatch(educator ->
                        educator.isFreeConstraintsGrid(cell1));
    }

    /**
     * Проверяет доступность групп для перестановки
     */
    private boolean areGroupsAvailable(AbstractLesson lesson1, AbstractLesson lesson2,
                                     CellForLesson cell1, CellForLesson cell2) {
        return lesson1.getGroupCombinations().stream().allMatch(group ->
                group.getGroups().stream().allMatch(g -> g.isFreeConstraintsGrid(cell2))) &&
                lesson2.getGroupCombinations().stream().allMatch(group ->
                        group.getGroups().stream().allMatch(g -> g.isFreeConstraintsGrid(cell1)));
    }

    /**
     * Проверяет наличие конфликтов с цепочками занятий
     */
    private boolean hasChainConflicts(ScheduleGrid schedule, AbstractLesson lesson1, AbstractLesson lesson2) {
        List<Lesson> allLessons = schedule.getScheduleGridMap().values().stream()
                .flatMap(List::stream)
                .filter(l -> l instanceof Lesson)
                .map(l -> (Lesson)l)
                .collect(Collectors.toList());

        List<Lesson> chain1 = slotChainService.findLessonsInSlotChain((Lesson)lesson1, allLessons);
        List<Lesson> chain2 = slotChainService.findLessonsInSlotChain((Lesson)lesson2, allLessons);

        return !chain1.isEmpty() || !chain2.isEmpty();
    }

    /**
     * Выбирает лучший вариант перестановки
     */
    private Optional<SwapOption> chooseBestSwapOption(List<SwapOption> options) {
        if (options.isEmpty()) {
            return Optional.empty();
        }

        return options.stream()
                .min(Comparator.comparingInt(this::calculateSwapCost));
    }

    /**
     * Рассчитывает "стоимость" перестановки
     */
    private int calculateSwapCost(SwapOption swap) {
        int cost = 0;

        // Учитываем разницу во времени
        cost += Math.abs(swap.originalCell().getTimeSlotPair().ordinal() -
                swap.targetCell().getTimeSlotPair().ordinal()) * 10;

        // Учитываем количество затронутых студентов
        cost += swap.originalLesson().getGroupCombinations().stream()
                .mapToInt(gc -> gc.getGroups().size()).sum();
        cost += swap.targetLesson().getGroupCombinations().stream()
                .mapToInt(gc -> gc.getGroups().size()).sum();

        return cost;
    }
} 