package ru.services.distribution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Сервис для поиска доступных дат проведения занятий.
 * Отвечает только за подбор дат, не за размещение.
 */
@Slf4j
@RequiredArgsConstructor
public class LessonDateFinder {
    /**
     * Запас дней от конца диапазона для резервных дат
     */
    private static final int DAYS_END_BUFFER = 4;

    private final DistributionContext context;
    private final LessonPlacementService placement;

    /**
     * Находит первую доступную дату для занятия из списка дат.
     *
     * @param lesson занятие
     * @param dates  список candidate-дат (приоритетных)
     * @return первая подходящая дата или null
     */
    public LocalDate findFirstAvailable(Lesson lesson, List<LocalDate> dates) {
        for (LocalDate date : dates) {
            if (canPlaceOn(lesson, date)) {
                return date;
            }
        }
        return null;
    }

    /**
     * Проверяет, можно ли провести занятие в указанную дату.
     */
    public boolean canPlaceOn(Lesson lesson, LocalDate date) {
        return placement.canPlace(lesson, date);
    }

    /**
     * Получает все доступные даты для занятия в заданном периоде.
     *
     * @param lesson      занятие
     * @param semesterEnd конец семестра
     * @return отсортированный список доступных дат
     */
    public List<LocalDate> getAvailableDates(Lesson lesson, LocalDate semesterEnd) {
        return CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isAfter(semesterEnd))
                .filter(d -> canPlaceOn(lesson, d))
                .sorted()
                .toList();
    }

    /**
     * Получает все доступные даты для списка занятий.
     * Дата считается доступной, если подходит хотя бы для одного занятия.
     */
    public List<LocalDate> getAvailableDates(List<Lesson> lessons, LocalDate semesterEnd) {
        if (lessons.isEmpty()) {
            return List.of();
        }

        Lesson prototype = lessons.getFirst();
        return CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isAfter(semesterEnd))
                .filter(d -> canPlaceOn(prototype, d))
                .sorted()
                .toList();
    }

    /**
     * Вычисляет даты для размещения занятий с равномерным распределением.
     * <p>
     * Логика группировки:
     * - Лекция + >=2 практик после = 1 дата (лекция + 2 практики)
     * - Лекция + <2 практик = только лекция = 1 дата
     * - Практики = по 2 на дату
     * <p>
     * Даты распределяются равномерно по рабочему диапазону с запасом от конца.
     *
     * @param lessons        отсортированный список занятий
     * @param availableDates доступные даты
     * @return маппинг занятие → дата
     */
    public Map<Lesson, LocalDate> calculatePotentialDates(List<Lesson> lessons, List<LocalDate> availableDates) {
        // 1. Подсчитываем сколько нужно дат
        int datesNeeded = countDatesNeeded(lessons);

        // 2. Выбираем даты равномерно из рабочего диапазона
        List<LocalDate> selectedDates = selectDatesEvenly(availableDates, datesNeeded);

        // 3. Создаём маппинг занятие → дата
        return createLessonDateMapping(lessons, selectedDates);
    }

    /**
     * Равномерно выбирает даты из списка, оставляя запас в конце.
     *
     * @param availableDates все доступные даты
     * @param count          сколько дат нужно выбрать
     * @return выбранные даты
     */
    private List<LocalDate> selectDatesEvenly(List<LocalDate> availableDates, int count) {
        if (availableDates.isEmpty() || count <= 0) {
            return List.of();
        }

        // Рабочий диапазон: без последних DAYS_END_BUFFER дней
        int workRangeSize = Math.max(1, availableDates.size() - DAYS_END_BUFFER);
        workRangeSize = Math.min(workRangeSize, availableDates.size());

        List<LocalDate> workRange = availableDates.subList(0, workRangeSize);

        // Если нужно меньше дат чем в рабочем диапазоне — выбираем равномерно
        if (count <= workRangeSize) {
            return distributeEvenly(workRange, count);
        }

        // Иначе берём весь рабочий диапазон + добавляем из запаса если нужно
        List<LocalDate> result = new ArrayList<>(workRange);
        if (workRangeSize < availableDates.size() && count > workRangeSize) {
            int remaining = Math.min(count - workRangeSize, availableDates.size() - workRangeSize);
            result.addAll(availableDates.subList(workRangeSize, workRangeSize + remaining));
        }

        return result;
    }

    /**
     * Распределяет даты равномерно по списку.
     *
     * @param dates список дат
     * @param count сколько выбрать
     * @return выбранные даты
     */
    private List<LocalDate> distributeEvenly(List<LocalDate> dates, int count) {
        if (count <= 0) return List.of();
        if (count == 1) return List.of(dates.getFirst());
        if (count >= dates.size()) return new ArrayList<>(dates);

        List<LocalDate> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i * (dates.size() - 1) / (count - 1);
            result.add(dates.get(index));
        }
        return result;
    }

    /**
     * Создаёт маппинг занятие → дата на основе группировки.
     */
    private Map<Lesson, LocalDate> createLessonDateMapping(List<Lesson> lessons, List<LocalDate> dates) {
        Map<Lesson, LocalDate> result = new LinkedHashMap<>();
        int dateIndex = 0;
        int i = 0;

        while (i < lessons.size() && dateIndex < dates.size()) {
            Lesson current = lessons.get(i);
            LocalDate date = dates.get(dateIndex);

            if (isLecture(current)) {
                // Считаем практики после лекции
                int practicesAfter = 0;
                for (int j = i + 1; j < lessons.size() && !isLecture(lessons.get(j)); j++) {
                    practicesAfter++;
                }

                if (practicesAfter >= 2) {
                    // Лекция + 2 практики = 1 дата
                    result.put(lessons.get(i), date);
                    result.put(lessons.get(i + 1), date);
                    result.put(lessons.get(i + 2), date);
                    i += 3;
                } else {
                    // Только лекция = 1 дата
                    result.put(current, date);
                    i++;
                }
            } else {
                // Практики
                result.put(current, date);
                if (i + 1 != lessons.size() && lessons.get(i + 1).getKindOfStudy() != KindOfStudy.LECTURE) {
                    result.put(lessons.get(i + 1), date);
                    i += 2;
                } else {
                    i++;
                }
            }
            dateIndex++;
        }

        return result;
    }

    /**
     * Подсчитывает количество нужных дат для списка занятий.
     */
    private int countDatesNeeded(List<Lesson> lessons) {
        int dates = 0;
        int i = 0;

        while (i < lessons.size()) {
            Lesson current = lessons.get(i);

            if (isLecture(current)) {
                // Считаем практики после лекции
                int practicesAfter = 0;
                for (int j = i + 1; j < lessons.size() && !isLecture(lessons.get(j)); j++) {
                    practicesAfter++;
                }

                if (practicesAfter >= 2) {
                    i += 3;
                } else {
                    i++;
                }
            } else {
                if (i + 1 != lessons.size() && lessons.get(i + 1).getKindOfStudy() != KindOfStudy.LECTURE) {
                    i += 2;
                } else {
                    i++;
                }
            }
            dates++;
        }

        return dates;
    }

    private boolean isLecture(Lesson lesson) {
        return lesson.getKindOfStudy() == KindOfStudy.LECTURE;
    }

    /**
     * Получает доступные даты с явным набором skipPairs.
     * Используется стратегией для анализа и размещения.
     */
    public List<LocalDate> getAvailableDates(Lesson lesson,
                                             LocalDate semesterEnd,
                                             Set<TimeSlotPair> skipPairs) {
        return CellForLessonFactory.getAllCells().stream()
                .map(CellForLesson::getDate)
                .distinct()
                .filter(d -> !d.isAfter(semesterEnd))
                .filter(d -> placement.canPlace(lesson, d, skipPairs))
                .sorted()
                .toList();
    }

    /**
     * Считает количество уникальных дней когда хотя бы одно занятие
     * из списка может быть размещено с указанными ограничениями.
     * Используется селектором стратегии.
     */
    public int countAvailableDays(List<Lesson> practices,
                                  LocalDate semesterEnd,
                                  Set<TimeSlotPair> skipPairs) {
        if (practices.isEmpty()) return 0;

        // Берём первое занятие как репрезентативное —
        // у всех практик одного преподавателя ограничения схожи
        Lesson representative = practices.getFirst();
        return getAvailableDates(representative, semesterEnd, skipPairs).size();
    }

    public List<LocalDate> filterAvailableDates(Lesson lesson, List<LocalDate> dates) {
        return dates.stream()
                .filter(d -> canPlaceOn(lesson, d))
                .toList();
    }

    public List<LocalDate> datesWithPriority(List<LocalDate> allDates, Set<LocalDate> priorityDates) {
        List<LocalDate> result = new ArrayList<>();

        for (LocalDate date : allDates) {
            if (priorityDates.contains(date)) {
                result.add(date);
            }
        }

        for (LocalDate date : allDates) {
            if (!priorityDates.contains(date)) {
                result.add(date);
            }
        }

        return result;
    }

    public LocalDate findBestDate(Lesson lesson, List<LocalDate> allDates, Set<LocalDate> priorityDates) {
        List<LocalDate> prioritized = datesWithPriority(allDates, priorityDates);
        return findFirstAvailable(lesson, prioritized);
    }
}
