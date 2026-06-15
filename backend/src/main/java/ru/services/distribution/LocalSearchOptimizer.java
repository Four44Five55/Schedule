package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.services.solver.PlacementOption;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LocalSearchOptimizer {

    private static final int MAX_ITERATIONS_WITHOUT_IMPROVEMENT = 20;
    private static final int MAX_TOTAL_ITERATIONS = 100;

    // =========================================================================
    // Точка входа
    // =========================================================================

    public void optimize(DistributionContext context) {
        log.info("=== LocalSearch: старт оптимизации ===");

        List<Lesson> practices = getAllPlacedPractices(context);
        if (practices.isEmpty()) {
            log.info("Нет размещённых практик для оптимизации");
            return;
        }

        // Снимок distributed — не меняется во время параллельного чтения
        List<Lesson> distributed = List.copyOf(context.getDistributedLessons());

        int initialScore = computeTotalScore(practices, context, distributed);
        log.info("  Score до оптимизации: {}", initialScore);

        // Предвычисляем кэш score один раз
        Map<Lesson, Integer> scoreCache = buildScoreCache(practices, context, distributed);

        // Предвычисляем карту дата → занятия
        Map<LocalDate, List<Lesson>> byDate = buildByDate(practices, context);
        List<LocalDate> sortedDates = byDate.keySet().stream().sorted().toList();

        int totalIterations = 0;
        int improvements = 0;
        int iterationsWithoutImprovement = 0;

        while (totalIterations < MAX_TOTAL_ITERATIONS
                && iterationsWithoutImprovement < MAX_ITERATIONS_WITHOUT_IMPROVEMENT) {

            totalIterations++;

            List<Lesson> shuffled = new ArrayList<>(practices);
            Collections.shuffle(shuffled);

            boolean found = tryFindAndApplySwap(
                    shuffled, byDate, sortedDates, scoreCache, context);

            if (found) {
                improvements++;
                iterationsWithoutImprovement = 0;
                // Обновляем снимок и карту после swap
                distributed = List.copyOf(context.getDistributedLessons());
                rebuildByDate(practices, byDate, context);
            } else {
                iterationsWithoutImprovement++;
            }
        }

        int finalScore = computeTotalScore(practices, context, distributed);
        log.info("=== LocalSearch: завершено ===");
        log.info("  Итераций: {}, улучшений: {}", totalIterations, improvements);
        log.info("  Score: {} → {} (улучшение: {})",
                initialScore, finalScore, initialScore - finalScore);
    }

    // =========================================================================
    // Поиск и применение swap
    // =========================================================================

    private boolean tryFindAndApplySwap(List<Lesson> lessons,
                                        Map<LocalDate, List<Lesson>> byDate,
                                        List<LocalDate> sortedDates,
                                        Map<Lesson, Integer> scoreCache,
                                        DistributionContext context) {
        List<Lesson> distributed = List.copyOf(context.getDistributedLessons());
        SwapCandidate best = null;

        for (Lesson a : lessons) {
            CellForLesson cellA = context.getWorkspace().getCellForLesson(a);
            if (cellA == null) continue;

            for (Lesson b : getNeighbors(cellA.getDate(), byDate, sortedDates)) {
                if (b.equals(a)) continue;
                CellForLesson cellB = context.getWorkspace().getCellForLesson(b);
                if (cellB == null) continue;
                if (cellA.getDate().equals(cellB.getDate())) continue;
                if (sameChain(a, b)) continue;

                // ★ Только score — никакого canSwap здесь
                int scoreBefore = scoreCache.getOrDefault(a, 0)
                        + scoreCache.getOrDefault(b, 0);
                int scoreAfter = lessonScore(a, cellB, context, distributed)
                        + lessonScore(b, cellA, context, distributed);
                int delta = scoreBefore - scoreAfter;

                if (delta > 0 && (best == null || delta > best.scoreDelta())) {
                    best = new SwapCandidate(a, b, cellA, cellB, delta);
                }
            }
        }

        if (best == null) return false;

        // ★ canSwap вызывается ОДИН РАЗ для лучшего кандидата
        if (canSwap(best.a(), best.b(), best.cellA(), best.cellB(), context)) {
            executeSwap(best.a(), best.b(), best.cellA(), best.cellB(), context);
            invalidateCache(scoreCache, best.a(), best.b(),
                    best.cellA(), best.cellB(), lessons, context);
            return true;
        }

        return false;
    }

    private SwapCandidate findBestSwapForLesson(Lesson a,
                                                Map<LocalDate, List<Lesson>> byDate,
                                                List<LocalDate> sortedDates,
                                                Map<Lesson, Integer> scoreCache,
                                                DistributionContext context,
                                                List<Lesson> distributed) {
        CellForLesson cellA = context.getWorkspace().getCellForLesson(a);
        if (cellA == null) return null;

        SwapCandidate best = null;

        for (Lesson b : getNeighbors(cellA.getDate(), byDate, sortedDates)) {
            if (b.equals(a)) continue;
            CellForLesson cellB = context.getWorkspace().getCellForLesson(b);
            if (cellB == null) continue;
            if (cellA.getDate().equals(cellB.getDate())) continue;
            if (sameChain(a, b)) continue;

            // Используем кэш для scoreBefore
            int scoreBefore = scoreCache.getOrDefault(a, 0)
                    + scoreCache.getOrDefault(b, 0);

            // scoreAfter считаем только если есть шанс улучшения
            int scoreAfterA = lessonScore(a, cellB, context, distributed);
            int scoreAfterB = lessonScore(b, cellA, context, distributed);
            int delta = scoreBefore - (scoreAfterA + scoreAfterB);

            if (delta > 0 && (best == null || delta > best.scoreDelta())) {
                best = new SwapCandidate(a, b, cellA, cellB, delta);
            }
        }

        return best;
    }

    private boolean canSwap(Lesson a, Lesson b,
                            CellForLesson cellA, CellForLesson cellB,
                            DistributionContext context) {
        context.getWorkspace().removePlacement(a);
        context.getWorkspace().removePlacement(b);

        boolean aIntoB = context.getWorkspace().findPlacementOption(a, cellB).isPossible();
        boolean bIntoA = context.getWorkspace().findPlacementOption(b, cellA).isPossible();

        context.getWorkspace().forcePlacement(a, cellA, a.getAssignedAuditoriums());
        context.getWorkspace().forcePlacement(b, cellB, b.getAssignedAuditoriums());

        return aIntoB && bIntoA;
    }

    private void executeSwap(Lesson a, Lesson b,
                             CellForLesson cellA, CellForLesson cellB,
                             DistributionContext context) {
        context.getWorkspace().removePlacement(a);
        context.getWorkspace().removePlacement(b);

        PlacementOption optionA = context.getWorkspace().findPlacementOption(a, cellB);
        PlacementOption optionB = context.getWorkspace().findPlacementOption(b, cellA);

        context.getWorkspace().executePlacement(optionA);
        context.getWorkspace().executePlacement(optionB);
    }

    // =========================================================================
    // Функция оценки (меньше = лучше)
    // =========================================================================

    private int computeTotalScore(List<Lesson> lessons,
                                  DistributionContext context,
                                  List<Lesson> distributed) {
        return lessons.stream()
                .mapToInt(l -> lessonScore(
                        l, context.getWorkspace().getCellForLesson(l), context, distributed))
                .sum();
    }

    /**
     * Score одного занятия. Все score-методы используют переданный снимок distributed.
     */
    private int lessonScore(Lesson lesson, CellForLesson cell,
                            DistributionContext context,
                            List<Lesson> distributed) {
        if (cell == null) return 10000;
        return compactnessScore(lesson, cell, distributed, context)
                + uniformityScore(lesson, cell, distributed, context)
                + chronologyScore(lesson, cell, distributed, context)
                + lonelinesScore(lesson, cell, distributed, context);
    }

    /**
     * Компактность: штраф за разрыв > 1 дня до ближайшего занятия преподавателя.
     */
    private int compactnessScore(Lesson lesson, CellForLesson cell,
                                 List<Lesson> distributed,
                                 DistributionContext context) {
        Set<Integer> educatorIds = lesson.getEducators().stream()
                .map(Educator::getId)
                .collect(Collectors.toSet());
        LocalDate date = cell.getDate();

        OptionalLong minGap = distributed.stream()
                .filter(l -> !l.equals(lesson))
                .filter(l -> l.getEducators().stream()
                        .anyMatch(e -> educatorIds.contains(e.getId())))
                .map(l -> context.getWorkspace().getCellForLesson(l))
                .filter(Objects::nonNull)
                .mapToLong(c -> Math.abs(ChronoUnit.DAYS.between(date, c.getDate())))
                .filter(gap -> gap > 0)
                .min();

        if (minGap.isEmpty()) return 0;
        long gap = minGap.getAsLong();
        return gap > 1 ? (int) gap : 0;
    }

    /**
     * Равномерность: штраф за отклонение от идеальной даты позиции.
     */
    private int uniformityScore(Lesson lesson, CellForLesson cell,
                                List<Lesson> distributed,
                                DistributionContext context) {
        String key = getDisciplineKey(lesson);

        List<Lesson> group = distributed.stream()
                .filter(l -> getDisciplineKey(l).equals(key))
                .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                .toList();

        if (group.size() < 2) return 0;

        List<LocalDate> dates = group.stream()
                .map(l -> context.getWorkspace().getCellForLesson(l))
                .filter(Objects::nonNull)
                .map(CellForLesson::getDate)
                .sorted()
                .toList();

        if (dates.size() < 2) return 0;

        LocalDate first = dates.getFirst();
        LocalDate last = dates.getLast();
        long totalDays = ChronoUnit.DAYS.between(first, last);
        if (totalDays == 0) return 0;

        int posIndex = group.indexOf(lesson);
        if (posIndex < 0) return 0;

        long idealOffset = totalDays * posIndex / (group.size() - 1);
        LocalDate idealDate = first.plusDays(idealOffset);

        long deviation = Math.abs(ChronoUnit.DAYS.between(cell.getDate(), idealDate));
        return (int) (deviation / 3);
    }

    /**
     * Хронология: штраф за нарушение порядка позиций.
     */
    private int chronologyScore(Lesson lesson, CellForLesson cell,
                                List<Lesson> distributed,
                                DistributionContext context) {
        String key = getDisciplineKey(lesson);
        int position = lesson.getCurriculumSlot().getPosition();
        LocalDate date = cell.getDate();
        int penalty = 0;

        for (Lesson other : distributed) {
            if (other.equals(lesson)) continue;
            if (!getDisciplineKey(other).equals(key)) continue;

            CellForLesson otherCell = context.getWorkspace().getCellForLesson(other);
            if (otherCell == null) continue;

            int otherPos = other.getCurriculumSlot().getPosition();
            LocalDate otherDate = otherCell.getDate();

            if (position < otherPos && date.isAfter(otherDate)) {
                penalty += (int) ChronoUnit.DAYS.between(otherDate, date) * 3;
            }
        }

        return penalty;
    }

    /**
     * Одиночность: штраф если у преподавателя в этот день только одно занятие.
     */
    private int lonelinesScore(Lesson lesson, CellForLesson cell,
                               List<Lesson> distributed,
                               DistributionContext context) {
        Set<Integer> educatorIds = lesson.getEducators().stream()
                .map(Educator::getId)
                .collect(Collectors.toSet());
        LocalDate date = cell.getDate();

        long lessonsOnSameDay = distributed.stream()
                .filter(l -> !l.equals(lesson))
                .filter(l -> l.getEducators().stream()
                        .anyMatch(e -> educatorIds.contains(e.getId())))
                .map(l -> context.getWorkspace().getCellForLesson(l))
                .filter(Objects::nonNull)
                .filter(c -> c.getDate().equals(date))
                .count();

        return lessonsOnSameDay == 0 ? 20 : 0;
    }

    // =========================================================================
    // Кэш и вспомогательные методы
    // =========================================================================

    private Map<Lesson, Integer> buildScoreCache(List<Lesson> practices,
                                                 DistributionContext context,
                                                 List<Lesson> distributed) {
        Map<Lesson, Integer> cache = new HashMap<>();
        for (Lesson l : practices) {
            CellForLesson cell = context.getWorkspace().getCellForLesson(l);
            cache.put(l, lessonScore(l, cell, context, distributed));
        }
        return cache;
    }

    private void invalidateCache(Map<Lesson, Integer> scoreCache,
                                 Lesson a, Lesson b,
                                 CellForLesson oldCellA, CellForLesson oldCellB,
                                 List<Lesson> practices,
                                 DistributionContext context) {
        Set<Integer> affectedEducators = new HashSet<>();
        a.getEducators().forEach(e -> affectedEducators.add(e.getId()));
        b.getEducators().forEach(e -> affectedEducators.add(e.getId()));
        Set<LocalDate> affectedDates = Set.of(oldCellA.getDate(), oldCellB.getDate());

        List<Lesson> distributed = List.copyOf(context.getDistributedLessons());

        for (Lesson l : practices) {
            CellForLesson cell = context.getWorkspace().getCellForLesson(l);
            if (cell == null) continue;

            boolean affectedByEducator = l.getEducators().stream()
                    .anyMatch(e -> affectedEducators.contains(e.getId()));
            boolean affectedByDate = affectedDates.contains(cell.getDate());

            if (affectedByEducator || affectedByDate) {
                scoreCache.put(l, lessonScore(l, cell, context, distributed));
            }
        }
    }

    private Map<LocalDate, List<Lesson>> buildByDate(List<Lesson> practices,
                                                     DistributionContext context) {
        Map<LocalDate, List<Lesson>> byDate = new HashMap<>();
        practices.forEach(l -> {
            CellForLesson cell = context.getWorkspace().getCellForLesson(l);
            if (cell != null)
                byDate.computeIfAbsent(cell.getDate(), k -> new ArrayList<>()).add(l);
        });
        return byDate;
    }

    private void rebuildByDate(List<Lesson> practices,
                               Map<LocalDate, List<Lesson>> byDate,
                               DistributionContext context) {
        byDate.clear();
        practices.forEach(l -> {
            CellForLesson cell = context.getWorkspace().getCellForLesson(l);
            if (cell != null)
                byDate.computeIfAbsent(cell.getDate(), k -> new ArrayList<>()).add(l);
        });
    }

    private List<Lesson> getNeighbors(LocalDate date,
                                      Map<LocalDate, List<Lesson>> byDate,
                                      List<LocalDate> sortedDates) {
        return sortedDates.stream()
                .filter(d -> !d.equals(date))
                .filter(d -> Math.abs(ChronoUnit.DAYS.between(date, d)) <= 14)
                .flatMap(d -> byDate.getOrDefault(d, List.of()).stream())
                .toList();
    }

    private List<Lesson> getAllPlacedPractices(DistributionContext context) {
        return context.getDistributedLessons().stream()
                .filter(l -> l.getKindOfStudy() != null
                        && l.getKindOfStudy() != KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    private boolean sameChain(Lesson a, Lesson b) {
        if (a.getStudyStream() == null || b.getStudyStream() == null) return false;
        return getDisciplineKey(a).equals(getDisciplineKey(b))
                && a.getCurriculumSlot().getPosition() == b.getCurriculumSlot().getPosition();
    }

    private String getDisciplineKey(Lesson lesson) {
        int streamId = lesson.getStudyStream() != null
                ? lesson.getStudyStream().getId() : -1;
        int disciplineId = lesson.getDisciplineCourse().getDiscipline().getId();
        return streamId + "_" + disciplineId;
    }

    private record SwapCandidate(
            Lesson a,
            Lesson b,
            CellForLesson cellA,
            CellForLesson cellB,
            int scoreDelta
    ) {}
}