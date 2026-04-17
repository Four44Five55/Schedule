package ru.services.solver.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Геном (Genome) — представляет собой одно полное решение (вариант расписания).
 * Состоит из набора Генов.
 */
@Getter
@Setter
public class Genome {

    private final String id;

    /**
     * Хромосома: список всех перемещаемых блоков.
     */
    private final List<Gene> genes;

    /**
     * Текущая оценка качества (Fitness Score).
     * Рассчитывается FitnessEvaluator-ом.
     * Чем выше, тем лучше (или наоборот, зависит от стратегии, обычно штрафы < 0).
     */
    private double fitnessScore = Double.NEGATIVE_INFINITY;

    public Genome(List<Gene> genes) {
        this.id = UUID.randomUUID().toString();
        this.genes = genes;
    }

    /**
     * Создает глубокую копию генома (для мутаций, чтобы не портить родителя).
     */
    public Genome copy() {
        List<Gene> newGenes = new ArrayList<>(this.genes.size());
        for (Gene gene : this.genes) {
            newGenes.add(gene.copy()); // Глубокое копирование генов
        }
        Genome copy = new Genome(newGenes);
        copy.setFitnessScore(this.fitnessScore); // Копируем оценку (пока она актуальна)
        return copy;
    }

    /**
     * Сбрасывает кешированное значение фитнеса.
     * Нужно вызывать после любой мутации.
     */
    public void invalidateFitness() {
        this.fitnessScore = Double.NEGATIVE_INFINITY;
    }

    @Override
    public String toString() {
        return "Genome{fitness=" + fitnessScore + ", genes=" + genes.size() + "}";
    }
}
