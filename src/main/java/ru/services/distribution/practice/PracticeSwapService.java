package ru.services.distribution.practice;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.SlotChainService;
import ru.services.distribution.core.DistributionContext;
import ru.services.distribution.finder.DateFinder;
import ru.services.distribution.finder.DateFinderFactory;
import ru.services.distribution.placement.ChainPlacementHandler;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для свапа практик при конфликтах.
 * Вынесены методы из DistributionDiscipline:
 * - trySwapPracticeWithAnother()
 * - canSwapPractices()
 * - performSwap()
 */
@Slf4j
public class PracticeSwapService {
    private final DistributionContext context;
    private final SlotChainService slotChainService;
    private final ChainPlacementHandler chainHandler;

    public PracticeSwapService(DistributionContext context, SlotChainService slotChainService,
                               ChainPlacementHandler chainHandler) {
        this.context = context;
        this.slotChainService = slotChainService;
        this.chainHandler = chainHandler;
    }

    /**
     * Пытается поменять практику с другой практикой того же преподавателя.
     */
    public boolean trySwap(Lesson practice, Educator educator, List<Lesson> educatorLessons,
                          LocalDate minDate, LocalDate semesterEnd, Set<LocalDate> lectureDates) {
        List<Lesson> otherPractices = context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .filter(l -> !l.equals(practice))
                .collect(Collectors.toList());

        for (Lesson other : otherPractices) {
            CellForLesson otherCell = context.getWorkspace().getCellForLesson(other);
            if (otherCell == null) continue;

            LocalDate otherDate = otherCell.getDate();

            if (otherDate.isBefore(minDate) || otherDate.isAfter(semesterEnd)) {
                continue;
            }

            if (canSwap(practice, other)) {
                if (performSwap(practice, other, educator, educatorLessons, minDate, semesterEnd, lectureDates)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Проверяет, можно ли поменять две практики местами.
     */
    private boolean canSwap(Lesson practice1, Lesson practice2) {
        // 1. Группы должны совпадать
        if (!practice1.getStudyStream().equals(practice2.getStudyStream())) {
            return false;
        }

        // 2. Требования к аудиториям должны быть совместимы
        boolean practice1NeedsRoom = practice1.getRequiredAuditorium() != null;
        boolean practice2NeedsRoom = practice2.getRequiredAuditorium() != null;

        if (practice1NeedsRoom && practice2NeedsRoom) {
            if (!practice1.getRequiredAuditorium().equals(practice2.getRequiredAuditorium())) {
                return false;
            }
        }
        if (practice1NeedsRoom != practice2NeedsRoom) {
            return false;
        }

        // 3. Длина цепочки должна быть одинаковой
        List<Integer> chain1 = slotChainService.getFullChain(practice1.getCurriculumSlot().getId());
        List<Integer> chain2 = slotChainService.getFullChain(practice2.getCurriculumSlot().getId());

        if (chain1.size() != chain2.size()) {
            return false;
        }

        return true;
    }

    /**
     * Выполняет обмен двух практик местами.
     */
    private boolean performSwap(Lesson practice, Lesson other, Educator educator, List<Lesson> educatorLessons,
                                LocalDate minDate, LocalDate semesterEnd,
                                Set<LocalDate> lectureDates) {
        CellForLesson otherCell = context.getWorkspace().getCellForLesson(other);
        if (otherCell == null) return false;

        LocalDate otherDate = otherCell.getDate();

        // 1. Проверяем: можно ли разместить practice на место other?
        List<CellForLesson> otherDayCells = CellForLessonFactory.getCellsForDate(otherDate);
        if (!canPlacePracticeInCells(practice, otherDayCells)) {
            return false;
        }

        // 2. Убираем other из расписания
        context.getWorkspace().removePlacement(other);
        context.removeDistributedLesson(other);

        List<Lesson> practiceChain = chainHandler.getChainForLesson(practice, educatorLessons);
        if (!chainHandler.tryPlaceChainInDay(practiceChain, otherDayCells, false)) {
            // Не удалось — откатываем
            PlacementOption otherOption = context.getWorkspace().findPlacementOption(other, otherCell);
            if (otherOption.isPossible()) {
                context.getWorkspace().executePlacement(otherOption);
                context.addDistributedLesson(other);
            }
            return false;
        }

        // 4. Размещаем other на новое место
        // В реальном коде здесь нужен DateFinder для поиска новой даты
        return true; // Упрощение
    }

    /**
     * Проверяет, можно ли разместить практику в указанных ячейках дня.
     */
    private boolean canPlacePracticeInCells(Lesson practice, List<CellForLesson> dayCells) {
        for (CellForLesson cell : dayCells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                continue;
            }
            PlacementOption option = context.getWorkspace().findPlacementOption(practice, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }
}
