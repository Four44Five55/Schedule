package ru.services.distribution.finder;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.services.distribution.DistributionContext;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация поиска через скользящее окно.
 * Вынесены методы из DistributionDiscipline:
 * - findDateInSlidingWindow()
 * - findDateInSlidingWindowForChain()
 * - calculateDynamicWindowSize()
 * - findDateInCompactDays()
 * - findDateInCompactDaysForChain()
 */
@Slf4j
public class SlidingWindowDateFinder implements DateFinder {
    private final DistributionContext context;

    public SlidingWindowDateFinder(DistributionContext context) {
        this.context = context;
    }

    @Override
    public LocalDate findDate(Lesson lesson, LocalDate minDate, List<LocalDate> allDates,
                             Educator educator, Set<LocalDate> lectureDates, LocalDate semesterEnd) {
        // Проверяем, является ли это цепочкой
        List<Lesson> chain = getChainForLesson(lesson);

        if (chain.size() > 1) {
            // Цепочка: используем специальный поиск для цепочек
            return findDateForChain(chain, allDates, educator, lectureDates);
        } else {
            // Одиночная практика
            return findDateForSinglePractice(lesson, allDates, educator, lectureDates);
        }
    }

    /**
     * Поиск даты для одиночной практики.
     */
    public LocalDate findDateForSinglePractice(Lesson practice, List<LocalDate> allDates,
                                               Educator educator, Set<LocalDate> lectureDates) {
        int windowSize = calculateDynamicWindowSize(lectureDates.size());
        return findDateInSlidingWindow(practice, allDates, lectureDates, windowSize, educator);
    }

    /**
     * Поиск даты для цепочки занятий.
     */
    public LocalDate findDateForChain(List<Lesson> chain, List<LocalDate> allDates,
                                     Educator educator, Set<LocalDate> lectureDates) {
        int windowSize = calculateDynamicWindowSize(lectureDates.size());
        return findDateInSlidingWindowForChain(chain, allDates, lectureDates, windowSize, educator);
    }

