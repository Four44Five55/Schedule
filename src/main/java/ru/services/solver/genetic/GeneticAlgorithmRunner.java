package ru.services.solver.genetic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.entity.Lesson;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.genetic.operators.CrossoverStrategy;
import ru.services.solver.genetic.operators.MutationStrategy;
import ru.services.solver.genetic.operators.SelectionStrategy;
import ru.services.solver.model.Genome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneticAlgorithmRunner {

    private final PopulationInitializer initializer;
    private final FitnessEvaluator evaluator;
    private final SelectionStrategy selection;
    private final CrossoverStrategy crossover;
    private final MutationStrategy mutation;
    private final SolverConfig config;

    /**
     * Запускает генетический алгоритм.
     */
    public Genome run(ScheduleWorkspace workspace, List<Lesson> lessons) {
        long startTime = System.currentTimeMillis();



        // 1. Инициализация (Фаза 1)
        List<Genome> population = initializer.initializePopulation(
                workspace, lessons, config.getPopulationSize());

        // Первичная оценка
        evaluatePopulation(population, workspace);

        Genome globalBest = getBest(population);
        log.info("Initial best fitness: {}", globalBest.getFitnessScore());

        // 2. Эволюция (Фаза 2)
        int maxGenerations = config.getMaxGenerations();

        for (int generation = 0; generation < maxGenerations; generation++) {
            List<Genome> newPopulation = new ArrayList<>(config.getPopulationSize());

            // Элитизм: сохраняем лучшего без изменений
            newPopulation.add(getBest(population).copy());

            // Пока не заполним новую популяцию
            while (newPopulation.size() < config.getPopulationSize()) {
                // Селекция
                Genome parent1 = selection.select(population);
                Genome parent2 = selection.select(population);

                // Скрещивание
                Genome child = crossover.crossover(parent1, parent2);

                // Мутация
                mutation.mutate(child);

                newPopulation.add(child);
            }

            population = newPopulation;
            evaluatePopulation(population, workspace);

            // Обновляем глобального лидера
            Genome currentBest = getBest(population);
            if (currentBest.getFitnessScore() > globalBest.getFitnessScore()) {
                globalBest = currentBest.copy(); // Сохраняем копию
                log.info("Generation {}: New best fitness = {}", generation, globalBest.getFitnessScore());
            }

            // Опционально: условие раннего выхода (если нашли идеальное решение 0.0)
            if (globalBest.getFitnessScore() >= -0.001) {
                log.info("Found optimal solution!");
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("GA finished in {} ms. Best score: {}", (endTime - startTime), globalBest.getFitnessScore());

        return globalBest;
    }

    private void evaluatePopulation(List<Genome> population, ScheduleWorkspace workspace) {
        // Можно распараллелить stream().parallel(), так как evaluate не меняет shared state
        population.parallelStream().forEach(genome -> {
            if (genome.getFitnessScore() == Double.NEGATIVE_INFINITY) {
                evaluator.evaluate(genome, workspace);
            }
        });
    }

    private Genome getBest(List<Genome> population) {
        return population.stream()
                .max(Comparator.comparingDouble(Genome::getFitnessScore))
                .orElseThrow();
    }
}
