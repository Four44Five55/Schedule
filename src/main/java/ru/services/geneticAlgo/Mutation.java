package ru.services.geneticAlgo;
import ru.abstracts.AbstractLesson;
import ru.entity.*;
import java.util.*;

public class Mutation {
    public static void mutate(ScheduleChromosome chromosome, double mutationRate) {
        Random random = new Random();
        ScheduleGrid grid = chromosome.getScheduleGrid();
        List<CellForLesson> allCells = new ArrayList<>(grid.getScheduleGridMap().keySet());

        for (CellForLesson cell : allCells) {
            if (random.nextDouble() < mutationRate) {
                List<AbstractLesson> lessons = new ArrayList<>(grid.getListLessonInCell(cell));
                if (!lessons.isEmpty()) {
                    AbstractLesson lesson = lessons.get(random.nextInt(lessons.size()));
                    List<CellForLesson> freeCells = findFreeCellsForLesson(grid, lesson);

                    if (!freeCells.isEmpty()) {
                        // Удаляем из старой ячейки
                        grid.getScheduleGridMap().get(cell).remove(lesson);
                        // Добавляем в новую
                        CellForLesson newCell = freeCells.get(random.nextInt(freeCells.size()));
                        grid.getScheduleGridMap().get(newCell).add(lesson);
                    }
                }
            }
        }
        chromosome.setFitness(FitnessCalculator.calculate(chromosome));
    }

    private static List<CellForLesson> findFreeCellsForLesson(ScheduleGrid grid, AbstractLesson lesson) {
        List<CellForLesson> freeCells = new ArrayList<>();
        for (CellForLesson cell : grid.getScheduleGridMap().keySet()) {
            if (lesson.getAllMaterialEntity().stream()
                    .allMatch(entity -> entity.isFree(grid, cell))) {
                freeCells.add(cell);
            }
        }
        return freeCells;
    }
}
