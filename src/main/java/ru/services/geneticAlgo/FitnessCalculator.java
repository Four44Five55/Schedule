package ru.services.geneticAlgo;

import ru.abstracts.AbstractMaterialEntity;
import ru.entity.*;
import ru.abstracts.AbstractLesson;

import java.util.List;

public class FitnessCalculator {
    public static double calculate(ScheduleChromosome chromosome) {
        ScheduleGrid grid = chromosome.getScheduleGrid();
        int conflicts = 0;

        // 1. Проверка конфликтов преподавателей и аудиторий
        for (CellForLesson cell : grid.getScheduleGridMap().keySet()) {
            List<AbstractLesson> lessons = grid.getListLessonInCell(cell);
            for (AbstractLesson lesson : lessons) {
                // Проверка, свободна ли ячейка для всех сущностей занятия
                for (AbstractMaterialEntity entity : lesson.getAllMaterialEntity()) {
                    if (!entity.isFree(grid, cell)) {
                        conflicts += 10; // Штраф за занятость
                    }
                }
            }
        }

        // 2. Проверка "окон" у преподавателей (дополнительно)
        conflicts += countTeacherGaps(chromosome);

        return 1.0 / (1 + conflicts); // Нормализация
    }

    private static int countTeacherGaps(ScheduleChromosome chromosome) {
        // Логика подсчёта "окон"...
        return 0;
    }
}
