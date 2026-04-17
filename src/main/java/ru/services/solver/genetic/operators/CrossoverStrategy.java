package ru.services.solver.genetic.operators;

import org.springframework.stereotype.Component;
import ru.services.solver.model.Gene;
import ru.services.solver.model.Genome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class CrossoverStrategy {
    private final Random random = new Random();

    /**
     * Создает потомка из двух родителей.
     */
    public Genome crossover(Genome parent1, Genome parent2) {
        List<Gene> childGenes = new ArrayList<>(parent1.getGenes().size());

        // Предполагаем, что гены в обоих родителях идут в одном порядке (это гарантирует GeneConverter)
        for (int i = 0; i < parent1.getGenes().size(); i++) {
            Gene gene1 = parent1.getGenes().get(i);
            Gene gene2 = parent2.getGenes().get(i);

            // 50% вероятность выбора родителя
            if (random.nextBoolean()) {
                childGenes.add(gene1.copy());
            } else {
                childGenes.add(gene2.copy());
            }
        }

        return new Genome(childGenes);
    }
}
