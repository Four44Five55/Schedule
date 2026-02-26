package ru.services.distribution.validator;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Проверяет возможность размещения занятия.
 * Вынесены методы из DistributionDiscipline:
 * - canPlacePracticeInDate()
 * - canPlacePracticeInCell()
 * - canPlaceChainInDate()
 * - canPlacePracticeInCells()
 * - isDayViableForLesson()
 * - isCellFreeForLesson()
 */
@Slf4j
public class PlacementValidator {
    private final ScheduleWorkspace workspace;

    public PlacementValidator(ScheduleWorkspace workspace) {
        this.workspace = workspace;
    }

    /**
     * Проверяет, можно ли разместить практику в указанную дату.
     */
    public boolean canPlacePractice(Lesson practice, LocalDate date) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        log.trace("canPlacePracticeInDate: практика ID={}, дата={}, ячеек в день: {}",
                practice.getCurriculumSlot().getId(), date, dayCells.size());

        for (CellForLesson cell : dayCells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                log.trace("  → Ячейка {} пропущена (4-я пара)", cell.getTimeSlotPair());
                continue;
            }

            PlacementOption option = workspace.findPlacementOption(practice, cell);
            if (option.isPossible()) {
                log.trace("  → Ячейка {} доступна", cell.getTimeSlotPair());
                return true;
            } else {
                log.trace("  → Ячейка {} недоступна: {}", cell.getTimeSlotPair(), option.failureReason());
            }
        }
        log.trace("  → Дата {} не подходит: нет доступных ячеек", date);
        return false;
    }

    /**
     * Проверяет, можно ли разместить занятие в конкретной ячейке.
     */
    public boolean canPlaceInCell(Lesson lesson, CellForLesson cell) {
        if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
            return false;
        }
        PlacementOption option = workspace.findPlacementOption(lesson, cell);
        return option.isPossible();
    }

    /**
     * Проверяет, можно ли разместить цепочку занятий в указанную дату.
     */
    public boolean canPlaceChain(List<Lesson> chain, LocalDate date) {
        if (chain.isEmpty()) {
            return true;
        }
        if (chain.size() == 1) {
            return canPlacePractice(chain.get(0), date);
        }

        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        int chainSize = chain.size();

        log.trace("canPlaceChainInDate: цепочка из {} занятий, дата={}, ячеек в день: {}",
                chainSize, date, dayCells.size());

        // Скользящее окно: проверяем каждые chainSize слотов подряд
        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            boolean fit = true;

            // Проверяем непрерывность и доступность для каждого занятия цепочки
            for (int j = 0; j < chainSize; j++) {
                CellForLesson cell = dayCells.get(i + j);

                // 1. Исключаем 4-ю пару
                if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                    fit = false;
                    break;
                }

                // 2. Проверка непрерывности слотов
                if (j > 0) {
                    CellForLesson prevCell = dayCells.get(i + j - 1);
                    if (cell.getTimeSlotPair().ordinal() != prevCell.getTimeSlotPair().ordinal() + 1) {
                        fit = false;
                        break; // Разрыв в расписании дня
                    }
                }

                // 3. Проверяем доступность через canPlacePracticeInCell
                if (!canPlaceInCell(chain.get(j), cell)) {
                    fit = false;
                    break;
                }
            }

            if (fit) {
                log.trace("  → Нашлось место для цепочки: старт с ячейки {}", dayCells.get(i).getTimeSlotPair());
                return true;
            }
        }

        log.trace("  → Дата {} не подходит для цепочки: нет {} непрерывных слотов", date, chainSize);
        return false;
    }

    /**
     * Проверяет, можно ли разместить практику в указанных ячейках дня.
     */
    public boolean canPlaceInCells(Lesson practice, List<CellForLesson> dayCells) {
        for (CellForLesson cell : dayCells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                continue;
            }
            PlacementOption option = workspace.findPlacementOption(practice, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Быстрая проверка: может ли урок теоретически встать в этот день?
     */
    public boolean isDayViable(LocalDate date, Lesson lesson) {
        List<CellForLesson> cells = CellForLessonFactory.getCellsForDate(date);
        for (CellForLesson cell : cells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) continue;

            if (workspace.findPlacementOption(lesson, cell).isPossible()) {
                return true; // Нашли хотя бы одну дырку
            }
        }
        return false; // Весь день забит или заблокирован ограничениями
    }

    /**
     * Проверяет возможность назначения занятия в заданную ячейку для всех сущностей занятия.
     */
    public boolean isCellFree(Lesson lesson, CellForLesson cell) {
        return workspace.findPlacementOption(lesson, cell).isPossible();
    }
}
