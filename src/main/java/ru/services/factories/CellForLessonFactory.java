package ru.services.factories;

import ru.entity.CellForLesson;
import ru.enums.TimeSlotPair;
import ru.services.ScheduleDaysSlotsConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CellForLessonFactory {
    // Кеш: LocalDate -> TimeSlotPair -> CellForLesson
    private static final Map<LocalDate, Map<TimeSlotPair, CellForLesson>> DATE_SLOT_CACHE = new ConcurrentHashMap<>();

    /**
     * Генерирует ячейки расписания для заданного диапазона дат (включая обе границы)
     * и сохраняет их в кеш. Воскресенья игнорируются.
     *
     * @param startDate начальная дата периода (включительно)
     * @param endDate   конечная дата периода (включительно)
     * @return список созданных ячеек
     */
    public static List<CellForLesson> createCellsForDateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "Начальная дата не может быть null");
        Objects.requireNonNull(endDate, "Конечная дата не может быть null");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Начальная дата не может быть позже конечной: " + startDate + " > " + endDate);
        }

        List<CellForLesson> generatedCells = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // Создаем ячейки для временных слотов
            for (TimeSlotPair slot : TimeSlotPair.values()) {
                // Проверка на доступность сгенерированного слота
                if (!ScheduleDaysSlotsConfig.isSlotAvailable(date, slot)) {
                    continue; // Пропускаем запрещённые слоты
                }

                CellForLesson cell = new CellForLesson(date, slot);

                // Добавляем в кеш
                DATE_SLOT_CACHE.computeIfAbsent(date, k -> new ConcurrentHashMap<>()).put(slot, cell);

                generatedCells.add(cell);
            }
        }

        return generatedCells;
    }

    /**
     * Получает ячейку из кеша по конкретной дате и временному слоту
     *
     * @param date целевая дата
     * @param slot временной слот
     * @return Optional с ячейкой, если она существует
     */
    public static CellForLesson getCellByDateAndSlot(LocalDate date, TimeSlotPair slot) {
        Objects.requireNonNull(date, "Дата не может быть null");
        Objects.requireNonNull(slot, "Временной слот не может быть null");

        return DATE_SLOT_CACHE.getOrDefault(date, Collections.emptyMap()).get(slot);
    }

    /**
     * Получает все ячейки для указанного дня недели и временного слота
     *
     * @param dayOfWeek целевой день недели
     * @param slot      временной слот
     * @return неизменяемый список ячеек
     */
    public static List<CellForLesson> getCellsByDayAndSlot(DayOfWeek dayOfWeek, TimeSlotPair slot) {
        Objects.requireNonNull(dayOfWeek, "День недели не может быть null");
        Objects.requireNonNull(slot, "Временной слот не может быть null");

        return DATE_SLOT_CACHE.entrySet().stream()
                .filter(entry -> entry.getKey().getDayOfWeek() == dayOfWeek)
                .map(entry -> entry.getValue().get(slot))
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Очищает весь кеш (использовать с осторожностью!)
     */
    public static void clearCache() {
        DATE_SLOT_CACHE.clear();
    }

    /**
     * Проверяет существование ячейки в кеше
     *
     * @param date дата
     * @param slot временной слот
     */
    public static boolean containsCell(LocalDate date, TimeSlotPair slot) {
        return DATE_SLOT_CACHE.getOrDefault(date, Collections.emptyMap()).containsKey(slot);
    }

    /**
     * Создание списка CellForLesson для одной даты
     *
     * @param date дата
     * @return список ячеек  CellForLesson
     */
    public static List<CellForLesson> createCellForDate(LocalDate date) {
        Objects.requireNonNull(date, "Дата не может быть null");

        List<CellForLesson> cells = new ArrayList<>();

        if (!(date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            // Для каждой даты создаем объекты CellForLesson для каждого TimeSlotPair
            for (TimeSlotPair timeSlotPair : TimeSlotPair.values()) {
                cells.add(new CellForLesson(date, timeSlotPair));
            }
        }
        return cells;
    }

    /**
     * Создание списка CellForLesson для одной даты
     *
     * @param date дата
     * @return список ячеек  CellForLesson
     */
    public static List<CellForLesson> getCellsForDate(LocalDate date) {
        return Optional.ofNullable(DATE_SLOT_CACHE.get(date)) // Получаем внутреннюю Map по дате
                .map(entry ->
                        Arrays.stream(TimeSlotPair.values()) // Используем порядок из enum
                                .filter(entry::containsKey)   // Фильтруем только существующие слоты
                                .map(entry::get)              // Получаем CellForLesson
                                .collect(Collectors.toList())
                )
                .orElse(Collections.emptyList());       // Если даты нет - пустой список
    }

    /**
     * Возвращает все ячейки из кеша, отсортированные по дате и временному слоту.
     */
    public static List<CellForLesson> getAllOrderedCells() {
        return DATE_SLOT_CACHE.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Сортировка по дате
                .flatMap(entry -> entry.getValue().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()) // Сортировка по TimeSlotPair
                        .map(Map.Entry::getValue)
                )
                .collect(Collectors.toList());
    }
}
