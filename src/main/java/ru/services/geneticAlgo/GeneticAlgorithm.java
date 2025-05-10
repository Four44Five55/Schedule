package ru.services.geneticAlgo;

import ru.abstracts.AbstractLesson;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;

import java.util.*;
import java.util.stream.Collectors;

public class GeneticAlgorithm {
    public static ScheduleChromosome run(List<Lesson> lessons, ScheduleGrid templateGrid,
                                         int populationSize, int generations, double mutationRate) {
        // 1. Генерация начальной популяции
        List<ScheduleChromosome> population = PopulationGenerator.generate(
                populationSize, lessons, templateGrid
        );

        // 2. Эволюция
        for (int gen = 0; gen < generations; gen++) {
            System.out.println("Generation " + gen + " -------------------");
            System.out.println("Best fitness: " + population.get(0).getFitness());
            System.out.println("Lessons in best individual: " +
                    population.get(0).getScheduleGrid().getScheduleGridMap().values().stream()
                            .flatMap(List::stream).count());
            List<ScheduleChromosome> newPopulation = new ArrayList<>();

            // Вместо прямого копирования топ-10%:
            List<ScheduleChromosome> elites = population.stream()
                    .sorted(Comparator.comparingDouble(ScheduleChromosome::getFitness).reversed())
                    .limit(populationSize / 20) // Топ-5%
                    .collect(Collectors.toList());
            newPopulation.addAll(elites);

            // Заполняем популяцию
            while (newPopulation.size() < populationSize) {
                ScheduleChromosome parent1 = Selection.tournamentSelection(population, 5);
                ScheduleChromosome parent2 = Selection.tournamentSelection(population, 5);
                ScheduleChromosome child = Crossover.singlePointCrossover(parent1, parent2);
                Mutation.mutate(child, mutationRate);
                newPopulation.add(child);
            }

            population = newPopulation;
            System.out.println("Generation " + gen + ", Best Fitness: " + population.get(0).getFitness());
        }

        return population.get(0);
    }
}
