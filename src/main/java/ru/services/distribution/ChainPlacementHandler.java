package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.SlotChainService;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обрабатывает размещение цепочек занятий.
 * Упрощённая версия с сохранением логики работы с цепочками.
 */
@Slf4j
public class ChainPlacementHandler {
    private final SlotChainService slotChainService;
    private final DistributionContext context;
    private final ScheduleWorkspace workspace;

    public ChainPlacementHandler(SlotChainService slotChainService, DistributionContext context) {
        this.slotChainService = slotChainService;
        this.context = context;
        this.workspace = context.getWorkspace();
    }

    /**
     * Получает цепочку занятий для указанного занятия.
     * Сохранена оригинальная логика.
     */
    public List<Lesson> getChainForLesson(Lesson startLesson, List<Lesson> allSortedLessons) {
        List<Integer> chainSlotIds = slotChainService.getFullChain(startLesson.getCurriculumSlot().getId());

        if (chainSlotIds.size() <= 1) {
            return Collections.singletonList(startLesson);
        }

        Integer streamId = startLesson.getStudyStream().getId();

        List<Lesson> chain = allSortedLessons.stream()
                .filter(l -> chainSlotIds.contains(l.getCurriculumSlot().getId())
                        && l.getStudyStream().getId().equals(streamId))
                .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                .collect(Collectors.toList());

        if (!chain.isEmpty() && !chain.get(0).equals(startLesson)) {
            return Collections.singletonList(startLesson);
        }

        return chain;
    }

    /**
     * Пытается разместить цепочку занятий в указанную дату.
     */
    public boolean tryPlaceChain(List<Lesson> chain, LocalDate date) {
        if (chain.isEmpty()) return true;
        if (chain.size() == 1) {
            return tryPlaceSingle(chain.get(0), date);
        }

        List<CellForLesson> dayCells = getDayCells(date);
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        int chainSize = chain.size();

        // Скользящее окно: ищем место для всей цепочки
        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            if (canPlaceChainAt(chain, dayCells, i)) {
                placeChainAt(chain, dayCells, i);
                log.debug("Цепочка из {} занятий размещена на {}", chainSize, date);
                return true;
            }
        }

        log.trace("Не удалось разместить цепочку из {} занятий на {}", chainSize, date);
        return false;
    }

    /**
     * Размещение одиночного занятия (не цепочки).
     */
    public boolean tryPlaceSingle(Lesson lesson, LocalDate date) {
        List<CellForLesson> dayCells = getDayCells(date);

        for (CellForLesson cell : dayCells) {
            if (shouldSkipCell(cell)) continue;

            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (option.isPossible()) {
                workspace.executePlacement(option);
                context.addDistributedLesson(lesson);
                log.debug("Занятие размещено: {} на {}", lesson.getCurriculumSlot().getId(), date);
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, можно ли разместить цепочку в указанный день.
     */
    public boolean canPlaceChain(List<Lesson> chain, LocalDate date) {
        if (chain.isEmpty()) return true;
        if (chain.size() == 1) {
            return canPlaceSingle(chain.get(0), date);
        }

        List<CellForLesson> dayCells = getDayCells(date);
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        int chainSize = chain.size();
        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            if (canPlaceChainAt(chain, dayCells, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, можно ли разместить одиночное занятие в указанный день.
     */
    public boolean canPlaceSingle(Lesson lesson, LocalDate date) {
        List<CellForLesson> dayCells = getDayCells(date);

        for (CellForLesson cell : dayCells) {
            if (shouldSkipCell(cell)) continue;

            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }

    // ========== Приватные методы ==========

    private List<CellForLesson> getDayCells(LocalDate date) {
        return CellForLessonFactory.getCellsForDate(date);
    }

    private boolean shouldSkipCell(CellForLesson cell) {
        return cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH;
    }

    private boolean canPlaceChainAt(List<Lesson> chain, List<CellForLesson> cells, int offset) {
        for (int j = 0; j < chain.size(); j++) {
            Lesson lesson = chain.get(j);
            CellForLesson cell = cells.get(offset + j);

            // Проверка непрерывности слотов
            if (j > 0) {
                CellForLesson prevCell = cells.get(offset + j - 1);
                int expectedOrdinal = prevCell.getTimeSlotPair().ordinal() + 1;
                if (cell.getTimeSlotPair().ordinal() != expectedOrdinal) {
                    return false;
                }
            }

            // Пропуск 4-й пары
            if (shouldSkipCell(cell)) {
                return false;
            }

            // Проверка доступности
            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (!option.isPossible()) {
                return false;
            }
        }
        return true;
    }

    private void placeChainAt(List<Lesson> chain, List<CellForLesson> cells, int offset) {
        for (int j = 0; j < chain.size(); j++) {
            Lesson lesson = chain.get(j);
            CellForLesson cell = cells.get(offset + j);
            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            workspace.executePlacement(option);
            context.addDistributedLesson(lesson);
        }
    }
}
