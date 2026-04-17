package ru.services.solver.genetic.operators;

import org.springframework.stereotype.Component;
import ru.services.solver.model.Genome;

import java.util.List;
import java.util.Random;

@Component
public class SelectionStrategy {
    private final Random random = new Random();
    private static final int TOURNAMENT_SIZE = 5; // Размер турнира

    /**
     * Выбирает одного родителя из популяции с помощью турнира.
     */
    public Genome select(List<Genome> population) {
        Genome best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Genome candidate = population.get(random.nextInt(population.size()));
            if (best == null || candidate.getFitnessScore() > best.getFitnessScore()) {
                best = candidate;
            }
        }
        return best; // Возвращаем лучшего из случайных (не копию, просто ссылку)
    }
}
