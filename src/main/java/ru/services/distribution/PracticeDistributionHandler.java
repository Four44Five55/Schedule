package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.Educator;
import ru.entity.Lesson;

import java.time.LocalDate;
import java.util.ArrayList;
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
            if (educator.getId()==304){
                distributeForEducator(educator, semesterEnd);
            }

        }
       /* for (Educator educator : context.getEducators()) {
            distributeForEducator(educator, semesterEnd);
        }*/
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

        // Получаем все даты, когда у преподавателя уже есть занятия (приоритетные)
        Set<LocalDate> occupiedDates = placement.getOccupiedDates(educator);
        log.info("Занятые даты (приоритетные): {}", occupiedDates);

        log.info("Практик: {}", practices.size());

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
            if (chain.size() > 1 && !chain.getFirst().equals(current)) {
                skippedCount++;
                index++;
                continue;
            }

            // Пытаемся разместить цепочку с fallback-логикой
            PlacementResult result = tryPlaceChainWithFallback(chain, occupiedDates, semesterEnd);

            if (result.placed()) {
                placedCount += chain.size();
                index += chain.size();
                logPlacementSuccess(chain, result.usedDate(), result.dateSource());
            } else {
                index++;
                logPlacementFailure(chain);
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
     * Пытается разместить цепочку с fallback-логикой.
     * <p>
     * Порядок попыток:
     * 1. Дата из мапы (назначенная в фазе 1)
     * 2. Приоритетные даты (занятые даты преподавателя)
     * 3. Все доступные даты
     *
     * @param chain         цепочка занятий
     * @param occupiedDates приоритетные даты (дни с другими занятиями)
     * @param semesterEnd   конец семестра
     * @return результат размещения
     */
    private PlacementResult tryPlaceChainWithFallback(List<Lesson> chain,
                                                       Set<LocalDate> occupiedDates,
                                                       LocalDate semesterEnd) {
        if (chain.isEmpty()) {
            return new PlacementResult(false, null, "empty chain");
        }

        Lesson firstLesson = chain.getFirst();

        // 1. Пробуем назначенную дату из фазы 1
        LocalDate assignedDate = context.getDateForLesson(firstLesson);
        if (assignedDate != null) {
            if (chainHandler.tryPlaceChain(chain, assignedDate, LessonPlacementService.PRACTICE_SKIP)) {
                return new PlacementResult(true, assignedDate, "назначенная (фаза 1)");
            }
        }

        // 2. Пробуем приоритетные даты (занятые даты преподавателя)
        if (!occupiedDates.isEmpty()) {
            List<LocalDate> priorityDates = new ArrayList<>(occupiedDates);
            // Сортируем для детерминированности
            priorityDates.sort(LocalDate::compareTo);

            for (LocalDate date : priorityDates) {
                if (chainHandler.tryPlaceChain(chain, date, LessonPlacementService.PRACTICE_SKIP)) {
                    return new PlacementResult(true, date, "занятая дата (приоритет)");
                }
            }
        }

        // 3. Пробуем все доступные даты
        List<LocalDate> availableDates = dateFinder.getAvailableDates(firstLesson, semesterEnd);
        for (LocalDate date : availableDates) {
            // Пропускаем уже проверенные приоритетные даты
            if (occupiedDates.contains(date)) {
                continue;
            }
            if (chainHandler.tryPlaceChain(chain, date, LessonPlacementService.PRACTICE_SKIP)) {
                return new PlacementResult(true, date, "доступная дата");
            }
        }

        return new PlacementResult(false, null, "нет доступной даты");
    }

    /**
     * Логирует успешное размещение цепочки.
     */
    private void logPlacementSuccess(List<Lesson> chain, LocalDate date, String source) {
        for (int i = 0; i < chain.size(); i++) {
            Lesson lesson = chain.get(i);
            String theme = lesson.getCurriculumSlot().getThemeLesson() == null
                    ? "N/A" : lesson.getCurriculumSlot().getThemeLesson().getThemeNumber();
            log.info("✓ {} - {}/{} тема: {} ({} из {}) размещена на {} [{}]",
                    lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                    lesson.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                    lesson.getCurriculumSlot().getPosition(),
                    theme, i + 1, chain.size(), date, source);
        }
    }

    /**
     * Логирует неудачное размещение цепочки.
     */
    private void logPlacementFailure(List<Lesson> chain) {
        for (int i = 0; i < chain.size(); i++) {
            Lesson lesson = chain.get(i);
            String theme = lesson.getCurriculumSlot().getThemeLesson() == null
                    ? "N/A" : lesson.getCurriculumSlot().getThemeLesson().getThemeNumber();
            log.info("✗ {} - {}/{} тема: {} ({} из {}) НЕ размещена",
                    lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                    lesson.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                    lesson.getCurriculumSlot().getPosition(),
                    theme, i + 1, chain.size());
        }
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

    /**
     * Результат попытки размещения цепочки.
     */
    private record PlacementResult(
            boolean placed,         // true если размещено успешно
            LocalDate usedDate,     // использованная дата (null если не размещено)
            String dateSource       // источник даты для логирования
    ) {}
}
