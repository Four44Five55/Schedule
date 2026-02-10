package ru.services.solver.genetic.operators;

import org.springframework.stereotype.Component;
import ru.entity.CellForLesson;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.model.Gene;
import ru.services.solver.model.Genome;

import java.util.List;
import java.util.Random;

@Component
public class MutationStrategy {
    private final Random random = new Random();
    private static final double MUTATION_RATE = 0.05; // 5% шанс мутации для каждого гена

    /**
     * Подвергает геном мутации (изменяет состояние на месте).
     */
    public void mutate(Genome genome) {
        List<CellForLesson> allCells = CellForLessonFactory.getAllCells();
        if (allCells.isEmpty()) return;

        boolean mutated = false;
        for (Gene gene : genome.getGenes()) {
            if (gene.isPinned()) continue;

            // Проходим по каждому гену и с маленькой вероятностью меняем его
            if (random.nextDouble() < MUTATION_RATE) {
                // Выбираем новый случайный слот
                CellForLesson newSlot = allCells.get(random.nextInt(allCells.size()));
                gene.setAssignedSlot(newSlot);

                // TODO: Здесь стоит перевыбрать аудиторию, если старая занята.
                // Для MVP пока оставляем старую или сбрасываем.
                // gene.setAssignedAuditoriums(...)

                mutated = true;
            }
        }

        if (mutated) {
            genome.invalidateFitness(); // Оценка устарела
        }
    }
}
