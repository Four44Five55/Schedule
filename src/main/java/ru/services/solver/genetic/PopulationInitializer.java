package ru.services.solver.genetic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.entity.*;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.Gene;
import ru.services.solver.model.Genome;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PopulationInitializer {

    private final GeneConverter geneConverter;

    public List<Genome> initializePopulation(ScheduleWorkspace workspace, List<Lesson> rawLessons, int populationSize) {
        List<Gene> masterGenes = geneConverter.convertToGenes(rawLessons);
        List<Genome> population = new ArrayList<>(populationSize);
        List<CellForLesson> allCells = CellForLessonFactory.getAllCells();

        for (int i = 0; i < populationSize; i++) {
            // 1. Очищаем черновик перед созданием новой особи
            workspace.clear();

            // 2. Создаем копии генов для новой особи
            List<Gene> individualGenes = copyGenes(masterGenes);

            // 3. Генерируем (используя логику Workspace для проверки конфликтов)
            Genome genome = createGenome(workspace, individualGenes, allCells);
            population.add(genome);
        }

        // Очищаем за собой, чтобы Workspace остался чистым для алгоритма
        workspace.clear();

        return population;
    }

    private Genome createGenome(ScheduleWorkspace workspace, List<Gene> genes, List<CellForLesson> cells) {
        // Рандомизация порядка
        //Collections.shuffle(genes);

        List<CellForLesson> shuffledCells = new ArrayList<>(cells);
        Collections.shuffle(shuffledCells);

        for (Gene gene : genes) {
            boolean placed = false;

            for (CellForLesson cell : shuffledCells) {
                // Ищем место.
                // findPlacementOption теперь проверяет И статику (отпуска),
                // И динамику (занятость другими генами этой особи, которые мы уже внесли через forcePlacement)
                PlacementOption option = workspace.findPlacementOption(lessonFromGene(gene), cell); // Передаем любой урок для проверки ресурсов, т.к. они связаны

                // Тут нужен нюанс: findPlacementOption принимает Lesson.
                // Нам нужно проверить ВСЕ уроки в гене.
                if (canPlaceGene(workspace, gene, cell)) {
                    // Место найдено! Записываем в Workspace, чтобы занять ресурсы
                    placeGeneToWorkspace(workspace, gene, cell);
                    placed = true;
                    break;
                }
            }

            // Аварийный режим
            if (!placed) {
                CellForLesson randomCell = shuffledCells.get(0);
                // Принудительно занимаем ресурсы, чтобы создать конфликт
                forceGeneToWorkspace(workspace, gene, randomCell);
            }
        }

        // Возвращаем геном с оригинальным порядком (важно для кроссовера)
        // Но с заполненными полями assignedSlot/Auditoriums
        return new Genome(genes);
    }

    private boolean canPlaceGene(ScheduleWorkspace workspace, Gene gene, CellForLesson cell) {
        for (Lesson lesson : gene.getLessons()) {
            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            if (!option.isPossible()) return false;
        }
        return true;
    }

    private void placeGeneToWorkspace(ScheduleWorkspace workspace, Gene gene, CellForLesson cell) {
        gene.setAssignedSlot(cell);
        List<Auditorium> assignedAuditoriums = new ArrayList<>();

        for (Lesson lesson : gene.getLessons()) {
            PlacementOption option = workspace.findPlacementOption(lesson, cell);
            // Берем подобранную аудиторию
            Auditorium aud = (option.assignedAuditoriums() != null && !option.assignedAuditoriums().isEmpty())
                    ? option.assignedAuditoriums().get(0) : null;
            assignedAuditoriums.add(aud);

            // ВАЖНО: Записываем в черновик (workspace), чтобы заблокировать ресурсы
            // Используем forcePlacement, так как он атомарный
            workspace.forcePlacement(lesson, cell, (aud != null) ? List.of(aud) : null);
        }
        gene.setAssignedAuditoriums(assignedAuditoriums);
    }

    private void forceGeneToWorkspace(ScheduleWorkspace workspace, Gene gene, CellForLesson cell) {
        gene.setAssignedSlot(cell);

        List<Auditorium> nullAuds = new ArrayList<>();
        for (Lesson l : gene.getLessons()) nullAuds.add(null);
        gene.setAssignedAuditoriums(nullAuds);

        for (Lesson lesson : gene.getLessons()) {
            // Принудительно занимаем преподавателя и группу (без аудитории)
            workspace.forcePlacement(lesson, cell, null);
        }
    }

    private Lesson lessonFromGene(Gene gene) {
        return gene.getLessons().get(0);
    }

    private List<Gene> copyGenes(List<Gene> source) {
        List<Gene> copy = new ArrayList<>(source.size());
        for (Gene g : source) {
            copy.add(g.copy());
        }
        return copy;
    }
}