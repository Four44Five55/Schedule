package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.Educator;
import ru.entity.Lesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Обрабатывает фазу 2 — распределение практик.
 */
@Slf4j
public class PracticeDistributionHandler {
    private final DistributionContext context;
    private final LessonPlacementService placement;
    private final ChainPlacementHandler chainHandler;
    private final LessonDateFinder dateFinder;

    public PracticeDistributionHandler(DistributionContext context,
                                       LessonPlacementService placement,
                                       ChainPlacementHandler chainHandler) {
        this.context = context;
        this.placement = placement;
        this.chainHandler = chainHandler;
        this.dateFinder = new LessonDateFinder(context, placement);
    }

    /**
     * Распределяет практики для всех преподавателей.
     */
    public void distributePractices(LocalDate semesterEnd) {
        for (Educator educator : context.getEducators()) {
            if (educator.getId() == 301) {
                distributeForEducator(educator, semesterEnd);
            }
        }
    }

    /**
     * Распределяет практики для указанного преподавателя.
     */
    public void distributeForEducator(Educator educator, LocalDate semesterEnd) {
        log.info("=== Распределение практик для: {} ===", educator.getName());

        List<Lesson> practices = placement.getPracticesForEducator(educator);
        if (practices.isEmpty()) {
            log.info("Нет практик для распределения");
            return;
        }

        List<LocalDate> availableDates = dateFinder.getAvailableDates(practices, semesterEnd);
        if (availableDates.isEmpty()) {
            log.error("Нет доступных дат для практик {}", educator.getName());
            return;
        }

        Set<LocalDate> lectureDates = placement.getLectureDates(educator);
        log.info("Лекционные даты (приоритетные): {}", lectureDates);

        log.info("Доступных дней: {}, практик: {}", availableDates.size(), practices.size());

        int placedCount = 0;
        int skippedCount = 0;

        int index = 0;
        while (index < practices.size()) {
            Lesson current = practices.get(index);

            // Пропускаем уже размещённые
            if (context.isLessonDistributed(current)) {
                skippedCount++;
                index++;
                continue;
            }

            // Получаем цепочку
            List<Lesson> chain = chainHandler.getChainForLesson(current, practices);

            // Пропускаем если не первое в цепочке
            if (chain.size() > 1 && !chain.get(0).equals(current)) {
                skippedCount++;
                index++;
                continue;
            }

            // Ищем дату с приоритетом лекционных дат
            LocalDate date = findDateWithPriority(chain, availableDates, lectureDates);
            if (date == null) {
                log.warn("Не найдена дата для практики: {}", current.getCurriculumSlot().getId());
                index++;
                continue;
            }

            // Размещаем цепочку
            if (chainHandler.tryPlaceChain(chain, date)) {
                placedCount += chain.size();
                index += chain.size();
                log.info("✓ Практика (цепочка из {}) размещена на {}", chain.size(), date);
            } else {
                log.warn("✗ Не удалось разместить цепочку на {}", date);
                index++;
            }
        }

        log.info("=== Результат практик для {}: размещено={}, пропущено={}, неразмещено={} ===",
                educator.getName(), placedCount, skippedCount, practices.size() - placedCount - skippedCount);

        // Лог неразмещённых
        if (placedCount + skippedCount < practices.size()) {
            logUnplacedPractices(educator, practices);
        }
    }

    /**
     * Ищет дату для размещения цепочки с приоритетом лекционных дат.
     */
    private LocalDate findDateWithPriority(List<Lesson> chain, List<LocalDate> availableDates, Set<LocalDate> priorityDates) {
        // Сначала проверяем приоритетные даты (лекционные)
        for (LocalDate date : availableDates) {
            if (priorityDates.contains(date) && chainHandler.canPlaceChain(chain, date)) {
                return date;
            }
        }

        // Если не нашли в приоритетных — ищем в любых доступных
        for (LocalDate date : availableDates) {
            if (chainHandler.canPlaceChain(chain, date)) {
                return date;
            }
        }
        return null;
    }

    /**
     * Логирует неразмещённые практики.
     */
    private void logUnplacedPractices(Educator educator, List<Lesson> practices) {
        List<Lesson> unplaced = practices.stream()
                .filter(l -> !context.isLessonDistributed(l))
                .toList();

        if (!unplaced.isEmpty()) {
            log.warn("=== Неразпределённые практики для {} ===", educator.getName());
            for (Lesson l : unplaced) {
                String theme = l.getCurriculumSlot().getThemeLesson() != null
                        ? l.getCurriculumSlot().getThemeLesson().getThemeNumber()
                        : "N/A";
                log.warn("  {}/{}, тема: {}, группы: {}",
                        l.getKindOfStudy().getAbbreviationName(),
                        l.getCurriculumSlot().getPosition(),
                        theme,
                        l.getStudyStream().getGroups());
            }
        }
    }
}
