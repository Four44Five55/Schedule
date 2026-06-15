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
    private final PracticePreScheduler preScheduler;

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
        this.preScheduler = new PracticePreScheduler(dateFinder);
    }

    /**
     * Распределяет практики для всех преподавателей.
     */
    public void distributePractices(LocalDate semesterEnd) {
/*        for (Educator educator : context.getEducators()) {
            if (educator.getId()==304){
                distributeForEducator(educator, semesterEnd);
            }

        }*/
        for (Educator educator : context.getEducators()) {
            distributeForEducator(educator, semesterEnd);
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

        // Выбираем стратегию ДО цикла размещения
        PracticeSlotStrategy strategy = strategySelector.selectFor(educator, practices, semesterEnd);
        log.info("Выбрана стратегия: {}", strategy.getName());

        Set<LocalDate> occupiedDates = placement.getOccupiedDates(educator);
        log.info("Занятые даты (приоритетные): {}", occupiedDates);
        log.info("Практик: {}", practices.size());

        // ★ Предварительно вычисляем целевые даты для всех занятий
        Map<Lesson, LocalDate> targetDates = preScheduler.buildTargetDates(
                practices, strategy, semesterEnd);

        int placedCount = 0;
        int skippedCount = 0;
        int index = 0;
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

            String key = getPlacementKey(chain.getFirst());
            LocalDate minDate = lastPlacedByStreamAndDiscipline.get(key);

            // ★ Целевая дата из pre-scheduler (равномерное распределение)
            LocalDate targetDate = targetDates.get(chain.getFirst());

            PlacementResult result = tryPlaceChainWithStrategy(
                    chain, Set.of(), semesterEnd, strategy, minDate, targetDate);

            if (result.placed()) {
                boolean isFallback = result.dateSource().contains("fallback");
                if (!isFallback && (minDate == null || result.usedDate().isAfter(minDate))) {
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

        // Второй проход — неразмещённые занятия
        List<Lesson> unplaced = practices.stream()
                .filter(l -> !context.isLessonDistributed(l))
                .toList();

        if (!unplaced.isEmpty()) {
            log.info("Второй проход (уплотнение): {} неразмещённых занятий", unplaced.size());
            int repackPlaced = repackUnplaced(unplaced, strategy, semesterEnd);
            placedCount += repackPlaced;
        }

        log.info("=== Результат практик для {}: размещено={}, пропущено={}, неразмещено={} ===",
                educator.getName(), placedCount, skippedCount,
                practices.size() - placedCount - skippedCount);

        if (placedCount + skippedCount < practices.size()) {
            logUnplacedPractices(educator, practices);
        }
    }
    private int repackUnplaced(List<Lesson> unplaced, PracticeSlotStrategy strategy,
                               LocalDate semesterEnd) {
        int placed = 0;

        Map<String, List<Lesson>> byKey = unplaced.stream()
                .collect(Collectors.groupingBy(this::getPlacementKey));

        for (Map.Entry<String, List<Lesson>> entry : byKey.entrySet()) {
            String key = entry.getKey();
            List<Lesson> groupUnplaced = entry.getValue();

            // Все занятия этой дисциплины+потока — уже размещённые + неразмещённые
            // отсортированные по позиции
            List<LocalDate> placedDates = getPlacedDatesForKey(key);
            if (placedDates.isEmpty()) continue;

            // Самая ранняя дата уже размещённых — от неё считаем интервал
            LocalDate firstPlaced = placedDates.getFirst();
            LocalDate lastPlaced = placedDates.getLast();

            // Сколько всего занятий нужно разместить (уже + не)
            int totalNeeded = placedDates.size() + groupUnplaced.size();

            // Сколько дней доступно от первой до конца семестра
            List<LocalDate> allAvailable = dateFinder.getAvailableDates(
                    groupUnplaced.getFirst(), semesterEnd, strategy.getSkipPairs());

            // Берём только даты от firstPlaced
            List<LocalDate> candidates = allAvailable.stream()
                    .filter(d -> !d.isBefore(firstPlaced))
                    .toList();

            if (candidates.size() < totalNeeded) {
                log.warn("  Уплотнение [{}]: недостаточно дат даже от начала ({} < {})",
                        key, candidates.size(), totalNeeded);
                continue;
            }

            // Равномерно распределяем неразмещённые в оставшиеся свободные слоты
            // (те что не заняты уже размещёнными)
            Set<LocalDate> alreadyUsed = new HashSet<>(placedDates);
            List<LocalDate> freeSlots = candidates.stream()
                    .filter(d -> !alreadyUsed.contains(d))
                    .toList();

            log.info("  Уплотнение [{}]: {} неразмещённых, {} свободных слотов от {}",
                    key, groupUnplaced.size(), freeSlots.size(), firstPlaced);

            // Занятия уже отсортированы по позиции — размещаем по порядку
            // соблюдая хронологию: каждое следующее после предыдущего
            LocalDate lastUsed = lastPlaced;
            int slotIndex = 0;

            for (Lesson lesson : groupUnplaced) {
                if (context.isLessonDistributed(lesson)) continue;

                List<Lesson> chain = chainHandler.getChainForLesson(lesson, unplaced);
                if (chain.size() > 1 && !chain.getFirst().equals(lesson)) continue;

                Set<LocalDate> lectureDates = buildLectureDatesCache(chain.getFirst());

                // Ищем первый свободный слот ПОСЛЕ lastUsed
                boolean chainPlaced = false;
                while (slotIndex < freeSlots.size()) {
                    LocalDate date = freeSlots.get(slotIndex);
                    slotIndex++;

                    if (date.isBefore(lastUsed)) continue; // строго после предыдущего

                    Set<TimeSlotPair> skipPairs = lectureDates.contains(date)
                            ? addFirst(strategy.getSkipPairs()) : strategy.getSkipPairs();

                    if (chainHandler.tryPlaceChain(chain, date, skipPairs)) {
                        logPlacementSuccess(chain, date, strategy.getName() + " [уплотнение]");
                        placed += chain.size();
                        lastUsed = date;
                        chainPlaced = true;
                        break;
                    }
                }

                if (!chainPlaced) {
                    log.warn("  Уплотнение: не удалось разместить {}",
                            lesson.getCurriculumSlot().getPosition());
                }
            }
        }

        return placed;
    }
    /**
     * Возвращает даты уже размещённых занятий для данного ключа дисциплина+поток,
     * отсортированные по возрастанию.
     */
    private List<LocalDate> getPlacedDatesForKey(String key) {
        // key = "streamId_disciplineId"
        String[] parts = key.split("_");
        int streamId = Integer.parseInt(parts[0]);
        int disciplineId = Integer.parseInt(parts[1]);

        return context.getDistributedLessons().stream()
                .filter(l -> l.getStudyStream() != null
                        && l.getStudyStream().getId() == streamId)
                .filter(l -> l.getDisciplineCourse().getDiscipline().getId() == disciplineId)
                .map(l -> context.getWorkspace().getCellForLesson(l))
                .filter(Objects::nonNull)
                .map(CellForLesson::getDate)
                .distinct()
                .sorted()
                .toList();
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
                                                      LocalDate minDate,
                                                      LocalDate targetDate) {
        if (chain.isEmpty()) return new PlacementResult(false, null, "empty chain");

        Lesson firstLesson = chain.getFirst();
        Set<TimeSlotPair> baseSkipPairs = strategy.getSkipPairs();

        // Кэш дат с лекциями — один раз для всего метода
        Set<LocalDate> lectureDates = buildLectureDatesCache(firstLesson);

// targetDate уже учитывает равномерное распределение из preScheduler
// но если фаза 1 дала дату — берём ближайшую из двух к targetDate
        LocalDate assignedDate = context.getDateForLesson(firstLesson);
        LocalDate effectiveTarget = targetDate != null ? targetDate : assignedDate;
        if (effectiveTarget != null) {
            if (minDate != null && minDate.isAfter(effectiveTarget))
                effectiveTarget = minDate;

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
