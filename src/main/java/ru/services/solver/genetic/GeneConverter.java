package ru.services.solver.genetic;

import org.springframework.stereotype.Component;
import ru.entity.Lesson;
import ru.services.solver.model.Gene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GeneConverter {

    /**
     * Преобразует плоский список уроков в список Генов.
     * Уроки с одинаковым parallelGroupId объединяются в один Ген.
     */
    public List<Gene> convertToGenes(List<Lesson> lessons) {
        List<Gene> genes = new ArrayList<>();

        // 1. Разделяем уроки на "одиночные" и "параллельные"
        List<Lesson> singleLessons = new ArrayList<>();
        List<Lesson> parallelLessons = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getParallelGroupId() != null) {
                parallelLessons.add(lesson);
            } else {
                singleLessons.add(lesson);
            }
        }

        // 2. Создаем гены для одиночных уроков
        for (Lesson lesson : singleLessons) {
            genes.add(new Gene(List.of(lesson)));
        }

        // 3. Группируем параллельные по ID и создаем для них гены
        Map<String, List<Lesson>> groupedByParallel = parallelLessons.stream()
                .collect(Collectors.groupingBy(Lesson::getParallelGroupId));

        for (List<Lesson> group : groupedByParallel.values()) {
            genes.add(new Gene(group));
        }

        return genes;
    }
}
