package ru.services.solver.legacy;

import lombok.RequiredArgsConstructor;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.LessonSortingService;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.util.List;

/**
 * Адаптер, который выполняет логику старого алгоритма распределения,
 * используя новое ядро решателя (ScheduleWorkspace).
 */

/**
 * Адаптер, который выполняет адаптированную логику старого алгоритма распределения.
 */
@RequiredArgsConstructor
public class LegacyAlgorithmRunner {

    private final ScheduleWorkspace workspace;
    private final List<Lesson> lessonsToPlace;
    private final LessonSortingService lessonSorterService;

    /**
     * Запускает основной цикл распределения.
     */
    public void run() {
        List<Lesson> sortedLessons = lessonSorterService.getSortedLessons(lessonsToPlace);

        List<CellForLesson> allPossibleCells = CellForLessonFactory.getAllCells();

        for (Lesson lesson : sortedLessons) {
            boolean placed = false;
            // Итерируемся по всем возможным ячейкам и ищем первое подходящее место
            for (CellForLesson cell : allPossibleCells) {

                PlacementOption option = workspace.findPlacementOption(lesson, cell);

                if (option.isPossible()) {
                    workspace.executePlacement(option);
                    placed = true;
                    System.out.println("УСПЕХ: Размещено занятие " + lesson + " в ячейку " + cell);
                    break; // Нашли место, переходим к следующему занятию
                }
            }
            if (!placed) {
                System.err.println("ПРОВАЛ: Не удалось найти место для занятия " + lesson);
            }
        }
    }
}
