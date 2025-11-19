package ru.services.distribution;

import lombok.Getter;
import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractMaterialEntity;
import ru.entity.*;
import ru.services.SlotChainService;
import ru.services.solver.model.ScheduleGrid;
import ru.services.statistics.DefaultScheduleStatistics;
import ru.services.statistics.ScheduleStatistics;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class UnifiedScheduleManager {
    private final Map<String, ScheduleGrid> disciplineSchedules;

    @Getter
    private final ScheduleGrid unifiedSchedule;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final SlotChainService slotChainService;
    private final ScheduleStatistics statistics;

    // Кэш для результатов проверки цепочек для оптимизации
    private final Map<Integer, List<Integer>> slotChainCache = new HashMap<>();

    public UnifiedScheduleManager(LocalDate startDate, LocalDate endDate, SlotChainService slotChainService, ScheduleGrid unifiedSchedule) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.disciplineSchedules = new HashMap<>();
        this.unifiedSchedule = unifiedSchedule;
        this.slotChainService = slotChainService;
        this.statistics = new DefaultScheduleStatistics(unifiedSchedule, disciplineSchedules);
    }

    /**
     * Основной метод добавления расписания дисциплины
     */
    public void addDisciplineSchedule(String disciplineName, ScheduleGrid schedule) {
        if (disciplineName == null || schedule == null) {
            throw new IllegalArgumentException("Параметры не могут быть null");
        }
        if (!schedule.getStartDate().equals(startDate) || !schedule.getEndDate().equals(endDate)) {
            throw new IllegalArgumentException("Диапазон дат расписания не соответствует менеджеру");
        }

        disciplineSchedules.put(disciplineName, schedule);
        mergeSchedule(schedule);
        statistics.updateStatistics(disciplineName, schedule);

        double successRate = statistics.getTotalLessons(disciplineName) > 0 ? (statistics.getAddedLessons(disciplineName) * 100.0) / statistics.getTotalLessons(disciplineName) : 0;
        System.out.printf("Добавление расписания для %s: всего занятий - %d, успешно добавлено - %d (%.2f%%)%n",
                disciplineName, statistics.getTotalLessons(disciplineName), statistics.getAddedLessons(disciplineName), successRate);
    }

    public void printStatistics() {
        statistics.printStatistics();
    }

    // --- Вспомогательные структуры для перестановок и переносов ---

    private record SwapOption(CellForLesson originalCell, AbstractLesson originalLesson, CellForLesson targetCell, AbstractLesson targetLesson) {}
    private record PlacementOption(AbstractLesson lessonToPlace, CellForLesson targetCell, Optional<AbstractLesson> lessonToSwap) {}

    // --- Основная логика слияния ---

    private void mergeSchedule(ScheduleGrid schedule) {
        schedule.getScheduleGridMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(CellForLesson::getDate)))
                .forEach(entry -> {
                    CellForLesson originalCell = entry.getKey();
                    entry.getValue().forEach(lesson -> {
                        // Стратегия 1: Попытка прямого добавления
                        if (isCellFreeForLesson(originalCell, lesson)) {
                            unifiedSchedule.addLessonToCell(originalCell, lesson);
                            return;
                        }

                        // Стратегия 2: Попытка перестановки в тот же день
                        Optional<SwapOption> sameDaySwap = findBestSwapOptionFor(originalCell, lesson);
                        if (sameDaySwap.isPresent()) {
                            executeSwap(sameDaySwap.get());
                            return;
                        }

                        // Стратегия 3: Попытка переноса или перестановки в другие дни
                        Optional<PlacementOption> crossDayPlacement = findBestCrossDayPlacementFor(originalCell, lesson, schedule);
                        if (crossDayPlacement.isPresent()) {
                            executePlacement(crossDayPlacement.get());
                            return;
                        }

                        // Если ничего не помогло
                        logDetailedConflict(originalCell, lesson, "Не удалось найти место или вариант для перестановки.");
                    });
                });
    }

    // --- Методы для поиска решений ---

    /**
     * Ищет наилучший вариант размещения (простой перенос или перестановка) для занятия,
     * используя динамическое окно, основанное на занятости преподавателя.
     */
    private Optional<PlacementOption> findBestCrossDayPlacementFor(CellForLesson originalCell, AbstractLesson lessonToPlace, ScheduleGrid originalDisciplineSchedule) {
        LocalDate originalDate = originalCell.getDate();

        LocalDate searchStartDate = findPreviousLessonDate(lessonToPlace, originalDate, originalDisciplineSchedule)
                .orElse(this.startDate.minusDays(1));
        LocalDate searchEndDate = findNextLessonDate(lessonToPlace, originalDate, originalDisciplineSchedule)
                .orElse(this.endDate.plusDays(1));

        List<LocalDate> searchDates = new ArrayList<>();
        for (LocalDate date = searchStartDate.plusDays(1); date.isBefore(searchEndDate); date = date.plusDays(1)) {
            if (!date.equals(originalDate)) {
                searchDates.add(date);
            }
        }
        searchDates.sort(Comparator.comparingInt(date -> Math.abs(date.getDayOfYear() - originalDate.getDayOfYear())));

        for (LocalDate date : searchDates) {
            Optional<PlacementOption> emptySlotPlacement = findEmptySlotOnDate(lessonToPlace, date);
            if (emptySlotPlacement.isPresent()) return emptySlotPlacement;
        }

        return searchDates.stream()
                .map(date -> findSwapOptionsOnDate(lessonToPlace, originalCell, date))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingInt(this::calculatePlacementCost));
    }

    private Optional<SwapOption> findBestSwapOptionFor(CellForLesson conflictCell, AbstractLesson newLesson) {
        return findSwapOptionsOnDate(newLesson, conflictCell, conflictCell.getDate())
                .map(opt -> new SwapOption(conflictCell, newLesson, opt.targetCell(), opt.lessonToSwap().orElse(null)));
    }

    private Optional<PlacementOption> findEmptySlotOnDate(AbstractLesson lesson, LocalDate date) {
        return unifiedSchedule.getScheduleGridMap().keySet().stream()
                .filter(cell -> cell.getDate().equals(date))
                // .filter(cell -> lesson.getKindOfStudy().isInterchangeableWith(cell.getTimeSlotPair().getKindOfStudy())) // <- ЭТА СТРОКА УДАЛЕНА
                .filter(cell -> isCellFreeForLesson(cell, lesson))
                .map(freeCell -> new PlacementOption(lesson, freeCell, Optional.empty()))
                .findFirst(); // Берем первый попавшийся свободный слот
    }


    private Optional<PlacementOption> findSwapOptionsOnDate(AbstractLesson lessonToPlace, CellForLesson originalCell, LocalDate date) {
        return unifiedSchedule.getScheduleGridMap().entrySet().stream()
                .filter(entry -> entry.getKey().getDate().equals(date))
                .flatMap(entry -> {
                    CellForLesson targetCell = entry.getKey();
                    return entry.getValue().stream()
                            .filter(targetLesson -> isSwapPossible(lessonToPlace, originalCell, targetLesson, targetCell))
                            .map(targetLesson -> new PlacementOption(lessonToPlace, targetCell, Optional.of(targetLesson)));
                })
                .min(Comparator.comparingInt(this::calculatePlacementCost));
    }

    // --- Проверочные методы и вспомогательная логика ---

    private boolean isCellFreeForLesson(CellForLesson cell, AbstractLesson lesson) {
        return lesson.getAllMaterialEntity().stream().allMatch(entity -> entity.isFree(unifiedSchedule, cell));
    }

    private boolean isSwapPossible(AbstractLesson lesson1, CellForLesson cell1, AbstractLesson lesson2, CellForLesson cell2) {
        if (cell1.equals(cell2)) return false;
        if (!lesson1.getKindOfStudy().isInterchangeableWith(lesson2.getKindOfStudy())) return false;
        if (isLessonInChain(lesson1) || isLessonInChain(lesson2)) return false;

        boolean lesson1FitsInCell2 = lesson1.getAllMaterialEntity().stream()
                .allMatch(entity -> isEntityFreeForSwap(entity, cell2, lesson2));
        if (!lesson1FitsInCell2) return false;

        return lesson2.getAllMaterialEntity().stream()
                .allMatch(entity -> isEntityFreeForSwap(entity, cell1, lesson1));
    }

    private boolean isEntityFreeForSwap(AbstractMaterialEntity entity, CellForLesson targetCell, AbstractLesson lessonToIgnore) {
        if (!entity.isFreeConstraintsGrid(targetCell)) return false;
        return unifiedSchedule.getLessonsUsingEntity(entity, targetCell).stream()
                .allMatch(lessonInCell -> lessonInCell.equals(lessonToIgnore));
    }

    private boolean isLessonInChain(AbstractLesson lesson) {
        Integer slotId = lesson.getCurriculumSlotId();
        if (slotId == null) return false;
        return !slotChainCache.computeIfAbsent(slotId, slotChainService::getAllLinkedSlotIds).isEmpty();
    }

    private Optional<LocalDate> findPreviousLessonDate(AbstractLesson lesson, LocalDate currentDate, ScheduleGrid originalSchedule) {
        if (lesson.getEducators().isEmpty()) return Optional.empty();
        Educator mainEducator = lesson.getEducators().get(0);
        return originalSchedule.getScheduleGridMap().entrySet().stream()
                .filter(entry -> entry.getKey().getDate().isBefore(currentDate))
                .filter(entry -> entry.getValue().stream().anyMatch(l -> l.getEducators().contains(mainEducator)))
                .map(entry -> entry.getKey().getDate())
                .max(LocalDate::compareTo);
    }

    private Optional<LocalDate> findNextLessonDate(AbstractLesson lesson, LocalDate currentDate, ScheduleGrid originalSchedule) {
        if (lesson.getEducators().isEmpty()) return Optional.empty();
        Educator mainEducator = lesson.getEducators().get(0);
        return originalSchedule.getScheduleGridMap().entrySet().stream()
                .filter(entry -> entry.getKey().getDate().isAfter(currentDate))
                .filter(entry -> entry.getValue().stream().anyMatch(l -> l.getEducators().contains(mainEducator)))
                .map(entry -> entry.getKey().getDate())
                .min(LocalDate::compareTo);
    }

    private int calculatePlacementCost(PlacementOption option) {
        int cost = 0;
        if (option.lessonToSwap.isPresent()) {
            cost += 50; // Штраф за перестановку
            cost += option.lessonToSwap.get().getGroups().size();
        } else {
            cost += 10; // Небольшой штраф за простой перенос
        }
        cost += option.lessonToPlace.getGroups().size();
        return cost;
    }

    // --- Методы выполнения действий и логирования ---

    private void executePlacement(PlacementOption option) {
        if (option.lessonToSwap.isPresent()) {
            AbstractLesson lessonToSwap = option.lessonToSwap.get();
            executeSwap(new SwapOption(null, option.lessonToPlace, option.targetCell, lessonToSwap));
        } else {
            unifiedSchedule.addLessonToCell(option.targetCell, option.lessonToPlace);
            statistics.recordSwap(option.lessonToPlace.getDiscipline().getName());
            logPlacement(option);
        }
    }

    private void executeSwap(SwapOption swap) {
        // originalCell может быть null, если вызов из executePlacement
        unifiedSchedule.removeLessonFromCell(swap.targetCell, swap.targetLesson);
        unifiedSchedule.addLessonToCell(swap.targetCell, swap.originalLesson);

        // ПРОВЕРКА НА NULL, чтобы избежать ошибки
        if(swap.originalCell != null){
            unifiedSchedule.addLessonToCell(swap.originalCell, swap.targetLesson);
        }

        statistics.recordSwap(swap.originalLesson.getDiscipline().getName());
        logSwap(swap);
    }

    private void logSwap(SwapOption swap) {
        String originalPositionText = (swap.originalCell != null) ?
                String.format("с %s %s", swap.originalCell.getDate(), swap.originalCell.getTimeSlotPair()) :
                "из неразмещенных";

        String targetPositionText = String.format("%s %s", swap.targetCell.getDate(), swap.targetCell.getTimeSlotPair());

        System.out.printf("""
                    [ПЕРЕСТАНОВКА] Выполнена перестановка занятий:
                    - %s %s (%s) перемещено %s на %s
                    - %s %s (%s) перемещено с %s на %s
                    """,
                swap.originalLesson.getDiscipline().getAbbreviation(), swap.originalLesson.getGroupCombinations().get(0),
                swap.originalLesson.getKindOfStudy(), originalPositionText,
                targetPositionText,

                swap.targetLesson.getDiscipline().getAbbreviation(), swap.targetLesson.getGroupCombinations().get(0),
                swap.targetLesson.getKindOfStudy(),
                targetPositionText, originalPositionText
        );
    }

    private void logPlacement(PlacementOption option) {
        System.out.printf("""
                        [ПЕРЕНОС] Занятие успешно перенесено:
                        - %s %s (%s) размещено в %s %s
                        """,
                option.lessonToPlace.getDiscipline().getAbbreviation(),
                option.lessonToPlace.getGroupCombinations().get(0),
                option.lessonToPlace.getKindOfStudy(),
                option.targetCell.getDate(),
                option.targetCell.getTimeSlotPair()
        );
    }

    private void logDetailedConflict(CellForLesson cell, AbstractLesson newLesson, String message) {
        String conflictDetails = newLesson.getAllMaterialEntity().stream()
                .filter(entity -> !entity.isFree(unifiedSchedule, cell))
                .map(entity -> {
                    String reason;
                    if (!entity.isFreeConstraintsGrid(cell)) {
                        reason = "персональное ограничение: " + entity.getConstraint(cell);
                    } else {
                        String lessons = unifiedSchedule.getLessonsUsingEntity(entity, cell).stream()
                                .map(l -> l.getDiscipline().getAbbreviation())
                                .collect(Collectors.joining(", "));
                        reason = "уже занят(а) в: " + lessons;
                    }
                    return String.format("  - %-12s '%-15s' (%s)",
                            entity.getClass().getSimpleName(), entity.getName(), reason);
                })
                .distinct().collect(Collectors.joining("\n"));

        System.out.printf("""
                        [КОНФЛИКТ] %s %s:
                        Не удалось добавить: %s для %s
                        Сообщение: %s
                        Причины конфликтов:
                        %s
                        ----------------------------------------------------
                        """,
                cell.getDate(), cell.getTimeSlotPair(),
                newLesson.getDiscipline().getName(), newLesson.getGroupCombinations().get(0),
                message,
                conflictDetails.isEmpty() ? "  Неизвестная причина" : conflictDetails
        );
    }
}