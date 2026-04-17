package ru.services.solver.genetic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.entity.*;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.Gene;
import ru.services.solver.model.Genome;
import ru.services.solver.model.SchedulableResource;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FitnessEvaluator {

    private final SolverConfig config;

    /**
     * Рассчитывает и устанавливает оценку (fitness) для генома.
     *
     * @param genome    Особь для оценки.
     * @param workspace Контекст (для доступа к статическим ограничениям ресурсов).
     */
    public void evaluate(Genome genome, ScheduleWorkspace workspace) {
        double score = 0.0;

        // Используем Sets для быстрого обнаружения дублей (Конфликтов)
        // Ключ: ID ресурса + Hash ячейки времени
        // Мы используем String ключи для простоты MVP, в high-load лучше использовать составные int ключи
        Set<String> occupiedEducators = new HashSet<>();
        Set<String> occupiedGroups = new HashSet<>();
        Set<String> occupiedAuditoriums = new HashSet<>();

        List<Gene> genes = genome.getGenes();

        for (Gene gene : genes) {
            CellForLesson slot = gene.getAssignedSlot();

            // 1. Штраф, если слот вообще не назначен (такого быть не должно, но на всякий случай)
            if (slot == null) {
                score += config.getHardConstraintPenalty();
                continue;
            }

            List<Lesson> lessons = gene.getLessons();
            List<Auditorium> auditoriums = gene.getAssignedAuditoriums();

            for (int i = 0; i < lessons.size(); i++) {
                Lesson lesson = lessons.get(i);
                Auditorium auditorium = auditoriums.get(i);

                // --- А. Проверка ПРЕПОДАВАТЕЛЕЙ ---
                for (Educator educator : lesson.getEducators()) {
                    String key = educator.getId() + "_" + slot.hashCode();
                    // Проверка на динамический конфликт (накладка занятий)
                    if (!occupiedEducators.add(key)) {
                        score += config.getHardConstraintPenalty();
                    }
                    // Проверка на статический конфликт (отпуск/выходной)
                    SchedulableResource resource = workspace.getResourceManager().getEducatorResource(educator.getId());
                    if (resource.hasConstraint(slot)) {
                        score += config.getStaticConstraintPenalty();
                    }
                }

                // --- Б. Проверка ГРУПП ---
                if (lesson.getStudyStream() != null) {
                    for (Group group : lesson.getStudyStream().getGroups()) {
                        String key = group.getId() + "_" + slot.hashCode();
                        if (!occupiedGroups.add(key)) {
                            score += config.getHardConstraintPenalty();
                        }
                        SchedulableResource resource = workspace.getResourceManager().getGroupResource(group.getId());
                        if (resource.hasConstraint(slot)) {
                            score += config.getStaticConstraintPenalty();
                        }
                    }
                }

                // --- В. Проверка АУДИТОРИЙ ---
                if (auditorium != null) {
                    String key = auditorium.getId() + "_" + slot.hashCode();
                    if (!occupiedAuditoriums.add(key)) {
                        score += config.getHardConstraintPenalty();
                    }
                    SchedulableResource resource = workspace.getResourceManager().getAuditoriumResource(auditorium.getId());
                    if (resource.hasConstraint(slot)) {
                        score += config.getStaticConstraintPenalty();
                    }
                } else {
                    // Аудитория не была назначена (не хватило места)
                    score += config.getMissingAuditoriumPenalty();
                }
                // --- Г. Проверка ПОРЯДКА ТЕМ (Sequential Constraint) ---
                // Группируем уроки по курсу для проверки хронологии
                Map<Integer, List<Gene>> genesByCourse = genes.stream()
                        .collect(Collectors.groupingBy(
                                g -> g.getLessons().get(0).getDisciplineCourse().getId()
                        ));

                for (List<Gene> courseGenes : genesByCourse.values()) {
                    // Сортируем гены по их "плановой" позиции (CurriculumSlot.position)
                    courseGenes.sort(Comparator.comparingInt(
                            g -> g.getLessons().get(0).getCurriculumSlot().getPosition()
                    ));

                    // Проверяем хронологию времени
                    for (i = 0; i < courseGenes.size() - 1; i++) {
                        Gene current = courseGenes.get(i);
                        Gene next = courseGenes.get(i + 1);

                        CellForLesson t1 = current.getAssignedSlot();
                        CellForLesson t2 = next.getAssignedSlot();

                        if (t1 != null && t2 != null) {
                            // Сравниваем даты и время
                            if (compareCells(t1, t2) > 0) {
                                // Нарушение порядка! Тема i+1 стоит раньше темы i
                                score += config.getOrderConstraintPenalty(); // Например, -50
                            }
                        }
                    }
                }
            }

            // Записываем результат в геном
            genome.setFitnessScore(score);
        }
    }
    private int compareCells(CellForLesson c1, CellForLesson c2) {
        int dateCmp = c1.getDate().compareTo(c2.getDate());
        if (dateCmp != 0) return dateCmp;
        return c1.getTimeSlotPair().compareTo(c2.getTimeSlotPair());
    }
}