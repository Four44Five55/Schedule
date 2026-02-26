package ru.services.distribution.practice;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.LessonSortingService;
import ru.services.SlotChainService;
import ru.services.distribution.core.DistributionContext;
import ru.services.distribution.finder.DateFinder;
import ru.services.distribution.finder.DateFinderFactory;
import ru.services.distribution.placement.ChainPlacementHandler;
import ru.services.distribution.placement.LessonPlacementService;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Обрабатывает фазу 2 — распределение практик.
 * Вынесены методы из DistributionDiscipline:
 * - distributePracticesPhase()
 * - distributePracticesForEducator()
 * - placePracticeInDate()
 * - rollbackPractices()
 */
@Slf4j
public class PracticeDistributionHandler {
    private final DistributionContext context;
    private final LessonSortingService lessonSortingService;
    private final SlotChainService slotChainService;
    private final LessonPlacementService placementService;
    private final ChainPlacementHandler chainHandler;
    private final PracticeSwapService swapService;

    public PracticeDistributionHandler(DistributionContext context,
                                       LessonSortingService lessonSortingService,
                                       SlotChainService slotChainService,
                                       LessonPlacementService placementService,
                                       ChainPlacementHandler chainHandler,
                                       PracticeSwapService swapService) {
        this.context = context;
        this.lessonSortingService = lessonSortingService;
        this.slotChainService = slotChainService;
        this.placementService = placementService;
        this.chainHandler = chainHandler;
        this.swapService = swapService;
    }

    /**
     * Распределяет практики для всех преподавателей.
     */
    public void distributePractices(LocalDate semesterEnd) {
        for (Educator educator : context.getEducators()) {
            distributePracticesForEducator(educator, semesterEnd);
        }
    }

    /**
     * Распределение практик для преподавателя.
     */
    public void distributePracticesForEducator(Educator educator, LocalDate semesterEnd) {
        log.info("=== НАЧАЛО: Распределение практик для преподавателя: {} ===", educator.getName());

        List<Lesson> practices = context.getLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .collect(Collectors.toList());

        if (practices.isEmpty()) {
            return;
        }

        log.info("Всего практик для распределения: {}", practices.size());

        long alreadyInSet = practices.stream().filter(context::isLessonDistributed).count();
        if (alreadyInSet > 0) {
            log.warn("ВНИМАНИЕ: {} из {} практик УЖЕ находятся в distributedLessonsSet",
                    alreadyInSet, practices.size());
        }

        List<Lesson> sortedPractices = lessonSortingService.getSortedLessons(practices);

        Set<LocalDate> lectureDates = getDatesWithLecturesForEducator(educator);
        log.debug("Даты с лекциями (приоритетные): {}", lectureDates);

        int placedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Lesson practice : sortedPractices) {
            if (context.isLessonDistributed(practice)) {
                skippedCount++;
                continue;
            }

            List<Lesson> chain = chainHandler.getChainForLesson(practice, practices);

            boolean isFirstInChain = chain.isEmpty() || chain.getFirst().equals(practice);
            if (!isFirstInChain) {
                log.debug("Практика ID={} не первая в цепочке (пропуск)", practice.getCurriculumSlot().getId());
                skippedCount++;
                continue;
            }

            LocalDate minDate = placementService.findMinDate(practice, educator);

            if (minDate == null) {
                log.warn("minDate is null для практики ID={}. Пропуск.", practice.getCurriculumSlot().getId());
                failedCount += chain.size();
                continue;
            }
            if (minDate.isAfter(semesterEnd)) {
                log.warn("minDate ({}) после конца семестра ({}). Практика ID={} не может быть размещена!",
                        minDate, semesterEnd, practice.getCurriculumSlot().getId());
                failedCount += chain.size();
                continue;
            }

            LocalDate targetDate;
            int chainSize = chain.size();
            boolean isChain = chainSize > 1;

            // Получаем все доступные даты
            List<LocalDate> allDates = CellForLessonFactory.getAllCells().stream()
                    .map(CellForLesson::getDate)
                    .distinct()
                    .filter(d -> !d.isBefore(minDate))
                    .filter(d -> !d.isAfter(semesterEnd))
                    .sorted()
                    .toList();

            // Используем DateFinder для поиска даты
            DateFinder dateFinder = DateFinderFactory.createFinder(educator, context);
            targetDate = dateFinder.findDate(practice, minDate, allDates, educator, lectureDates, semesterEnd);

            if (targetDate != null) {
                placePracticeInDate(practice, educator, practices, targetDate);
                placedCount += chainSize;
                CellForLesson cell = context.getWorkspace().getCellForLesson(practice);
                String themeNumber = practice.getCurriculumSlot().getThemeLesson() != null
                        ? practice.getCurriculumSlot().getThemeLesson().getThemeNumber()
                        : "N/A";
                log.info("✓ {} размещена: {}/{} {} №-{} дата {},{} размер цепочки: {}",
                        isChain ? "Цепочка" : "Практика",
                        practice.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                        themeNumber,
                        practice.getStudyStream().getGroups(),
                        practice.getCurriculumSlot().getPosition(),
                        targetDate,
                        cell != null ? cell.getTimeSlotPair() : "NULL",
                        chainSize);
            } else {
                log.warn("✗ Не удалось найти дату для {} ID={}. Пробуем swap...",
                        isChain ? "цепочки" : "практики",
                        practice.getCurriculumSlot().getId());

                if (!swapService.trySwap(practice, educator, practices, minDate, semesterEnd, lectureDates)) {
                    log.error("CRITICAL: Не удалось разместить {}: {} | Группы: {} | minDate: {} | semesterEnd: {}",
                            isChain ? "цепочку" : "практику",
                            practice.getKindOfStudy(),
                            practice.getStudyStream().getName(),
                            minDate,
                            semesterEnd);
                    failedCount += chainSize;
                } else {
                    log.info("✓ {} размещена через swap: {}", isChain ? "Цепочка" : "Практика", practice.getCurriculumSlot().getId());
                    placedCount += chainSize;
                }
            }
        }

