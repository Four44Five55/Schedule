package ru.services.solver.legacy;

import lombok.RequiredArgsConstructor;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.CurriculumSlotService;
import ru.services.SlotChainService;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.util.List;

/**
 * Адаптер, который выполняет логику старого алгоритма распределения,
 * используя новое ядро решателя (ScheduleWorkspace).
 */
@RequiredArgsConstructor
public class LegacyAlgorithmRunner {

    private final ScheduleWorkspace workspace;
    private final List<Lesson> allLessons;

    // Зависимости, которые раньше были в хелпере, теперь передаются сюда
    private final SlotChainService slotChainService;
    private final CurriculumSlotService curriculumSlotService;

    public LegacyAlgorithmRunner(ScheduleWorkspace workspace, List<Lesson> lessons, SlotChainService slotChainService, CurriculumSlotService curriculumSlotService) {
        this.workspace = workspace;
        this.allLessons = lessons;
        this.slotChainService = slotChainService;
        this.curriculumSlotService = curriculumSlotService;
    }

    /**
     * Основной метод, запускающий распределение.
     * Логика взята из старого DistributionDisciplineUniform, но адаптирована.
     */
    public void distribute(List<Lesson> lessonsToPlace) {

        // Здесь можно вставить вашу старую логику сортировки из ListLessonsHelper,
        // или любую другую логику итерации по занятиям.
        // Для примера возьмем простейший вариант: пройтись по всем занятиям и
        // найти для каждого первое попавшееся свободное место.

        List<CellForLesson> allPossibleCells = CellForLessonFactory.getAllOrderedCells();

        for (Lesson lesson : lessonsToPlace) {
            boolean placed = false;
            for (CellForLesson cell : allPossibleCells) {

                // 1. ИЩЕМ ВАРИАНТ РАЗМЕЩЕНИЯ ЧЕРЕЗ WORKSPACE
                PlacementOption option = workspace.findPlacementOption(lesson, cell);

                if (option.isPossible()) {
                    // 2. ЕСЛИ ВАРИАНТ НАЙДЕН - РАЗМЕЩАЕМ ЧЕРЕЗ WORKSPACE
                    workspace.executePlacement(option);
                    placed = true;
                    System.out.println("Размещено: " + lesson + " в " + cell);
                    break; // Переходим к следующему занятию
                }
            }
            if (!placed) {
                System.err.println("НЕ УДАЛОСЬ РАЗМЕСТИТЬ: " + lesson);
            }
        }
    }
}
