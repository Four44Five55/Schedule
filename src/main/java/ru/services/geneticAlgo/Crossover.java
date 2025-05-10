package ru.services.geneticAlgo;

import ru.entity.*;
import java.util.*;

public class Crossover {
    public static ScheduleChromosome singlePointCrossover(ScheduleChromosome parent1, ScheduleChromosome parent2) {
        ScheduleChromosome child = parent1.deepCopy(); // Создаём глубокую копию
        Random random = new Random();
        List<CellForLesson> cells = new ArrayList<>(parent1.getScheduleGrid().getScheduleGridMap().keySet());
        int crossoverPoint = random.nextInt(cells.size());

        // Копируем занятия из parent2 после точки кроссовера
        for (int i = crossoverPoint; i < cells.size(); i++) {
            CellForLesson cell = cells.get(i);
            // Очищаем ячейку в ребёнке и добавляем занятия из parent2
            child.getScheduleGrid().getScheduleGridMap().get(cell).clear();
            child.getScheduleGrid().getScheduleGridMap().get(cell)
                    .addAll(parent2.getScheduleGrid().getListLessonInCell(cell));
        }

        // Обязательно пересчитываем фитнес!
        child.setFitness(FitnessCalculator.calculate(child));
        return child;
    }
}