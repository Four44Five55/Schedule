package ru.services.geneticAlgo;

import java.util.*;

public class Selection {
    public static ScheduleChromosome tournamentSelection(List<ScheduleChromosome> population, int tournamentSize) {
        Random random = new Random();
        ScheduleChromosome best = null;
        Set<Integer> selectedIndices = new HashSet<>(); // Чтобы избежать повторов

        while (selectedIndices.size() < tournamentSize) {
            int candidateIndex = random.nextInt(population.size());
            if (selectedIndices.add(candidateIndex)) {
                ScheduleChromosome candidate = population.get(candidateIndex);
                if (best == null || candidate.getFitness() > best.getFitness()) {
                    best = candidate;
                }
            }
        }
        return best;
    }
}
