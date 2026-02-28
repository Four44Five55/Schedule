package ru.services.distribution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.services.LessonSortingService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Вынесен из DistributionDiscipline.sortEducatorsByPriority()
 * Приоритет 1: количество групп в лекциях (по убыванию)
 * Приоритет 2: общее количество занятий (по убыванию)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EducatorPrioritizer {
    private final LessonSortingService lessonSortingService;

    /**
     * Сортирует преподавателей по приоритету распределения.
     * Приоритет 1: количество групп в лекциях (по убыванию)
     * Приоритет 2: общее количество занятий (по убыванию)
     *
     * @param educators список преподавателей
     * @param lessons   все занятия
     * @return отсортированный список преподавателей
     */
    public List<Educator> sortByPriority(List<Educator> educators, List<Lesson> lessons) {
        // Предварительный подсчёт метрик для сортировки
        Map<Educator, Integer> lectureGroupsCount = new HashMap<>();
        Map<Educator, Integer> totalLessonsCount = new HashMap<>();

        for (Educator educator : educators) {
            // Подсчёт уникальных групп в лекциях
            Set<Group> groupsInLectures = lessons.stream()
                    .filter(l -> l.getEducators().contains(educator))
                    .filter(l -> l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE)
                    .flatMap(l -> l.getStudyStream().getGroups().stream())
                    .collect(Collectors.toSet());
            lectureGroupsCount.put(educator, groupsInLectures.size());

            // Подсчёт общего количества занятий
            long totalCount = lessons.stream()
                    .filter(l -> l.getEducators().contains(educator))
                    .count();
            totalLessonsCount.put(educator, (int) totalCount);
        }

        // Комбинированная сортировка
        List<Educator> sortedEducators = new ArrayList<>(educators);
        sortedEducators.sort((e1, e2) -> {
            // Приоритет 1: количество групп в лекциях (по убыванию)
            int groupsCompare = Integer.compare(
                    lectureGroupsCount.getOrDefault(e2, 0),
                    lectureGroupsCount.getOrDefault(e1, 0)
            );
            if (groupsCompare != 0) return groupsCompare;

            // Приоритет 2: общее количество занятий (по убыванию)
            return Integer.compare(
                    totalLessonsCount.getOrDefault(e2, 0),
                    totalLessonsCount.getOrDefault(e1, 0)
            );
        });

        // Логирование порядка распределения
        log.info("=== Порядок распределения преподавателей ===");
        for (int i = 0; i < sortedEducators.size(); i++) {
            Educator e = sortedEducators.get(i);
            log.info("{}. {} - групп в лекциях: {}, всего занятий: {}",
                    i + 1, e.getName(),
                    lectureGroupsCount.get(e),
                    totalLessonsCount.get(e));
        }

        return sortedEducators;
    }
}
