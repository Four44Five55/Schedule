package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.services.LessonSortingService;
import ru.services.distribution.strategy.PracticeSlotStrategy;
import ru.services.distribution.strategy.PracticeSlotStrategySelector;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Обрабатывает фазу 2 — распределение практик.
 */
@Slf4j
public class PracticeDistributionHandler {
    private final DistributionContext context;
    private final LessonPlacementService placement;
    private final ChainPlacementHandler chainHandler;
    private final LessonDateFinder dateFinder;
    private final PracticeSlotStrategySelector strategySelector;

    public PracticeDistributionHandler(DistributionContext context,
                                       LessonPlacementService placement,
                                       ChainPlacementHandler chainHandler,
                                       LessonSortingService lessonSortingService) {
        this.context = context;
        this.placement = placement;
        this.chainHandler = chainHandler;
        this.dateFinder = new LessonDateFinder(context, placement);
        this.strategySelector = new PracticeSlotStrategySelector(
                dateFinder, lessonSortingService);
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
/*        for (Educator educator : context.getEducators()) {
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
        // выбираем стратегию ДО цикла размещения
        PracticeSlotStrategy strategy = strategySelector.selectFor(
                educator, practices, semesterEnd);
        log.info("Выбрана стратегия: {}", strategy.getName());
        // Получаем все даты, когда у преподавателя уже есть занятия (приоритетные)
        Set<LocalDate> occupiedDates = placement.getOccupiedDates(educator);
        log.info("Занятые даты (приоритетные): {}", occupiedDates);
        log.info("Практик: {}", practices.size());

        int placedCount = 0;
        int skippedCount = 0;
        int index = 0;
        // Отдельный минимум для каждой группы
        Map<String, LocalDate> lastPlacedByStreamAndDiscipline = new HashMap<>();

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

            //  передаём стратегию
            // Определяем minDate по группе первого занятия цепочки
            String key = getPlacementKey(chain.getFirst());
            LocalDate minDate = lastPlacedByStreamAndDiscipline.get(key);

            PlacementResult result = tryPlaceChainWithStrategy(
                    chain, Set.of(), semesterEnd, strategy, minDate);

            if (result.placed()) {
                // Обновляем только для этой группы
                if (minDate == null || result.usedDate().isAfter(minDate)) {
                    lastPlacedByStreamAndDiscipline.put(key, result.usedDate());
                }
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
    private String getPlacementKey(Lesson lesson) {
        Integer streamId = lesson.getStudyStream() != null
                ? lesson.getStudyStream().getId() : -1;
        Integer disciplineId = lesson.getDisciplineCourse()
                .getDiscipline().getId();
        return streamId + "_" + disciplineId;
    }
    private Integer getStreamId(Lesson lesson) {
        if (lesson.getStudyStream() == null) return -1;
        return lesson.getStudyStream().getId();
    }
    /**
     * Размещает цепочку с учётом стратегии.
     * skipPairs определяются динамически для каждой даты через resolveSkipPairs —
     * 1-я пара запрещается только если в этот день есть лекция.
     */
    private PlacementResult tryPlaceChainWithStrategy(List<Lesson> chain,
                                                      Set<LocalDate> occupiedDates,
                                                      LocalDate semesterEnd,
                                                      PracticeSlotStrategy strategy,
                                                      LocalDate minDate) {
        if (chain.isEmpty()) return new PlacementResult(false, null, "empty chain");

        Lesson firstLesson = chain.getFirst();
        Set<TimeSlotPair> baseSkipPairs = strategy.getSkipPairs();

        // Кэш дат с лекциями — один раз для всего метода
        Set<LocalDate> lectureDates = buildLectureDatesCache(firstLesson);

        // 1. Назначенная дата из фазы 1 — уточняем в окне ±3 дня с учётом minDate
        LocalDate assignedDate = context.getDateForLesson(firstLesson);
        if (assignedDate != null) {
            LocalDate effectiveTarget = (minDate != null && minDate.isAfter(assignedDate))
                    ? minDate : assignedDate;

            LocalDate bestDate = dateFinder.findBestDateInWindow(
                    firstLesson, effectiveTarget, semesterEnd, baseSkipPairs, 3, minDate);

            Set<TimeSlotPair> skipPairs = lectureDates.contains(bestDate)
                    ? addFirst(baseSkipPairs) : baseSkipPairs;

            if (chainHandler.tryPlaceChain(chain, bestDate, skipPairs)) {
                return new PlacementResult(true, bestDate,
                        strategy.getName() + " [фаза 1, окно]");
            }

            // Если лучшая дата в окне не подошла — пробуем effectiveTarget напрямую
            if (!bestDate.equals(effectiveTarget)) {
                skipPairs = lectureDates.contains(effectiveTarget)
                        ? addFirst(baseSkipPairs) : baseSkipPairs;
                if (chainHandler.tryPlaceChain(chain, effectiveTarget, skipPairs)) {
                    return new PlacementResult(true, effectiveTarget,
                            strategy.getName() + " [фаза 1]");
                }
            }
        }

        // 2. Все доступные даты >= minDate, отсортированные по загруженности преподавателя
        List<LocalDate> allDates = dateFinder.getAvailableDates(
                firstLesson, semesterEnd, baseSkipPairs);

        List<LocalDate> preferredDates = (minDate != null)
                ? allDates.stream().filter(d -> !d.isBefore(minDate)).toList()
                : allDates;

        if (!preferredDates.isEmpty()) {
            // Считаем загруженность преподавателя по датам — один проход
            Map<LocalDate, Integer> educatorLoad = dateFinder.buildScoreCache(
                    firstLesson, preferredDates);

            // Сортируем: 1-2 занятия препода → пустые дни → перегруженные
            List<LocalDate> sorted = preferredDates.stream()
                    .sorted(Comparator.comparingInt((LocalDate d) -> {
                        int load = educatorLoad.getOrDefault(d, 0);
                        if (load == 0) return 1;   // пустой день — средний приоритет
                        if (load <= 2) return 0;   // 1-2 занятия — лучший приоритет
                        return 2;                  // перегруженный — последний
                    }))
                    .toList();

            for (LocalDate date : sorted) {
                Set<TimeSlotPair> skipPairs = lectureDates.contains(date)
                        ? addFirst(baseSkipPairs) : baseSkipPairs;
                if (chainHandler.tryPlaceChain(chain, date, skipPairs)) {
                    return new PlacementResult(true, date,
                            strategy.getName() + " [доступная]");
                }
            }
        }

        // 3. Fallback — снимаем ограничение minDate если ничего не нашли
        if (minDate != null) {
            List<LocalDate> fallbackDates = allDates.stream()
                    .filter(d -> d.isBefore(minDate))
                    .toList();

            Map<LocalDate, Integer> educatorLoad = dateFinder.buildScoreCache(
                    firstLesson, fallbackDates);

            List<LocalDate> sortedFallback = fallbackDates.stream()
                    .sorted(Comparator.comparingInt((LocalDate d) -> {
                        int load = educatorLoad.getOrDefault(d, 0);
                        if (load == 0) return 1;
                        if (load <= 2) return 0;
                        return 2;
                    }))
                    .toList();

            for (LocalDate date : sortedFallback) {
                Set<TimeSlotPair> skipPairs = lectureDates.contains(date)
                        ? addFirst(baseSkipPairs) : baseSkipPairs;
                if (chainHandler.tryPlaceChain(chain, date, skipPairs)) {
                    return new PlacementResult(true, date,
                            strategy.getName() + " [доступная, fallback]");
                }
            }
        }

        return new PlacementResult(false, null, "нет доступной даты");
    }

    /**
     * Собирает даты где у участников занятия есть лекции — один проход.
     * Используется для динамического определения запрета 1-й пары.
     */
    private Set<LocalDate> buildLectureDatesCache(Lesson lesson) {
        Set<Educator> educators = new HashSet<>(lesson.getEducators());
        Set<Group> groups = lesson.getStudyStream().getGroups();

        return context.getDistributedLessons().stream()
                .filter(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
                .filter(l -> l.getEducators().stream().anyMatch(educators::contains)
                        || (l.getStudyStream() != null &&
                        l.getStudyStream().getGroups().stream()
                                .anyMatch(groups::contains)))
                .map(l -> context.getWorkspace().getCellForLesson(l))
                .filter(Objects::nonNull)
                .map(CellForLesson::getDate)
                .collect(Collectors.toSet());
    }

    /**
     * Добавляет FIRST к набору skipPairs (запрет 1-й пары в день лекции).
     */
    private Set<TimeSlotPair> addFirst(Set<TimeSlotPair> base) {
        Set<TimeSlotPair> result = new HashSet<>(base);
        result.add(TimeSlotPair.FIRST);
        return Collections.unmodifiableSet(result);
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
            log.info("✅ {} - {}/{} тема: {} ({} из {}) размещена на {} [{}],{}",
                    lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                    lesson.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                    lesson.getCurriculumSlot().getPosition(),
                    theme, i + 1, chain.size(), date, source,
                    lesson.getStudyStream().getGroups());
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
            log.info("❌ {} - {}/{} тема: {} ({} из {}) НЕ размещена, группа: {}",
                    lesson.getDisciplineCourse().getDiscipline().getAbbreviation(),
                    lesson.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                    lesson.getCurriculumSlot().getPosition(),
                    theme, i + 1, chain.size(),
                    lesson.getStudyStream().getGroups());
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
                log.warn("❌ {} - {}/{}, тема: {}, группы: {}",
                        l.getDisciplineCourse().getDiscipline().getAbbreviation(),
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
    ) {
    }
}
