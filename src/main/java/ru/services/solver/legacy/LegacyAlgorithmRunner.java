package ru.services.solver.legacy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.logicSchema.DisciplineCourse;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.services.LessonSortingService;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Адаптер, который выполняет логику старого алгоритма распределения,
 * используя новое ядро решателя (ScheduleWorkspace).
 */

/**
 * Адаптер, который выполняет адаптированную логику старого алгоритма распределения.
 */
@Slf4j
@RequiredArgsConstructor
public class LegacyAlgorithmRunner {

    private final ScheduleWorkspace workspace;
    private final List<Lesson> lessonsToPlace;
    private final LessonSortingService lessonSorterService;
    // Кеш: Дисциплина -> Последний назначенный слот
    // Используется для соблюдения хронологии (Тема 2 после Темы 1)
    private final Map<Integer, CellForLesson> lastSlotForCourse = new HashMap<>();

    /**
     * Запускает основной цикл распределения.
     */
    public void run() {
        // 1. Сортировка (Лекция -> Практика -> Практика ...)
        // Используем твой сложный алгоритм сортировки
        boolean streamSorted = false;
        List<Lesson> sortedLessons = lessonSorterService.changeOrderLessons(new ArrayList<>(lessonsToPlace), streamSorted);

        List<CellForLesson> allCells = CellForLessonFactory.getAllCells();

        for (Lesson lesson : sortedLessons) {
            boolean placed = false;

            // 2. Определение точки старта поиска
            // Ищем ячейки только ПОСЛЕ предыдущего занятия этого курса
            List<CellForLesson> availableCells = getCellsAfterLastLesson(lesson, allCells);

            // 3. Поиск места
            for (CellForLesson cell : availableCells) {

                // --- Правило: Лекции утром, Практики днем ---
                if (!isPreferredTime(lesson, cell)) {
                    continue; // Пропускаем неудобные слоты, ищем дальше
                }

                // --- Правило: Не ставить на 4-ю пару (из старого кода) ---
                if (cell.getTimeSlotPair() == TimeSlotPair.FOURTH) {
                    continue;
                }

                PlacementOption option = workspace.findPlacementOption(lesson, cell);

                if (option.isPossible()) {
                    workspace.executePlacement(option);
                    updateLastSlot(lesson, cell);
                    placed = true;
                    // log.info("Placed: {} at {}", lesson, cell);
                    break;
                }
            }

            // Если не нашли "удобное" место, пробуем любое (Fall-back)
            if (!placed) {
                for (CellForLesson cell : availableCells) {
                    PlacementOption option = workspace.findPlacementOption(lesson, cell);
                    if (option.isPossible()) {
                        workspace.executePlacement(option);
                        updateLastSlot(lesson, cell);
                        placed = true;
                        // log.warn("Fallback placement: {} at {}", lesson, cell);
                        break;
                    }
                }
            }

            if (!placed) {
                log.error("FAILED to place lesson: {}", lesson);
            }
        }
    }

    /**
     * Возвращает список ячеек, идущих хронологически ПОСЛЕ последнего назначенного занятия
     * по этой дисциплине.
     */
    private List<CellForLesson> getCellsAfterLastLesson(Lesson lesson, List<CellForLesson> allCells) {
        DisciplineCourse course = lesson.getDisciplineCourse();
        if (course == null) return allCells;

        CellForLesson lastCell = lastSlotForCourse.get(course.getId());
        if (lastCell == null) return allCells; // Это первое занятие

        // Фильтруем: берем только те, что позже lastCell
        return allCells.stream()
                .filter(cell -> compareCells(cell, lastCell) > 0) // Строго больше
                .collect(Collectors.toList());
    }

    private void updateLastSlot(Lesson lesson, CellForLesson cell) {
        DisciplineCourse course = lesson.getDisciplineCourse();
        if (course != null) {
            // Запоминаем, где мы закончили с этой дисциплиной
            lastSlotForCourse.put(course.getId(), cell);
        }
    }

    private int compareCells(CellForLesson c1, CellForLesson c2) {
        int dateCmp = c1.getDate().compareTo(c2.getDate());
        if (dateCmp != 0) return dateCmp;
        return c1.getTimeSlotPair().compareTo(c2.getTimeSlotPair());
    }

    /**
     * Реализует логику:
     * - Лекции любят 1-ю пару (или любую).
     * - Практики любят быть ПОСЛЕ лекций (т.е. 2-я, 3-я пара).
     */
    private boolean isPreferredTime(Lesson lesson, CellForLesson cell) {
        if (lesson.getKindOfStudy() == KindOfStudy.LECTURE) {
            return true; // Лекции можно ставить когда угодно (но лучше утром)
        } else {
            // Практика: желательно не 1-я пара (чтобы лекция успела пройти утром)
            // Это эвристика.
            return cell.getTimeSlotPair() != TimeSlotPair.FIRST;
        }
    }
}
