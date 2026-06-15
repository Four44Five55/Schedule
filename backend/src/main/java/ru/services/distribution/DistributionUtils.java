package ru.services.distribution;

import ru.entity.Group;
import ru.entity.Lesson;
import ru.services.solver.ScheduleWorkspace;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Утилитные методы для распределения.
 * Вынесены методы из DistributionDiscipline:
 * - hasCommonGroups()
 * - findPreviousLesson()
 */
public class DistributionUtils {
    private final DistributionContext context;
    private final ScheduleWorkspace workspace;

    public DistributionUtils(DistributionContext context) {
        this.context = context;
        this.workspace = context.getWorkspace();
    }

    /**
     * Проверяет, есть ли общие группы между двумя наборами.
     */
    public static boolean hasCommonGroups(Set<Group> groups1, Set<Group> groups2) {
        return !Collections.disjoint(groups1, groups2);
    }

    /**
     * Находит предыдущее занятие для указанного урока.
     */
    public Lesson findPreviousLesson(Lesson currentLesson) {
        if (currentLesson == null || currentLesson.getCurriculumSlot() == null) {
            return null;
        }

        Integer currentDisciplineId = currentLesson.getDisciplineCourse().getDiscipline().getId();
        Integer currentSlotId = currentLesson.getCurriculumSlot().getId();
        Set<Group> currentGroups = currentLesson.getStudyStream() != null
                ? currentLesson.getStudyStream().getGroups()
                : Collections.emptySet();

        List<Lesson> lessons = context.getLessons();
        int currentIndex = lessons.indexOf(currentLesson);

        for (int i = currentIndex - 1; i >= 0; i--) {
            Lesson candidate = lessons.get(i);

            boolean sameDiscipline = candidate.getDisciplineCourse().getDiscipline().getId().equals(currentDisciplineId);
            boolean earlierSlot = candidate.getCurriculumSlot().getId() < currentSlotId;

            if (sameDiscipline && earlierSlot) {
                Set<Group> candidateGroups = candidate.getStudyStream() != null
                        ? candidate.getStudyStream().getGroups()
                        : Collections.emptySet();

                if (hasCommonGroups(currentGroups, candidateGroups)) {
                    return candidate;
                }
            }
        }

        return null;
    }
}
