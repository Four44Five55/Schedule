package ru.services.distribution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.services.LessonSortingService;
import ru.services.SlotChainService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис приоритизации преподавателей для распределения занятий.
 * <p>
 * Исходная сортировка (до распределения):
 * - Приоритет 1: количество групп в лекциях (по убыванию)
 * - Приоритет 2: общее количество занятий (по убыванию)
 * <p>
 * Пересортировка после 1 фазы (для практик):
 * - Приоритет 1: доля сцепленных занятий (по убыванию)
 * - Приоритет 2: количество доступных дней (по возрастанию)
 * - Приоритет 3: количество групп в потоке (по убыванию)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EducatorPrioritizer {
    private final LessonSortingService lessonSortingService;
    private final SlotChainService slotChainService;

    /**
     * Сортирует преподавателей по приоритету распределения (исходная сортировка).
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
        log.info("=== Порядок распределения преподавателей (исходный) ===");
        for (int i = 0; i < sortedEducators.size(); i++) {
            Educator e = sortedEducators.get(i);
            log.info("{}. {} - групп в лекциях: {}, всего занятий: {}",
                    i + 1, e.getName(),
                    lectureGroupsCount.get(e),
                    totalLessonsCount.get(e));
        }

        return sortedEducators;
    }

    /**
     * Пересортирует преподавателей после 1 фазы (лекций) для распределения практик.
     * <p>
     * Новая логика приоритизации:
     * - Приоритет 1: выше доля сцепленных занятий (сложнее разместить связку без разрыва)
     * - Приоритет 2: наименьшее количество доступных дней (чем меньше, тем выше приоритет)
     * - Приоритет 3: больше групп в потоке (потоковые занятия сложнее разместить)
     *
     * @param context     контекст распределения с состояниями после фазы 1
     * @param semesterEnd конец семестра для расчета доступных дней
     * @return пересортированный список преподавателей
     */
    public List<Educator> resortAfterPhase1(DistributionContext context, LocalDate semesterEnd) {
        List<Educator> educators = context.getEducators();
        List<Lesson> allLessons = context.getLessons();

        // Создаем сервисы для анализа
        LessonPlacementService placementService = new LessonPlacementService(context.getWorkspace(), context);
        LessonDateFinder dateFinder = new LessonDateFinder(context, placementService);

        // Метрики для сортировки
        Map<Educator, EducatorMetrics> metrics = new HashMap<>();

        for (Educator educator : educators) {
            // Получаем только нераспределенные практики преподавателя
            List<Lesson> undistributedPractices = allLessons.stream()
                    .filter(l -> l.getEducators().contains(educator))
                    .filter(l -> l.getKindOfStudy() != KindOfStudy.LECTURE)
                    .filter(l -> !context.isLessonDistributed(l))
                    .toList();

            if (undistributedPractices.isEmpty()) {
                // Нет нераспределенных практик - низкий приоритет
                metrics.put(educator, new EducatorMetrics(Integer.MAX_VALUE, 0, 0));
                continue;
            }

            // 1. Подсчитываем количество доступных дней
            int availableDays = countAvailableDays(undistributedPractices, dateFinder, semesterEnd);

            // 2. Подсчитываем количество групп (потоковость)
            int totalGroups = countTotalGroups(undistributedPractices);

            // 3. Подсчитываем долю сцепленных занятий (соотношение к общему числу)
            double chainedRatio = calculateChainedRatio(undistributedPractices);

            metrics.put(educator, new EducatorMetrics(availableDays, totalGroups, chainedRatio));
        }

        // Сортировка по новым критериям
        List<Educator> sortedEducators = new ArrayList<>(educators);
        sortedEducators.sort((e1, e2) -> {
            EducatorMetrics m1 = metrics.get(e1);
            EducatorMetrics m2 = metrics.get(e2);

            // Приоритет 1: выше доля цепочек = выше приоритет
            int chainCompare = Double.compare(m2.chainedRatio, m1.chainedRatio);
            if (chainCompare != 0) return chainCompare;

            // Приоритет 2: меньше доступных дней = выше приоритет
            int daysCompare = Integer.compare(m1.availableDays, m2.availableDays);
            if (daysCompare != 0) return daysCompare;

            // Приоритет 3: больше групп = выше приоритет (потоковые сложнее разместить)
            return Integer.compare(m2.totalGroups, m1.totalGroups);
        });

        // Логирование нового порядка
        log.info("=== Порядок распределения преподавателей (после фазы 1) ===");
        for (int i = 0; i < sortedEducators.size(); i++) {
            Educator e = sortedEducators.get(i);
            EducatorMetrics m = metrics.get(e);
            log.info("{}. {} - доступных дней: {}, групп: {}, доля цепочек: {}%",
                    i + 1, e.getName(), m.availableDays, m.totalGroups,
                    (int) (m.chainedRatio * 100));
        }

        return sortedEducators;
    }

    /**
     * Подсчитывает количество доступных дней для списка занятий.
     * День считается доступным, если в нем можно разместить хотя бы одно занятие.
     */
    private int countAvailableDays(List<Lesson> lessons, LessonDateFinder dateFinder, LocalDate semesterEnd) {
        Set<LocalDate> availableDates = new HashSet<>();

        for (Lesson lesson : lessons) {
            List<LocalDate> dates = dateFinder.getAvailableDates(lesson, semesterEnd);
            availableDates.addAll(dates);
        }

        return availableDates.size();
    }

    /**
     * Подсчитывает общее количество уникальных групп в занятиях.
     */
    private int countTotalGroups(List<Lesson> lessons) {
        return lessons.stream()
                .filter(l -> l.getStudyStream() != null)
                .filter(l -> l.getStudyStream().getGroups() != null)
                .flatMap(l -> l.getStudyStream().getGroups().stream())
                .map(Group::getId)
                .collect(Collectors.toSet())
                .size();
    }

    /**
     * Вычисляет долю сцепленных занятий относительно общего числа занятий.
     * <p>
     * Логика:
     * - Занятие считается сцепленным, если оно входит в цепочку длиной > 1
     * - Возвращает долю от 0.0 (нет цепочек) до 1.0 (все в цепочках)
     * - Чем выше доля, тем сложнее разместить (выше приоритет)
     */
    private double calculateChainedRatio(List<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return 0.0;
        }

        // Находим все занятия, которые входят в цепочки
        Set<Integer> chainedSlotIds = new HashSet<>();

        for (Lesson lesson : lessons) {
            List<Integer> chainIds = slotChainService.getFullChain(lesson.getCurriculumSlot().getId());
            // Если цепочка длиннее 1, значит это сцепленное занятие
            if (chainIds.size() > 1) {
                chainedSlotIds.addAll(chainIds);
            }
        }

        // Считаем сколько занятий из списка входят в цепочки
        long chainedCount = lessons.stream()
                .filter(l -> chainedSlotIds.contains(l.getCurriculumSlot().getId()))
                .count();

        return (double) chainedCount / lessons.size();
    }

    /**
     * DTO для хранения метрик преподавателя при пересортировке.
     */
    private record EducatorMetrics(
            int availableDays,    // Количество доступных дней (меньше = выше приоритет)
            int totalGroups,      // Общее количество групп (больше = выше приоритет)
            double chainedRatio   // Доля сцепленных занятий 0.0-1.0 (выше = выше приоритет)
    ) {}
}