    /**
     * Поиск даты в скользящем окне с приоритетом лекций и учётом равномерности.
     */
    private LocalDate findDateInSlidingWindow(Lesson practice, List<LocalDate> allDates,
                                              Set<LocalDate> lectureDates, int windowSize, Educator educator) {
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
                int priority = (hasLecture ? 1000 : 0)
                        + (2 - Math.min(practicesInDay, 2)) * 100
                        + (windowSize - distanceFromStart) * 10;

                boolean hasCompactnessBonus = false;
                int nearbyDaysWithLessons = 0;

                // Компактность: бонус за даты, где уже есть занятия преподавателя
                if (useCompactness && educator != null) {
                    int compactnessBonus = calculateCompactnessBonus(date, practice, educator);
                    nearbyDaysWithLessons = countNearbyDaysWithLessons(date, practice, educator);
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
     * Поиск даты в скользящем окне для цепочки занятий.
     */
    private LocalDate findDateInSlidingWindowForChain(List<Lesson> chain, List<LocalDate> allDates,
                                                      Set<LocalDate> lectureDates, int windowSize, Educator educator) {
        boolean useCompactness = educator != null && educator.isCompactSchedule();

        if (useCompactness) {
            LocalDate compactDate = findDateInCompactDaysForChain(chain, allDates, educator);
            if (compactDate != null) {
                log.debug("  → Найдена компактная дата для цепочки: {}", compactDate);
                return compactDate;
            }
        }

        int totalDates = allDates.size();

        for (int offset = 0; offset < totalDates; offset += windowSize) {
            int endIndex = Math.min(offset + windowSize, totalDates);
            List<LocalDate> windowDates = allDates.subList(offset, endIndex);

            log.debug("  Проверка окна для цепочки [{} - {}] из {} дат (compactMode: {})",
                    offset, endIndex, windowDates.size(), useCompactness);

            List<DateCandidate> candidates = new ArrayList<>();

            for (LocalDate date : windowDates) {
                if (!canPlaceChainInDate(chain, date)) {
                    continue;
                }

                boolean hasLecture = lectureDates.contains(date);
                int practicesInDay = countPracticesInDate(date, chain.get(0));
                int distanceFromStart = windowDates.indexOf(date);

                int priority = (hasLecture ? 1000 : 0)
                        + (2 - Math.min(practicesInDay, 2)) * 100
                        + (windowSize - distanceFromStart) * 10;

                boolean hasCompactnessBonus = false;
                int nearbyDaysWithLessons = 0;

                if (useCompactness && educator != null) {
                    int compactnessBonus = calculateCompactnessBonus(date, chain.get(0), educator);
                    nearbyDaysWithLessons = countNearbyDaysWithLessons(date, chain.get(0), educator);
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

            if (!candidates.isEmpty()) {
                candidates.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
                DateCandidate best = candidates.getFirst();
                log.debug("  → Лучший кандидат для цепочки в окне: {}", best.date());
                return best.date();
            }

            log.debug("  В окне [{} - {}] не найдено подходящих дат для цепочки", offset, endIndex);
        }

        return null;
    }

    /**
     * Специальный поиск компактной даты.
     */
    private LocalDate findDateInCompactDays(Lesson practice, List<LocalDate> allDates, Educator educator) {
        Set<LocalDate> datesWithLessons = context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> context.getWorkspace().getCellForLesson(l) != null)
                .map(l -> context.getWorkspace().getCellForLesson(l).getDate())
                .collect(Collectors.toSet());

        if (datesWithLessons.isEmpty()) {
            return null;
        }

        log.debug("  findDateInCompactDays: найдено {} дат с занятиями преподавателя", datesWithLessons.size());

        for (LocalDate date : datesWithLessons) {
            if (!allDates.contains(date)) {
                continue;
            }

            if (canPlacePracticeInDate(practice, date)) {
                log.debug("    → Дата {} подходит для компактного размещения", date);
                return date;
            }

            log.trace("    → Дата {} не подходит: нет свободных слотов", date);
        }

        return null;
    }

    /**
     * Специальный поиск компактной даты для цепочки.
     */
    private LocalDate findDateInCompactDaysForChain(List<Lesson> chain, List<LocalDate> allDates, Educator educator) {
        Set<LocalDate> datesWithLessons = context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> context.getWorkspace().getCellForLesson(l) != null)
                .map(l -> context.getWorkspace().getCellForLesson(l).getDate())
                .collect(Collectors.toSet());

        if (datesWithLessons.isEmpty()) {
            return null;
        }

        for (LocalDate date : datesWithLessons) {
            if (!allDates.contains(date)) {
                continue;
            }

            if (canPlaceChainInDate(chain, date)) {
                return date;
            }
        }

        return null;
    }

    /**
     * Расчитывает размер окна поиска.
     */
    private int calculateDynamicWindowSize(int lectureDatesCount) {
        if (lectureDatesCount <= 3) {
            return 21;
        }
        if (lectureDatesCount <= 7) {
            return 14;
        }
        if (lectureDatesCount <= 14) {
            return 10;
        }
        return 7;
    }

    /**
     * Проверяет, можно ли разместить практику в указанную дату.
     */
    private boolean canPlacePracticeInDate(Lesson practice, LocalDate date) {
        List<CellForLesson> dayCells = CellForLessonFactory.getCellsForDate(date);

        for (CellForLesson cell : dayCells) {
            if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                continue;
            }

            PlacementOption option = context.getWorkspace().findPlacementOption(practice, cell);
            if (option.isPossible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, можно ли разместить цепочку в указанную дату.
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

        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            boolean fit = true;

            for (int j = 0; j < chainSize; j++) {
                CellForLesson cell = dayCells.get(i + j);

                if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                    fit = false;
                    break;
                }

                if (j > 0) {
                    CellForLesson prevCell = dayCells.get(i + j - 1);
                    if (cell.getTimeSlotPair().ordinal() != prevCell.getTimeSlotPair().ordinal() + 1) {
                        fit = false;
                        break;
                    }
                }

                PlacementOption option = context.getWorkspace().findPlacementOption(chain.get(j), cell);
                if (!option.isPossible()) {
                    fit = false;
                    break;
                }
            }

            if (fit) {
                return true;
            }
        }

        return false;
    }

    /**
     * Подсчитывает количество практик уже размещённых в указанную дату.
     */
    private int countPracticesInDate(LocalDate date, Lesson practice) {
        return (int) context.getDistributedLessons().stream()
                .filter(l -> l.getKindOfStudy() != ru.enums.KindOfStudy.LECTURE)
                .filter(l -> l.getEducators().equals(practice.getEducators()))
                .filter(l -> {
                    var cell = context.getWorkspace().getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .count();
    }

    /**
     * Рассчитывает бонус к приоритету за компактность расписания.
     */
    private int calculateCompactnessBonus(LocalDate targetDate, Lesson practice, Educator educator) {
        int lessonsInTargetDay = countLessonsInDate(targetDate, educator);
        if (lessonsInTargetDay > 0) {
            return 500;
        }

        for (int dayOffset = 1; dayOffset <= 2; dayOffset++) {
            LocalDate prevDay = targetDate.minusDays(dayOffset);
            LocalDate nextDay = targetDate.plusDays(dayOffset);

            int lessonsInPrevDay = countLessonsInDate(prevDay, educator);
            int lessonsInNextDay = countLessonsInDate(nextDay, educator);

            if (lessonsInPrevDay > 0 || lessonsInNextDay > 0) {
                return dayOffset == 1 ? 200 : 100;
            }
        }

        return 0;
    }

    /**
     * Подсчитывает количество занятий преподавателя в указанную дату.
     */
    private int countLessonsInDate(LocalDate date, Educator educator) {
        return (int) context.getDistributedLessons().stream()
                .filter(l -> l.getEducators().contains(educator))
                .filter(l -> {
                    var cell = context.getWorkspace().getCellForLesson(l);
                    return cell != null && cell.getDate().equals(date);
                })
                .count();
    }

    /**
     * Подсчитывает количество дней в радиусе ±2 дней, в которых есть занятия преподавателя.
     */
    private int countNearbyDaysWithLessons(LocalDate targetDate, Lesson practice, Educator educator) {
        int count = 0;
        for (int dayOffset = -2; dayOffset <= 2; dayOffset++) {
            if (dayOffset == 0) continue;
            LocalDate checkDate = targetDate.plusDays(dayOffset);
            if (countLessonsInDate(checkDate, educator) > 0) {
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
     * Получает цепочку для занятия (упрощенная версия, используется для определения типа).
     */
    private List<Lesson> getChainForLesson(Lesson lesson) {
        return Collections.singletonList(lesson);
    }
}