        log.info("=== РЕЗУЛЬТАТ для {}: " +
                        "Всего практик={}, " +
                        "Размещено сейчас={}, " +
                        "Уже были размещены (пропущено)={}, " +
                        "Не удалось разместить={} ===",
                educator.getName(),
                sortedPractices.size(),
                placedCount,
                skippedCount,
                failedCount);
    }

    /**
     * Размещает практику в указанную дату.
     */
    private void placePracticeInDate(Lesson practice, Educator educator, List<Lesson> educatorLessons, LocalDate targetDate) {
        List<Lesson> chain = chainHandler.getChainForLesson(practice, educatorLessons);

        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(targetDate);
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        if (chainHandler.tryPlaceChainInDay(chain, dayCells, false)) {
            CellForLesson placedCell = context.getWorkspace().getCellForLesson(practice);
            if (placedCell != null) {
                log.info("DEBUG: практика размещена на слоте: {}", placedCell.getTimeSlotPair());
            }
        } else {
            log.warn("Не удалось разместить цепочку в дату {}", targetDate);
        }
    }

    /**
     * Возвращает даты, в которые у преподавателя уже есть лекции.
     */
    private Set<LocalDate> getDatesWithLecturesForEducator(Educator educator) {
        Set<LocalDate> dates = new HashSet<>();
        for (Lesson lesson : context.getDistributedLessons()) {
            if (lesson.getEducators().contains(educator) &&
                    lesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {

                CellForLesson cell = context.getWorkspace().getCellForLesson(lesson);
                if (cell != null) {
                    dates.add(cell.getDate());
                }
            }
        }
        return dates;
    }

    /**
     * Откатывает распределённые практики преподавателя.
     */
    public void rollbackPractices(Educator educator) {
        List<Lesson> toRemove = new ArrayList<>();

        for (Lesson lesson : context.getDistributedLessons()) {
            if (lesson.getEducators().contains(educator) && lesson.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE) {
                context.getWorkspace().removePlacement(lesson);
                toRemove.add(lesson);
            }
        }

        for (Lesson lesson : toRemove) {
            context.removeDistributedLesson(lesson);
        }
    }
}
