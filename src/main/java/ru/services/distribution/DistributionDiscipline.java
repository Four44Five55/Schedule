package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.services.CurriculumSlotService;
import ru.services.LessonSortingService;
import ru.services.SlotChainService;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class DistributionDiscipline {
    private final SlotChainService slotChainService;
    private final CurriculumSlotService curriculumSlotService;
    private final LessonSortingService lessonSortingService;

    private final ScheduleWorkspace workspace;
    private List<Lesson> lessons;
    private final List<Educator> educators;
    private final List<Lesson> distributedLessons;
    private final Set<Lesson> distributedLessonsSet; // для быстрой проверки O(1)

    public DistributionDiscipline(ScheduleWorkspace workspace,
                                  List<Lesson> lessons,
                                  List<Educator> educators,
                                  SlotChainService slotChainService,
                                  CurriculumSlotService curriculumSlotService,
                                  LessonSortingService lessonSortingService) {
        this.workspace = workspace;
        this.lessons = lessons;
        this.educators = educators;
        this.slotChainService = slotChainService;
        this.curriculumSlotService = curriculumSlotService;
        this.lessonSortingService = lessonSortingService;
        this.distributedLessons = new ArrayList<>();
        this.distributedLessonsSet = new HashSet<>();
    }

    public void distributeLessons() {
// 1. Разделяем занятия на Экзаменационные и Обычные
        List<Lesson> examLessons = new ArrayList<>();
        List<Lesson> regularLessons = new ArrayList<>();

        for (Lesson lesson : lessons) {
            // Считаем экзаменом сам Экзамен и Консультацию перед ним (если они сцеплены)
            // Для простоты пока проверяем KindOfStudy.
            // Если у тебя есть Консультации, их тоже надо сюда, если они часть сессии.
            if (lesson.getKindOfStudy() == ru.enums.KindOfStudy.EXAM) {
                examLessons.add(lesson);
            } else {
                regularLessons.add(lesson);
            }
        }
        //distributeExams(examLessons); // Новый метод
        // 2. Сначала распределяем обычные занятия (как раньше)
        // Временно подменяем глобальный список lessons, чтобы distributeLessonsForEducator работал только с ними
        // List<Lesson> allLessonsBackup = new ArrayList<>(this.lessons);


        //========================================
        this.lessons = regularLessons;
        //TODO реализовать метод для получения учебного периода без сессии, после того как сначала составится сессия
        LocalDate semesterEnd = LocalDate.of(2026, 8, 16);

        /* for (Educator educator : educators) {
            distributeLessonsForEducator(educator, semesterEnd);
        }*/
        //========================================
        // ЭТАП 1: ЛЕКЦИИ (Скелет)
        distributeLecturesPhase(semesterEnd);

        // ЭТАП 2: ПРАКТИКИ (Заполнение)
        distributePracticesPhase(semesterEnd);
    }
    //TODO пофиксить проблему равномерности распределения лекций, после последнейлекци не хвататет времени провести все ПЗ
    private void distributeLecturesPhase(LocalDate semesterEnd) {
        // Копия списка преподов (можно отсортировать по важности/нагрузке)
        List<Educator> sortedEducators = new ArrayList<>(educators);
        // sortedEducators.sort(...)

        for (Educator educator : sortedEducators) {
            // 1. Подготовка списка занятий преподователя
            List<Lesson> educatorLessons = lessons.stream()
                    .filter(l -> l.getEducators().contains(educator))
                    .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                    .collect(Collectors.toList());

            // 1. Полное распределение (Черновик)
            distributeLessonsForEducator(educator, educatorLessons, semesterEnd);

            // 2. Откат Практик (Cleaning)
            rollbackPractices(educator);
        }
    }

    private void distributePracticesPhase(LocalDate semesterEnd) {
        // Распределяем оставшиеся практики рядом с уже размещёнными лекциями
  /*      for (Educator educator : educators) {
            distributePracticesForEducator(educator, semesterEnd);
        }*/

        distributePracticesForEducator(educators.getLast(), semesterEnd);


    }

    /**
     * Распределение практик для преподавателя.
     * Практика размещается только ПОСЛЕ своей опорной лекции (из sortedLessons).
     */
    private void distributePracticesForEducator(Educator educator, LocalDate semesterEnd) {
        log.info("=== НАЧАЛО: Распределение практик для преподавателя: {} ===", educator.getName());

        // 1. Фильтруем только практики этого преподавателя
        List<Lesson> practices = lessons.stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .collect(Collectors.toList());

        if (practices.isEmpty()) {
            return;
        }

        log.info("Всего практик для распределения: {}", practices.size());

        // Проверяем: сколько из них уже в distributedLessonsSet
        long alreadyInSet = practices.stream().filter(distributedLessonsSet::contains).count();
        if (alreadyInSet > 0) {
            log.warn("ВНИМАНИЕ: {} из {} практик УЖЕ находятся в distributedLessonsSet (до начала распределения!)",
                    alreadyInSet, practices.size());
        }

        // 2. Сортируем практики (логика из основного списка)
        boolean ruleDistributing = false;
        List<Lesson> sortedPractices = lessonSortingService.getSortedLessons(practices);

        // 3. Получаем даты с лекциями этого преподавателя (приоритет для размещения)
        Set<LocalDate> lectureDates = getDatesWithLecturesForEducator(educator);
        log.debug("Даты с лекциями (приоритетные): {}", lectureDates);

        int placedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        // 4. Распределяем каждую практику
        for (Lesson practice : sortedPractices) {
            // Пропускаем уже распределённые занятия (включая цепочки)
            if (distributedLessonsSet.contains(practice)) {
                skippedCount++;
                // Детальная диагностика: почему практика уже в наборе?
                CellForLesson cell = workspace.getCellForLesson(practice);
                String themeNumber = practice.getCurriculumSlot().getThemeLesson() != null
                        ? practice.getCurriculumSlot().getThemeLesson().getThemeNumber()
                        : "N/A";
                log.warn("х Зан.уже размещено: " +
                                "{}/{}, {}, размещено ={}, слот={}",
                        practice.getCurriculumSlot().getKindOfStudy().getAbbreviationName(),
                        themeNumber,
                        practice.getStudyStream().getGroups(),
                        cell != null ? cell.getDate() : "NULL",
                        cell != null ? cell.getTimeSlotPair() : "NULL");

                // Проверяем: это возможно ошибка с несколькими преподавателями?
                if (practice.getEducators().size() > 1) {
                    log.warn("  → Практика ведётся {} преподавателями: {}",
                            practice.getEducators().size(),
                            practice.getEducators().stream()
                                    .map(e -> e.getName() + "(id=" + e.getId() + ")")
                                    .collect(Collectors.joining(", ")));
                }
                continue;
            }

            // 4.1. Получаем цепочку для практики
            List<Lesson> chain = getChainForLesson(practice, practices);

            // 4.2. Проверяем: является ли эта практика первой в цепочке
            boolean isFirstInChain = chain.isEmpty() || chain.get(0).equals(practice);
            if (!isFirstInChain) {
                // Это не первая практика в цепочке — пропускаем, она будет обработана вместе с первой
                log.debug("Практика ID={} не первая в цепочке (пропуск)", practice.getCurriculumSlot().getId());
                skippedCount++;
                continue;
            }

            // 4.3. Находим минимально допустимую дату (после опорной лекции)
            LocalDate minDate = findMinDateForPractice(practice, educator);
            log.debug("Практика: {}, тип: {}, группы: {}, minDate: {}, размер цепочки: {}",
                    practice.getCurriculumSlot().getId(),
                    practice.getKindOfStudy(),
                    practice.getStudyStream().getName(),
                    minDate,
                    chain.size());

            // Проверка валидности minDate
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
            int tempPoint = practice.getCurriculumSlot().getPosition();
            System.out.println(tempPoint);
            // 4.4. Ищем доступную дату в зависимости от типа: цепочка или одиночная практика
            LocalDate targetDate;
            int chainSize = chain.size();
            boolean isChain = chainSize > 1;

            if (isChain) {
                // Цепочка: ищем дату для всей цепочки
                targetDate = findAvailableDateForChain(chain, minDate, semesterEnd, lectureDates);
            } else {
                // Одиночная практика: существующая логика
                targetDate = findAvailableDateForPractice(practice, minDate, semesterEnd, lectureDates);
            }

            // 4.5. Если нашли — размещаем
            if (targetDate != null) {
                placePracticeInDate(practice, educator, practices, targetDate);
                placedCount += chainSize;
                CellForLesson cell = workspace.getCellForLesson(practice);
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
                // 4.6. Не удалось найти свободное место — пробуем swap с другой практикой
                log.warn("✗ Не удалось найти дату для {} ID={}. Пробуем swap...",
                        isChain ? "цепочки" : "практики",
                        practice.getCurriculumSlot().getId());
                diagnosePracticePlacementFailure(practice, educator, minDate, semesterEnd, lectureDates);

                if (!trySwapPracticeWithAnother(practice, educator, practices, minDate, semesterEnd, lectureDates)) {
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

        // Проверка целостности подсчёта
        int totalAccounted = placedCount + skippedCount + failedCount;
        if (totalAccounted != sortedPractices.size()) {
            log.error("=== ОШИБКА ПОДСЧЁТА: sortedPractices.size()={}, но размещено({}) + пропущено({}) + не размещено({}) = {} ===",
                    sortedPractices.size(), placedCount, skippedCount, failedCount, totalAccounted);
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
     * Возвращает даты, в которые у преподавателя уже есть лекции.
     */
    private Set<LocalDate> getDatesWithLecturesForEducator(Educator educator) {
        Set<LocalDate> dates = new HashSet<>();
        for (Lesson lesson : distributedLessons) {
            if (lesson.getEducators().contains(educator) &&
                    lesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {

                CellForLesson cell = workspace.getCellForLesson(lesson);
                if (cell != null) {
                    dates.add(cell.getDate());
                }
            }
        }
        return dates;
    }

    /**
     * Находит минимально допустимую дату для практики.
     * Берёт ПРЕДЫДУЩУЮ ЛЕКЦИЮ из основного sortedLessons и получает её дату из workspace.
     * Для СЕМИНАРОВ: добавляет +3 дня после последней лекции с той же темой.
     */
    private LocalDate findMinDateForPractice(Lesson practice, Educator educator) {
        // Получаем все занятия преподавателя в отсортированном порядке
        List<Lesson> educatorLessons = lessons.stream()
                .filter(l -> l.getEducators().contains(educator))
                .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                .collect(Collectors.toList());

        // Находим позицию текущей практики
        int practiceIndex = educatorLessons.indexOf(practice);
        if (practiceIndex == -1) {
            log.warn("findMinDateForPractice: практика ID={} не найдена в списке занятий преподавателя {}",
                    practice.getCurriculumSlot().getId(), educator.getName());
            return null;
        }

        log.trace("findMinDateForPractice: практика ID={}, позиция в списке: {} из {}",
                practice.getCurriculumSlot().getId(), practiceIndex, educatorLessons.size());

        LocalDate minDate = null;

        // 1. Для СЕМИНАРОВ: ищем последнюю лекцию с той же темой
        if (practice.getKindOfStudy() == ru.enums.KindOfStudy.SEMINAR) {
            LocalDate seminarMinDate = findMinDateForSeminar(practice, educator, educatorLessons, practiceIndex);
            if (seminarMinDate != null) {
                minDate = seminarMinDate;
                log.debug("findMinDateForPractice: для СЕМИНАРА ID={} установлен minDate={} (лекция той же темы + 3 дня)",
                        practice.getCurriculumSlot().getId(), minDate);
            }
        }

        // 2. Общий случай: ищем предыдущую лекцию
        if (minDate == null) {
            for (int i = practiceIndex - 1; i >= 0; i--) {
                Lesson previous = educatorLessons.get(i);
                if (previous.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {
                    // Нашли опорную лекцию — берём её дату из workspace
                    CellForLesson cell = workspace.getCellForLesson(previous);
                    if (cell != null) {
                        minDate = cell.getDate();
                        log.debug("findMinDateForPractice: для практики ID={} найдена предыдущая лекция ID={} в дату {}",
                                practice.getCurriculumSlot().getId(), previous.getCurriculumSlot().getId(), minDate);
                        break;
                    } else {
                        log.warn("findMinDateForPractice: предыдущая лекция ID={} не размещена в расписании (cell is null)",
                                previous.getCurriculumSlot().getId());
                    }
                }
            }
        }

        // Если не нашли лекцию — берём начало семестра
        if (minDate == null) {
            minDate = LocalDate.of(2026, 1, 1); // TODO: вынести в конфиг
            log.debug("findMinDateForPractice: для практики ID={} предыдущая лекция не найдена, используем дату по умолчанию: {}",
                    practice.getCurriculumSlot().getId(), minDate);
        }

        return minDate;
    }

    /**
     * Находит минимально допустимую дату для СЕМИНАРА.
     * Ищет последнюю лекцию с той же темой и добавляет 3 дня.
     *
     * @param seminar          семинар для размещения
     * @param educator         преподаватель
     * @param educatorLessons  все занятия преподавателя (отсортированные)
     * @param seminarIndex     позиция семинара в списке
     * @return минимальная дата (лекция + 3 дня) или null, если лекция не найдена
     */
    private LocalDate findMinDateForSeminar(Lesson seminar, Educator educator,
                                            List<Lesson> educatorLessons, int seminarIndex) {
        // Проверяем: есть ли тема у семинара
        if (seminar.getCurriculumSlot().getThemeLesson() == null) {
            log.debug("findMinDateForSeminar: семинар ID={} не имеет темы, используем стандартную логику",
                    seminar.getCurriculumSlot().getId());
            return null;
        }

        Integer themeId = seminar.getCurriculumSlot().getThemeLesson().getId();

        log.debug("findMinDateForSeminar: семинар ID={}, тема ID={}, поиск лекции с той же темой",
                seminar.getCurriculumSlot().getId(), themeId);

        // Ищем последнюю лекцию с той же темой (перед семинаром по позиции)
        for (int i = seminarIndex - 1; i >= 0; i--) {
            Lesson candidate = educatorLessons.get(i);

            // Пропускаем не-лекции
            if (candidate.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE) {
                continue;
            }

            // Проверяем: есть ли тема у лекции
            if (candidate.getCurriculumSlot().getThemeLesson() == null) {
                continue;
            }

            // Проверяем тему лекции
            Integer candidateThemeId = candidate.getCurriculumSlot().getThemeLesson().getId();
            if (themeId.equals(candidateThemeId)) {
                // Нашли лекцию с той же темой!
                CellForLesson cell = workspace.getCellForLesson(candidate);
                if (cell != null) {
                    LocalDate lectureDate = cell.getDate();
                    LocalDate seminarMinDate = lectureDate.plusDays(3); // +3 дня

                    log.debug("findMinDateForSeminar: для семинара ID={} найдена лекция ID={} с темой ID={} в дату {} -> minDate={}",
                            seminar.getCurriculumSlot().getId(), candidate.getCurriculumSlot().getId(),
                            themeId, lectureDate, seminarMinDate);

                    return seminarMinDate;
                }
            }
        }

        log.debug("findMinDateForSeminar: для семинара ID={} не найдена лекция с темой ID={}",
                seminar.getCurriculumSlot().getId(), themeId);
        return null;
    }

    /**
     * Диагностический метод: анализирует, почему практика не может быть размещена.
     * Выполняет детальную проверку доступности в minDate и ближайших датах.
     */
    private void diagnosePracticePlacementFailure(Lesson practice, Educator educator,
                                                  LocalDate minDate, LocalDate semesterEnd,
                                                  Set<LocalDate> lectureDates) {
        log.debug("=== ДИАГНОСТИКА для практики ID={} ===", practice.getCurriculumSlot().getId());

        // 1. Проверяем диапазон дат
        List<LocalDate> allDates = CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isBefore(minDate))
                .filter(d -> !d.isAfter(semesterEnd))
                .sorted()
                .toList();

        log.debug("Доступный диапазон дат [{} - {}]: {} дат", minDate, semesterEnd, allDates.size());

        if (allDates.isEmpty()) {
            log.error("КРИТИЧНО: Нет доступных дат в диапазоне! minDate={}, semesterEnd={}", minDate, semesterEnd);
            return;
        }

        // 2. Проверяем первые 5 доступных дат детально
        int checkLimit = Math.min(5, allDates.size());
        for (int i = 0; i < checkLimit; i++) {
            LocalDate testDate = allDates.get(i);
            boolean isLectureDate = lectureDates.contains(testDate);
            log.debug("Проверка даты: {} (приоритетная: {})", testDate, isLectureDate);

            List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(testDate);
            log.debug("  Ячеек в день: {}", dayCells.size());

            for (CellForLesson cell : dayCells) {
                PlacementOption option = workspace.findPlacementOption(practice, cell);
                if (option.isPossible()) {
                    log.debug("    Ячейка {} - ДОСТУПНА (score={})", cell.getTimeSlotPair(), option.score());
                } else {
                    log.debug("    Ячейка {} - НЕДОСТУПНА: {}", cell.getTimeSlotPair(), option.failureReason());
                }
            }
        }

        // 3. Информация о требованиях практики
        if (practice.getRequiredAuditorium() != null) {
            log.debug("Требуется аудитория: {}", practice.getRequiredAuditorium().getName());
        }
        if (practice.getPriorityAuditorium() != null) {
            log.debug("Приоритетная аудитория: {}", practice.getPriorityAuditorium().getName());
        }
        if (practice.getAllowedAuditoriumPool() != null) {
            log.debug("Пул аудиторий: {} аудиторий",
                    practice.getAllowedAuditoriumPool().getAuditoriums().size());
        }

        // 4. Проверка занятости ресурсов
        log.debug("Преподаватели: {}", practice.getEducators().stream()
                .map(e -> e.getName() + "(id=" + e.getId() + ")")
                .collect(Collectors.joining(", ")));
        log.debug("Группы: {}", practice.getStudyStream().getGroups().stream()
                .map(g -> g.getName() + "(id=" + g.getId() + ")")
                .collect(Collectors.joining(", ")));
    }

    /**
     * Ищет доступную дату для практики.
     * Использует окно поиска с приоритетом дат с лекциями.
     * Учитывает равномерность распределения практик по дням.
     */
    private LocalDate findAvailableDateForPractice(Lesson practice, LocalDate minDate,
                                                   LocalDate semesterEnd, Set<LocalDate> lectureDates) {
        // 1. Рассчитываем размер окна на основе нагрузки
        int windowSize = calculateDynamicWindowSize(lectureDates.size());

        // 2. Собираем все доступные даты >= minDate
        List<LocalDate> allDates = CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isBefore(minDate))
                .filter(d -> !d.isAfter(semesterEnd))
                .sorted()
                .toList();

        if (allDates.isEmpty()) {
            log.warn("Нет доступных дат для практики ID={} в диапазоне [{} - {}]",
                    practice.getCurriculumSlot().getId(), minDate, semesterEnd);
            return null;
        }

        log.debug("findAvailableDateForPractice: практика ID={}, диапазон [{} - {}], всего дат: {}, приоритетных дат: {}, размер окна: {}",
                practice.getCurriculumSlot().getId(), minDate, semesterEnd, allDates.size(),
                allDates.stream().filter(lectureDates::contains).count(), windowSize);

        // 3. Поиск с расширяющимся окном
        LocalDate foundDate = findDateInSlidingWindow(practice, allDates, lectureDates, windowSize);

        if (foundDate != null) {
            log.debug("  → Найдена дата: {} (приоритетная: {})",
                    foundDate, lectureDates.contains(foundDate));
            return foundDate;
        }

        log.warn("Не удалось найти дату для практики ID={} в диапазоне [{} - {}]",
                practice.getCurriculumSlot().getId(), minDate, semesterEnd);
        return null;
    }

    /**
     * Поиск даты в скользящем окне с приоритетом лекций и учётом равномерности.
     * При включённом режиме компактности (compactSchedule) отдаёт приоритет датам,
     * в которых уже есть занятия этого преподавателя.
     */
    private LocalDate findDateInSlidingWindow(Lesson practice, List<LocalDate> allDates,
                                              Set<LocalDate> lectureDates, int windowSize) {
        // Получаем первого преподавателя из занятия
        Educator educator = practice.getEducators().stream().findFirst().orElse(null);
        boolean useCompactness = educator != null && educator.isCompactSchedule();

        // УЛУЧШЕНИЕ 1: При compactSchedule=true сначала проверяем даты с уже размещёнными занятиями
        if (useCompactness) {
            LocalDate compactDate = findDateInCompactDays(practice, allDates, educator);
            if (compactDate != null) {
                log.debug("  → Найдена компактная дата: {} (в день с уже размещёнными занятиями)", compactDate);
                return compactDate;
            }
        }

        int totalDates = allDates.size();

        // Расширяем окно постепенно
        for (int offset = 0; offset < totalDates; offset += windowSize) {
            int endIndex = Math.min(offset + windowSize, totalDates);
            List<LocalDate> windowDates = allDates.subList(offset, endIndex);

            log.debug("  Проверка окна [{} - {}] из {} дат (compactMode: {})",
                    offset, endIndex, windowDates.size(), useCompactness);

            // Создаём список кандидатов с оценкой приоритета
            List<DateCandidate> candidates = new ArrayList<>();

            for (LocalDate date : windowDates) {
                if (!canPlacePracticeInDate(practice, date)) {
                    continue;
                }

                // Рассчитываем приоритет даты
                boolean hasLecture = lectureDates.contains(date);
                int practicesInDay = countPracticesInDate(date, practice);
                int distanceFromStart = windowDates.indexOf(date);

                // Базовый приоритет:
                // - Наличие лекции: +1000
                // - Меньше практик в дне: +100 * (2 - practicesInDay)
                // - Ближе к началу окна: +10 * (windowSize - distance)
                int priority = (hasLecture ? 1000 : 0)
                        + (2 - Math.min(practicesInDay, 2)) * 100
                        + (windowSize - distanceFromStart) * 10;

                boolean hasCompactnessBonus = false;
                int nearbyDaysWithLessons = 0;

                // Компактность: бонус за даты, где уже есть занятия преподавателя
                if (useCompactness && educator != null) {
                    int compactnessBonus = calculateCompactnessBonus(date, practice, educator);
                    nearbyDaysWithLessons = countNearbyDaysWithLessons(date, practice, educator);

                    // Добавляем бонус к приоритету
                    // +500 если в этот же день уже есть занятия
                    // +200 если есть занятия в соседний день (±1 день)
                    // +100 если есть занятия в ±2 дня
                    priority += compactnessBonus;

                    if (compactnessBonus > 0) {
                        hasCompactnessBonus = true;
                    }
                }

                candidates.add(new DateCandidate(date, priority, hasLecture, practicesInDay,
                        hasCompactnessBonus, nearbyDaysWithLessons));

                log.trace("    Кандидат: {} (лекция: {}, практик: {}, приоритет: {}, compactBonus: {})",
                        date, hasLecture, practicesInDay, priority,
                        hasCompactnessBonus ? nearbyDaysWithLessons : 0);
            }

            // Сортируем по приоритету и берём лучший
            if (!candidates.isEmpty()) {
                candidates.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
                DateCandidate best = candidates.getFirst();
                log.debug("  → Лучший кандидат в окне: {} (лекция: {}, практик: {}, compactBonus: {})",
                        best.date(), best.hasLecture(), best.practicesCount(), best.nearbyDaysWithLessons());
                return best.date();
            }

            log.debug("  В окне [{} - {}] не найдено подходящих дат", offset, endIndex);
        }

        return null;
    }

    /**
     * Специальный поиск компактной даты - ищет даты, где уже есть занятия преподавателя
     * и есть свободные слоты для текущего занятия.
     * Приоритет: даты с большим количеством занятий → меньшим расстоянием между слотами
     */
    private LocalDate findDateInCompactDays(Lesson practice, List<LocalDate> allDates, Educator educator) {
        // 1. Собираем даты, где уже есть занятия преподавателя
        Set<LocalDate> datesWithLessons = distributedLessons.stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> {
                    CellForLesson cell = workspace.getCellForLesson(l);
                    return cell != null;
                })
                .map(l -> workspace.getCellForLesson(l).getDate())
                .collect(Collectors.toSet());

        if (datesWithLessons.isEmpty()) {
            return null;
        }

        log.debug("  findDateInCompactDays: найдено {} дат с занятиями преподавателя", datesWithLessons.size());

        // 2. Проверяем каждую дату на наличие свободных слотов
        for (LocalDate date : datesWithLessons) {
            if (!allDates.contains(date)) {
                continue; // Дата вне доступного диапазона
            }

            if (canPlacePracticeInDate(practice, date)) {
                log.debug("    → Дата {} подходит для компактного размещения", date);
                return date;
            }

            // Диагностика: почему не подходит?
            log.trace("    → Дата {} не подходит: нет свободных слотов", date);
        }

        log.debug("  findDateInCompactDays: не найдено подходящих компактных дат");
        return null;
    }

    /**
     * Расчитывает размер окна поиска на основе количества дат с лекциями.
     * Чем меньше нагрузка (даты с лекциями), тем больше окно.
     */
    private int calculateDynamicWindowSize(int lectureDatesCount) {
        // Нагрузка небольшая - большое окно (до 21 дня)
        if (lectureDatesCount <= 3) {
            return 21;
        }
        // Средняя нагрузка - среднее окно
        if (lectureDatesCount <= 7) {
            return 14;
        }
        // Большая нагрузка - маленькое окно (больше точности)
        if (lectureDatesCount <= 14) {
            return 10;
        }
        // Очень большая нагрузка - минимальное окно
        return 7;
    }

    /**
     * Подсчитывает количество практик уже размещённых в указанную дату
     * для того же преподавателя и тех же групп.
     */
    private int countPracticesInDate(LocalDate date, Lesson practice) {
        return (int) distributedLessons.stream()
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .filter(l -> l.getEducators().equals(practice.getEducators()))
                .filter(l -> {
                    CellForLesson cell = workspace.getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .count();
    }

    /**
     * Рассчитывает бонус к приоритету за компактность расписания.
     * Компактность означает размещение занятий в минимальное количество дней:
     * - +500 если в этот же день уже есть занятия преподавателя
     * - +200 если есть занятия в соседний день (±1 день)
     * - +100 если есть занятия в ±2 дня
     */
    private int calculateCompactnessBonus(LocalDate targetDate, Lesson practice, Educator educator) {
        // Проверяем: есть ли уже занятия преподавателя в этот день
        int lessonsInTargetDay = countLessonsInDateForEducator(targetDate, educator);
        if (lessonsInTargetDay > 0) {
            return 500; // Максимальный бонус - занятия в тот же день
        }

        // Проверяем соседние дни
        for (int dayOffset = 1; dayOffset <= 2; dayOffset++) {
            LocalDate prevDay = targetDate.minusDays(dayOffset);
            LocalDate nextDay = targetDate.plusDays(dayOffset);

            int lessonsInPrevDay = countLessonsInDateForEducator(prevDay, educator);
            int lessonsInNextDay = countLessonsInDateForEducator(nextDay, educator);

            if (lessonsInPrevDay > 0 || lessonsInNextDay > 0) {
                // Чем ближе день, тем больше бонус
                return dayOffset == 1 ? 200 : 100;
            }
        }

        return 0; // Занятий поблизости нет
    }

    /**
     * Подсчитывает количество занятий преподавателя в указанную дату.
     */
    private int countLessonsInDateForEducator(LocalDate date, Educator educator) {
        return (int) distributedLessons.stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> {
                    CellForLesson cell = workspace.getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .count();
    }

    /**
     * Подсчитывает количество дней в радиусе ±2 дней, в которых есть занятия преподавателя.
     * Используется для логирования.
     */
    private int countNearbyDaysWithLessons(LocalDate targetDate, Lesson practice, Educator educator) {
        int count = 0;
        for (int dayOffset = -2; dayOffset <= 2; dayOffset++) {
            if (dayOffset == 0) continue; // Пропускаем саму дату
            LocalDate checkDate = targetDate.plusDays(dayOffset);
            if (countLessonsInDateForEducator(checkDate, educator) > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Вспомогательный record для хранения информации о кандидате даты.
     */
    private record DateCandidate(LocalDate date, int priority, boolean hasLecture, int practicesCount,
                                 boolean hasCompactnessBonus, int nearbyDaysWithLessons) {
        public DateCandidate(LocalDate date, int priority, boolean hasLecture, int practicesCount) {
            this(date, priority, hasLecture, practicesCount, false, 0);
        }
    }

    /**
     * Ищет доступную дату для цепочки занятий.
     * Использует окно поиска с приоритетом дат с лекциями.
     * Учитывает равномерность распределения практик по дням.
     */
    private LocalDate findAvailableDateForChain(List<Lesson> chain, LocalDate minDate,
                                                LocalDate semesterEnd, Set<LocalDate> lectureDates) {
        // 1. Рассчитываем размер окна на основе нагрузки
        int windowSize = calculateDynamicWindowSize(lectureDates.size());

        // 2. Собираем все доступные даты >= minDate
        List<LocalDate> allDates = CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isBefore(minDate))
                .filter(d -> !d.isAfter(semesterEnd))
                .sorted()
                .toList();

        int chainSize = chain.size();
        Lesson firstLesson = chain.get(0);

        if (allDates.isEmpty()) {
            log.warn("Нет доступных дат для цепочки из {} занятий (первая ID={}) в диапазоне [{} - {}]",
                    chainSize, firstLesson.getCurriculumSlot().getId(), minDate, semesterEnd);
            return null;
        }

        log.debug("findAvailableDateForChain: цепочка из {} занятий, первая ID={}, диапазон [{} - {}], всего дат: {}, приоритетных дат: {}, размер окна: {}",
                chainSize, firstLesson.getCurriculumSlot().getId(), minDate, semesterEnd, allDates.size(),
                allDates.stream().filter(lectureDates::contains).count(), windowSize);

        // 3. Поиск с расширяющимся окном (используем первый урок цепочки для оценки нагрузки)
        LocalDate foundDate = findDateInSlidingWindowForChain(chain, allDates, lectureDates, windowSize);

        if (foundDate != null) {
            log.debug("  → Найдена дата для цепочки: {} (приоритетная: {})",
                    foundDate, lectureDates.contains(foundDate));
            return foundDate;
        }

        log.warn("Не удалось найти дату для цепочки из {} занятий (первая ID={}) в диапазоне [{} - {}]",
                chainSize, firstLesson.getCurriculumSlot().getId(), minDate, semesterEnd);
        return null;
    }

    /**
     * Поиск даты в скользящем окне для цепочки занятий.
     * При включённом режиме компактности (compactSchedule) отдаёт приоритет датам,
     * в которых уже есть занятия этого преподавателя.
     */
    private LocalDate findDateInSlidingWindowForChain(List<Lesson> chain, List<LocalDate> allDates,
                                                       Set<LocalDate> lectureDates, int windowSize) {
        // Получаем первого преподавателя из цепочки
        Educator educator = chain.get(0).getEducators().stream().findFirst().orElse(null);
        boolean useCompactness = educator != null && educator.isCompactSchedule();

        // УЛУЧШЕНИЕ 1: При compactSchedule=true сначала проверяем даты с уже размещёнными занятиями
        if (useCompactness) {
            LocalDate compactDate = findDateInCompactDaysForChain(chain, allDates, educator);
            if (compactDate != null) {
                log.debug("  → Найдена компактная дата для цепочки: {} (в день с уже размещёнными занятиями)", compactDate);
                return compactDate;
            }
        }

        int totalDates = allDates.size();
        int chainSize = chain.size();

        // Расширяем окно постепенно
        for (int offset = 0; offset < totalDates; offset += windowSize) {
            int endIndex = Math.min(offset + windowSize, totalDates);
            List<LocalDate> windowDates = allDates.subList(offset, endIndex);

            log.debug("  Проверка окна для цепочки [{} - {}] из {} дат (compactMode: {})",
                    offset, endIndex, windowDates.size(), useCompactness);

            // Создаём список кандидатов с оценкой приоритета
            List<DateCandidate> candidates = new ArrayList<>();

            for (LocalDate date : windowDates) {
                if (!canPlaceChainInDate(chain, date)) {
                    continue;
                }

                // Рассчитываем приоритет даты
                boolean hasLecture = lectureDates.contains(date);
                int practicesInDay = countPracticesInDate(date, chain.get(0));
                int distanceFromStart = windowDates.indexOf(date);

                // Базовый приоритет:
                // - Наличие лекции: +1000
                // - Меньше практик в дне: +100 * (2 - practicesInDay)
                // - Ближе к началу окна: +10 * (windowSize - distance)
                int priority = (hasLecture ? 1000 : 0)
                        + (2 - Math.min(practicesInDay, 2)) * 100
                        + (windowSize - distanceFromStart) * 10;

                boolean hasCompactnessBonus = false;
                int nearbyDaysWithLessons = 0;

                // Компактность: бонус за даты, где уже есть занятия преподавателя
                if (useCompactness && educator != null) {
                    int compactnessBonus = calculateCompactnessBonus(date, chain.get(0), educator);
                    nearbyDaysWithLessons = countNearbyDaysWithLessons(date, chain.get(0), educator);

                    // Добавляем бонус к приоритету
                    priority += compactnessBonus;

                    if (compactnessBonus > 0) {
                        hasCompactnessBonus = true;
                    }
                }

                candidates.add(new DateCandidate(date, priority, hasLecture, practicesInDay,
                        hasCompactnessBonus, nearbyDaysWithLessons));

                log.trace("    Кандидат для цепочки: {} (лекция: {}, практик: {}, приоритет: {}, compactBonus: {})",
                        date, hasLecture, practicesInDay, priority,
                        hasCompactnessBonus ? nearbyDaysWithLessons : 0);
            }

            // Сортируем по приоритету и берём лучший
            if (!candidates.isEmpty()) {
                candidates.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
                DateCandidate best = candidates.getFirst();
                log.debug("  → Лучший кандидат для цепочки в окне: {} (лекция: {}, практик: {}, compactBonus: {})",
                        best.date(), best.hasLecture(), best.practicesCount(), best.nearbyDaysWithLessons);
                return best.date();
            }

            log.debug("  В окне [{} - {}] не найдено подходящих дат для цепочки", offset, endIndex);
        }

        return null;
    }

    /**
     * Специальный поиск компактной даты для цепочки занятий.
     * Аналогичен findDateInCompactDays, но использует canPlaceChainInDate.
     */
    private LocalDate findDateInCompactDaysForChain(List<Lesson> chain, List<LocalDate> allDates, Educator educator) {
        // 1. Собираем даты, где уже есть занятия преподавателя
        Set<LocalDate> datesWithLessons = distributedLessons.stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> {
                    CellForLesson cell = workspace.getCellForLesson(l);
                    return cell != null;
                })
                .map(l -> workspace.getCellForLesson(l).getDate())
                .collect(Collectors.toSet());

        if (datesWithLessons.isEmpty()) {
            return null;
        }

        log.debug("  findDateInCompactDaysForChain: найдено {} дат с занятиями преподавателя", datesWithLessons.size());

        // 2. Проверяем каждую дату на наличие свободных слотов для цепочки
        for (LocalDate date : datesWithLessons) {
            if (!allDates.contains(date)) {
                continue; // Дата вне доступного диапазона
            }

            if (canPlaceChainInDate(chain, date)) {
                log.debug("    → Дата {} подходит для компактного размещения цепочки", date);
                return date;
            }

            // Диагностика: почему не подходит?
            log.trace("    → Дата {} не подходит для цепочки: нет свободных слотов", date);
        }

        log.debug("  findDateInCompactDaysForChain: не найдено подходящих компактных дат для цепочки");
        return null;
    }

    /**
     * Проверяет, можно ли разместить практику в указанную дату.
     */
    private boolean canPlacePracticeInDate(Lesson practice, LocalDate date) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        log.trace("canPlacePracticeInDate: практика ID={}, дата={}, ячеек в день: {}",
                practice.getCurriculumSlot().getId(), date, dayCells.size());

        for (CellForLesson cell : dayCells) {
            // Исключаем 4-ю пару
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                log.trace("  → Ячейка {} пропущена (4-я пара)", cell.getTimeSlotPair());
                continue;
            }

            // Проверяем через workspace
            PlacementOption option = workspace.findPlacementOption(practice, cell);
            if (option.isPossible()) {
                log.trace("  → Ячейка {} доступна", cell.getTimeSlotPair());
                return true;
            } else {
                log.trace("  → Ячейка {} недоступна: {}", cell.getTimeSlotPair(), option.failureReason());
            }
        }
        log.trace("  → Дата {} не подходит: нет доступных ячеек", date);
        return false;
    }

    /**
     * Проверяет, можно ли разместить цепочку занятий в указанную дату.
     * Использует canPlacePracticeInDate для каждого занятия в цепочке.
     * Требует непрерывных слотов подряд в количестве chain.size().
     */
    private boolean canPlaceChainInDate(List<Lesson> chain, LocalDate date) {
        if (chain.isEmpty()) {
            return true;
        }
        if (chain.size() == 1) {
            return canPlacePracticeInDate(chain.get(0), date);
        }

        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        int chainSize = chain.size();

        log.trace("canPlaceChainInDate: цепочка из {} занятий, дата={}, ячеек в день: {}",
                chainSize, date, dayCells.size());

        // Скользящее окно: проверяем каждые chainSize слотов подряд
        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            boolean fit = true;

            // Проверяем непрерывность и доступность для каждого занятия цепочки
            for (int j = 0; j < chainSize; j++) {
                CellForLesson cell = dayCells.get(i + j);

                // 1. Исключаем 4-ю пару
                if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                    fit = false;
                    break;
                }

                // 2. Проверка непрерывности слотов
                if (j > 0) {
                    CellForLesson prevCell = dayCells.get(i + j - 1);
                    if (cell.getTimeSlotPair().ordinal() != prevCell.getTimeSlotPair().ordinal() + 1) {
                        fit = false;
                        break; // Разрыв в расписании дня
                    }
                }

                // 3. Проверяем доступность через canPlacePracticeInDate
                if (!canPlacePracticeInCell(chain.get(j), cell)) {
                    fit = false;
                    break;
                }
            }

            if (fit) {
                log.trace("  → Нашлось место для цепочки: старт с ячейки {}", dayCells.get(i).getTimeSlotPair());
                return true;
            }
        }

        log.trace("  → Дата {} не подходит для цепочки: нет {} непрерывных слотов", date, chainSize);
        return false;
    }

    /**
     * Проверяет, можно ли разместить занятие в конкретной ячейке.
     * Вынос из canPlacePracticeInDate для переиспользования в canPlaceChainInDate.
     */
    private boolean canPlacePracticeInCell(Lesson practice, CellForLesson cell) {
        if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
            return false;
        }
        PlacementOption option = workspace.findPlacementOption(practice, cell);
        return option.isPossible();
    }

    /**
     * Размещает практику в указанную дату.
     * Обрабатывает цепочки занятий.
     */
    private void placePracticeInDate(Lesson practice, Educator educator, List<Lesson> educatorLessons, LocalDate targetDate) {
        List<Lesson> chain = getChainForLesson(practice, educatorLessons);

        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(targetDate);
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        // Пытаемся разместить цепочку
        if (tryPlaceChainInDay(chain, dayCells, false)) {
            // Успех
        } else {
            log.warn("Не удалось разместить цепочку в дату {}", targetDate);
        }
    }

    /**
     * Пытается поменять практику с другой практикой того же преподавателя.
     */
    private boolean trySwapPracticeWithAnother(Lesson practice, Educator educator, List<Lesson> educatorLessons,
                                               LocalDate minDate, LocalDate semesterEnd,
                                               Set<LocalDate> lectureDates) {
        // Ищем другие практики этого преподавателя, которые уже распределены
        List<Lesson> otherPractices = distributedLessons.stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .filter(l -> !l.equals(practice))
                .collect(Collectors.toList());

        for (Lesson other : otherPractices) {
            CellForLesson otherCell = workspace.getCellForLesson(other);
            if (otherCell == null) continue;

            LocalDate otherDate = otherCell.getDate();

            // Проверяем: можно ли поставить текущую практику на место другой?
            if (otherDate.isBefore(minDate) || otherDate.isAfter(semesterEnd)) {
                continue;
            }

            // Проверяем взаимозаменяемость
            if (canSwapPractices(practice, other)) {
                // Пробуем сделать swap
                if (performSwap(practice, other, educator, educatorLessons, minDate, semesterEnd, lectureDates)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Проверяет, можно ли поменять две практики местами.
     * Учитывает: группы, требования к аудиториям, продолжительность цепочки.
     */
    private boolean canSwapPractices(Lesson practice1, Lesson practice2) {
        // 1. Группы должны совпадать
        if (!practice1.getStudyStream().equals(practice2.getStudyStream())) {
            return false;
        }

        // 2. Требования к аудиториям должны быть совместимы
        boolean practice1NeedsRoom = practice1.getRequiredAuditorium() != null;
        boolean practice2NeedsRoom = practice2.getRequiredAuditorium() != null;

        // Если обе требуют конкретные аудитории — они должны быть одинаковыми
        if (practice1NeedsRoom && practice2NeedsRoom) {
            if (!practice1.getRequiredAuditorium().equals(practice2.getRequiredAuditorium())) {
                return false;
            }
        }
        // Если только одна требует — не меняем (можем лишиться аудитории)
        if (practice1NeedsRoom != practice2NeedsRoom) {
            return false;
        }

        // 3. Длина цепочки должна быть одинаковой
        List<Integer> chain1 = slotChainService.getFullChain(practice1.getCurriculumSlot().getId());
        List<Integer> chain2 = slotChainService.getFullChain(practice2.getCurriculumSlot().getId());

        if (chain1.size() != chain2.size()) {
            return false;
        }

        return true;
    }

    /**
     * Выполняет обмен двух практик местами.
     * 1. Убираем other из его текущего места
     * 2. Размещаем practice на место other
     * 3. Размещаем other на первое свободное место >= minDate
     */
    private boolean performSwap(Lesson practice, Lesson other, Educator educator, List<Lesson> educatorLessons,
                                LocalDate minDate, LocalDate semesterEnd,
                                Set<LocalDate> lectureDates) {
        CellForLesson otherCell = workspace.getCellForLesson(other);
        if (otherCell == null) return false;

        LocalDate otherDate = otherCell.getDate();

        // 1. Проверяем: можно ли разместить practice на место other?
        List<CellForLesson> otherDayCells = CellForLessonFactory.getCellsForDate(otherDate);
        if (!canPlacePracticeInCells(practice, otherDayCells)) {
            return false;
        }

        // 2. Убираем other из расписания
        workspace.removePlacement(other);
        distributedLessons.remove(other);
        distributedLessonsSet.remove(other);

        List<Lesson> practiceChain = getChainForLesson(practice, educatorLessons);
        if (!tryPlaceChainInDay(practiceChain, otherDayCells, false)) {
            // Не удалось — откатываем
            PlacementOption otherOption = workspace.findPlacementOption(other, otherCell);
            if (otherOption.isPossible()) {
                workspace.executePlacement(otherOption);
                distributedLessons.add(other);
                distributedLessonsSet.add(other);
            }
            return false;
        }

        // 4. Размещаем other на новое место (приоритет: даты с лекциями)
        LocalDate otherMinDate = findMinDateForPractice(other, educator);
        LocalDate newDateForOther = findAvailableDateForPractice(other, otherMinDate, semesterEnd, lectureDates);

        if (newDateForOther != null) {
            placePracticeInDate(other, educator, educatorLessons, newDateForOther);
            return true;
        } else {
            // Не нашли место для other — откатываем swap
            // Убираем practice
            for (Lesson p : practiceChain) {
                workspace.removePlacement(p);
                distributedLessons.remove(p);
                distributedLessonsSet.remove(p);
            }
            // Возвращаем other
            PlacementOption otherOption = workspace.findPlacementOption(other, otherCell);
            if (otherOption.isPossible()) {
                workspace.executePlacement(otherOption);
                distributedLessons.add(other);
                distributedLessonsSet.add(other);
            }
            return false;
        }
    }

    /**
     * Проверяет, можно ли разместить практику в указанных ячейках дня.
     */
    private boolean canPlacePracticeInCells(Lesson practice, List<CellForLesson> dayCells) {
        List<Lesson> chain = Collections.singletonList(practice);
        // Быстрая проверка без реального размещения
        for (CellForLesson cell : dayCells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                continue;
            }
            PlacementOption option = workspace.findPlacementOption(practice, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }

    private void rollbackPractices(Educator educator) {
        // Ищем, что мы только что распределили
        List<Lesson> toRemove = new ArrayList<>();

        for (Lesson lesson : distributedLessons) {
            // Если это практика этого препода -> Удаляем
            if (lesson.getEducators().contains(educator) && lesson.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE) {
                workspace.removePlacement(lesson);
                toRemove.add(lesson);
            }
        }
        // Убираем из списка "готовых", чтобы на Этапе 2 алгоритм снова их взял
        distributedLessons.removeAll(toRemove);
        distributedLessonsSet.removeAll(toRemove);
    }

    public void distributeLessonsForEducator(Educator educator, List<Lesson> educatorLessons, LocalDate semesterEnd) {
        //TODO вынести флаг признака распределения последовательно или парарллельно в другоей место
        boolean ruleDistributing = false;
        List<Lesson> sortedLessons = lessonSortingService.changeOrderLessons(educatorLessons, ruleDistributing);
        if (sortedLessons.isEmpty()) return;

        // 2. Расчет необходимого количества дней
        int neededDays = lessonSortingService.calculateDaysForListLessons(sortedLessons);

        // первый урок как образец для проверки ограничений (группа, препод)
        Lesson prototypeLesson = sortedLessons.get(0);

        List<LocalDate> viableDates = CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isAfter(semesterEnd)) // Граница семестра
                .filter(d -> isDayViableForLesson(d, prototypeLesson)) // <--- ГЛАВНЫЙ ФИЛЬТР
                .sorted()
                .collect(Collectors.toList());

        if (viableDates.isEmpty()) {
            log.error("CRITICAL: Нет ни одного доступного дня для {}", educator.getName());
            return;
        }
        // 4. Выбор целевых дат (Равномерность)
        List<Integer> targetDateIndices = lessonSortingService.distributeLessonsEvenly(viableDates.size(), neededDays);

        int lessonIndex = 0;

        // 5. Главный цикл распределения
        for (Integer dateIdx : targetDateIndices) {
            if (lessonIndex >= sortedLessons.size()) break;

            // Идеальная дата
            LocalDate idealDate = viableDates.get(dateIdx);
            Lesson nextLesson = sortedLessons.get(lessonIndex);

            // Поиск ближайшей доступной даты (С учетом занятости препода/группы)
            LocalDate realDate = findNearestAvailableDate(idealDate, nextLesson, viableDates);

            if (realDate == null) {
                log.error("CRITICAL: Не нашлось свободного дня для {}", nextLesson);
                continue; // Пропускаем день, пробуем в следующий индекс
            }

            // Заполняем этот день
            List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(realDate);
            int lecturesToday = 0;
            int practicesToday = 0;

            // Пытаемся вставить максимум уроков в этот день
            while (lessonIndex < sortedLessons.size()) {
                Lesson currentLesson = sortedLessons.get(lessonIndex);

                // Проверка лимитов дня (чтобы соответствовать расчету neededDays)
                boolean isLecture = currentLesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE;
                if (isLecture) {
                    if (lecturesToday > 0) break; // Только 1 лекция в день
                } else {
                    if (practicesToday >= 2) break; // Максимум 2 практики
                }

                // Работаем с Цепочкой или Одиночкой атомарно
                List<Lesson> chain = getChainForLesson(currentLesson, sortedLessons);

                // Защита от дублей (если вернулась середина)
                if (chain.isEmpty() || (chain.size() > 1 && !chain.get(0).equals(currentLesson))) {
                    lessonIndex++;
                    continue;
                }

                // Пытаемся разместить цепочку (или одиночный урок)
                if (tryPlaceChainInDay(chain, dayCells, lecturesToday == 0)) {
                    // УСПЕХ
                    lessonIndex += chain.size();

                    // Обновляем счетчики
                    for (Lesson l : chain) {
                        if (l.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE) {
                            lecturesToday++;
                            practicesToday = 0;
                        } else {
                            practicesToday++;
                        }
                    }
                } else {
                    // Не влезло -> День заполнен или не подходит -> Идем к следующей дате
                    break;
                }
            }

        }

        // Лог остатка
        if (lessonIndex < sortedLessons.size()) {
            log.warn("\n=== WARN: Нераспределенные занятия для {} ===", educator.getName());
            log.warn("Всего не влезло: {}", (sortedLessons.size() - lessonIndex));

            for (int i = lessonIndex; i < sortedLessons.size(); i++) {
                Lesson l = sortedLessons.get(i);
                log.warn("  -> Урок [{}]: {} | Группы: {} | Позиция в плане: {}",
                        i, l.getKindOfStudy(), l.getStudyStream().getName(), l.getCurriculumSlot().getPosition());

                if (l.getRequiredAuditorium() != null) {
                    log.warn("     ! Требует аудиторию: {}", l.getRequiredAuditorium().getName());
                }

                // Проверка доступности (почему не влезло?)
                // Попробуем "ткнуть" в первый попавшийся слот, чтобы увидеть причину отказа
                CellForLesson testCell = CellForLessonFactory.getAllCells().get(0);
                PlacementOption option = workspace.findPlacementOption(l, testCell);
                if (!option.isPossible()) {
                    log.warn("     ! Причина отказа (тест на 1-м слоте): {}", option.failureReason());
                }
            }
            log.warn("===============================================================\n");
            log.warn("WARN: Нераспределенные занятия для {}: {}", educator.getName(), (sortedLessons.size() - lessonIndex));
        }
    }

    private List<Lesson> getChainForLesson(Lesson startLesson, List<Lesson> allSortedLessons) {
        // Получаем ID всех слотов в цепочке
        List<Integer> chainSlotIds = slotChainService.getFullChain(startLesson.getCurriculumSlot().getId());

        if (chainSlotIds.size() <= 1) {
            return Collections.singletonList(startLesson);
        }

        // Фильтруем уроки из общего списка, которые входят в эту цепочку и принадлежат этой группе
        // Важно: берем их из allSortedLessons, чтобы сохранить порядок сортировки
        Integer streamId = startLesson.getStudyStream().getId();

        List<Lesson> chain = allSortedLessons.stream()
                .filter(l -> chainSlotIds.contains(l.getCurriculumSlot().getId())
                        && l.getStudyStream().getId().equals(streamId))
                // Дополнительная сортировка по позиции, чтобы убедиться в порядке
                .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                .collect(Collectors.toList());

        // Проверяем: если startLesson не первый в цепочке, значит, мы уже распределили эту цепочку ранее?
        // Или если мы наткнулись на середину.
        // Чтобы не дублировать: если startLesson != chain.get(0), возвращаем пустой список (пропускаем)
        if (!chain.isEmpty() && !chain.get(0).equals(startLesson)) {
            // Это значит, что startLesson - это 2-й или 3-й элемент цепочки.
            // Но мы должны были обработать их вместе с 1-м.
            // Если мы здесь, значит, алгоритм дошел до 2-го элемента. Мы должны его пропустить,
            // так как он уже должен быть распределен (или не распределен вместе с 1-м).
            // Но логика цикла while (lessonIndex += size) должна была это предотвратить.
            // На всякий случай вернем то, что есть, но это сигнал об ошибке логики вызова.
            return Collections.singletonList(startLesson);
        }

        return chain;
    }

    private boolean tryPlaceChainInDay(List<Lesson> chain, List<CellForLesson> dayCells, boolean dayHasNoLecturesYet) {
        if (chain.isEmpty()) return true;
        if (dayCells.isEmpty()) return false;

        // Сортируем ячейки дня по времени
        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        int chainSize = chain.size();

        // Перебор возможных стартовых позиций (Скользящее окно)
        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            List<PlacementOption> transaction = new ArrayList<>();
            boolean fit = true;

            // Проверяем каждый урок цепочки
            for (int j = 0; j < chainSize; j++) {
                Lesson lesson = chain.get(j);
                CellForLesson cell = dayCells.get(i + j);

                // 1. Проверка непрерывности слотов
                if (j > 0) {
                    CellForLesson prevCell = dayCells.get(i + j - 1);
                    if (cell.getTimeSlotPair().ordinal() != prevCell.getTimeSlotPair().ordinal() + 1) {
                        fit = false;
                        break; // Разрыв в расписании дня (окно)
                    }
                }

                // 2. Правило 4-й пары
                if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                    fit = false;
                    break;
                }

                // 3. Правило "Практики без лекций - не на 1-ю пару"
                // Проверяем для первого урока в цепочке (или для всех)
                boolean isLecture = lesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE;
                if (!isLecture && dayHasNoLecturesYet && cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FIRST) {
                    // Если это цепочка Практика+Практика, и мы пытаемся поставить первую на утро - отказ
                    fit = false;
                    break;
                }

                // 4. Проверка доступности (Workspace)
                PlacementOption option = workspace.findPlacementOption(lesson, cell);
                if (!option.isPossible()) {
                    fit = false;
                    break;
                }

                transaction.add(option);
            }

            // Если всё подошло
            if (fit) {
                // Коммитим транзакцию (размещаем реально)
                for (int j = 0; j < chainSize; j++) {
                    PlacementOption op = transaction.get(j);
                    workspace.executePlacement(op);
                    distributedLessons.add(op.lessonToPlace());
                    distributedLessonsSet.add(op.lessonToPlace());
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Ищет ближайшую дату к idealDate, в которую возможно поставить урок.
     * Проверяет ограничения преподавателя и группы (через Workspace).
     */
    private LocalDate findNearestAvailableDate(LocalDate start, Lesson lesson, List<LocalDate> allDates) {
        int startIndex = allDates.indexOf(start);
        if (startIndex == -1) return null;

        // Ищем в радиусе (например, 7 дней вперед и назад)
        // Приоритет: 0 (сам день), +1, -1, +2, -2...
        for (int offset = 0; offset < 14; offset++) {
            // Пробуем сдвиг вперед и назад
            int[] tryIndices = (offset == 0) ? new int[]{startIndex} : new int[]{startIndex + offset, startIndex - offset};

            for (int idx : tryIndices) {
                if (idx >= 0 && idx < allDates.size()) {
                    LocalDate candidateDate = allDates.get(idx);

                    // Проверяем: Есть ли в этот день ХОТЬ ОДИН свободный слот для всех участников?
                    if (isDayViableForLesson(candidateDate, lesson)) {
                        return candidateDate;
                    }
                }
            }
        }
        return null; // Не нашли ничего рядом
    }

    /**
     * Быстрая проверка: может ли урок теоретически встать в этот день?
     */
    private boolean isDayViableForLesson(java.time.LocalDate date, Lesson lesson) {
        List<CellForLesson> cells = CellForLessonFactory.getCellsForDate(date);
        for (CellForLesson cell : cells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) continue;

            // Спрашиваем Workspace: "Если я захочу поставить сюда, есть ли препятствия?"
            // Это учитывает и занятость препода в других группах, и занятость группы в других предметах.
            if (workspace.findPlacementOption(lesson, cell).isPossible()) {
                return true; // Нашли хотя бы одну дырку
            }
        }
        return false; // Весь день забит или заблокирован ограничениями
    }

    /**
     * Метод распределения одиночного занятия
     *
     * @param lesson    распределяемое занятие
     * @param listCells список доступных ячеек для распределения
     */

    private void distributeSingleLesson(Lesson lesson, List<CellForLesson> listCells) {
        List<CellForLesson> cells = getAvailableCellsForLesson(lesson, listCells);
        for (Iterator<CellForLesson> it = cells.iterator(); it.hasNext(); ) {
            CellForLesson cell = it.next();
            if (isCellFreeForLesson(lesson, cell)) {
                PlacementOption option = workspace.findPlacementOption(lesson, cell);
                if (option.isPossible()) {
                    workspace.executePlacement(option);
                    distributedLessons.add(lesson);
                    distributedLessonsSet.add(lesson);
                }
                it.remove();
                break;
            }
        }
    }


    /**
     * Возвращает список доступных ячеек для распределения занятия. Т.е. все ячейки после предыдущего занятия
     *
     * @param lesson занятие
     * @param cells  список всех ячеек
     * @return List
     */

    private List<CellForLesson> getAvailableCellsForLesson(Lesson lesson, List<CellForLesson> cells) {
        if (lesson == null || cells == null || cells.isEmpty()) {
            return new ArrayList<>();
        }
        Lesson previousLesson = findPreviousLesson(lesson);

        CellForLesson currentCell = workspace.getCellForLesson(previousLesson);
        if (currentCell == null) {
            return new ArrayList<>(cells); // Если ячейка не найдена, возвращаем все
        }

        return cells.stream()
                .filter(cell -> {
                    // Сравниваем даты
                    int dateComparison = cell.getDate().compareTo(currentCell.getDate());

                    if (dateComparison > 0) {
                        // Если дата ячейки позже текущей - включаем
                        return true;
                    } else if (dateComparison == 0) {
                        // Если та же дата, сравниваем временные слоты
                        return cell.getTimeSlotPair().ordinal() > currentCell.getTimeSlotPair().ordinal();
                    }
                    // Если дата раньше - исключаем
                    return false;
                })
                .collect(Collectors.toList());
    }


    /**
     * Проверяет возможность назначения занятия в заданную ячейку для всех сущностей занятия
     */
    private boolean isCellFreeForLesson(Lesson lesson, CellForLesson cell) {
        return workspace.findPlacementOption(lesson, cell).isPossible();
    }

    /**
     * Находит предыдущее занятие для указанного урока, учитывая общие группы в groupCombinations.
     *
     * @param currentLesson текущее занятие, для которого ищем предыдущее
     * @return предыдущее занятие или null, если не найдено
     */

    private Lesson findPreviousLesson(Lesson currentLesson) {
        if (currentLesson == null || currentLesson.getCurriculumSlot() == null) {
            return null;
        }

        // Получаем данные текущего занятия для сравнения
        Integer currentDisciplineId = currentLesson.getDisciplineCourse().getDiscipline().getId();
        Integer currentSlotId = currentLesson.getCurriculumSlot().getId();
        // Получаем группы текущего занятия
        Set<Group> currentGroups = currentLesson.getStudyStream() != null
                ? currentLesson.getStudyStream().getGroups()
                : Collections.emptySet();

        // Проходим в обратном порядке
        for (int i = lessons.indexOf(currentLesson) - 1; i >= 0; i--) {
            Lesson candidate = lessons.get(i);

            // Проверки
            boolean sameDiscipline = candidate.getDisciplineCourse().getDiscipline().getId().equals(currentDisciplineId);
            boolean earlierSlot = candidate.getCurriculumSlot().getId() < currentSlotId;

            if (sameDiscipline && earlierSlot) {
                // Проверяем пересечение групп
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

    /**
     * Проверяет, есть ли общие группы между двумя наборами.
     */
    private static boolean hasCommonGroups(Set<Group> groups1, Set<Group> groups2) {
        return !Collections.disjoint(groups1, groups2);
    }


}
