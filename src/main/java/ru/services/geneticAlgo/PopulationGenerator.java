package ru.services.geneticAlgo;

import ru.abstracts.AbstractLesson;
import ru.entity.*;
import java.util.*;

public class PopulationGenerator {
    public static List<ScheduleChromosome> generate(int size, List<Lesson> lessons,
                                                    ScheduleGrid templateGrid) {
        List<ScheduleChromosome> population = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < size; i++) {
            ScheduleChromosome chromosome = new ScheduleChromosome(templateGrid.deepCopy());
            for (AbstractLesson lesson : lessons) {
                // Случайный выбор ячейки
                List<CellForLesson> cells = new ArrayList<>(templateGrid.getScheduleGridMap().keySet());
                CellForLesson randomCell = cells.get(random.nextInt(cells.size()));

                // Проверка, что ячейка свободна
                if (lesson.getAllMaterialEntity().stream()
                        .allMatch(entity -> entity.isFree(chromosome.getScheduleGrid(), randomCell))) {
                    chromosome.getScheduleGrid().addLessonToCell(randomCell, lesson);
                }
            }
            chromosome.setFitness(FitnessCalculator.calculate(chromosome));
            population.add(chromosome);
        }
        return population;
    }
}
