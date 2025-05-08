package ru.entity;

import ru.abstracts.AbstractGrid;
import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractMaterialEntity;
import ru.entity.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ScheduleGrid extends AbstractGrid {
    private final Map<CellForLesson, List<AbstractLesson>> scheduleGridMap = new HashMap<>();

    public ScheduleGrid() {
        super();
        fillBlankCellLessonForSchedule();
    }

    public ScheduleGrid(LocalDate startDate, LocalDate endDate) {
        super(startDate, endDate);
        fillBlankCellLessonForSchedule();
    }

    /**
     * Заполняет расписание занятий днями(дата и пара)
     */
    private void fillBlankCellLessonForSchedule() {
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellsForDateRange(this.getStartDate(), this.getEndDate());
        for (CellForLesson cellForLesson : cellForLessons) {
            scheduleGridMap.put(cellForLesson, new ArrayList<>());
        }
    }

    public Map<CellForLesson, List<AbstractLesson>> getScheduleGridMap() {
        return scheduleGridMap;
    }

    public List<AbstractLesson> getListLessonInCell(CellForLesson cell) {
        return scheduleGridMap.getOrDefault(cell, new ArrayList<>());
    }

    /**
     * Метод для добавления занятия в ячейку
     *
     * @param cell   целевая ячейка для добавления занятия
     * @param lesson занятие
     * @return boolean возвращает true или исключение в случае отсутствия ячейки
     */
    public boolean addLessonToCell(CellForLesson cell, AbstractLesson lesson) {
        if (!scheduleGridMap.containsKey(cell)) {
            return false;  // или throw new IllegalArgumentException("Ячейка не существует");
        }
        scheduleGridMap.get(cell).add(lesson);
        return true;
    }

    /**
     * Подсчитывает количество уникальных дат в scheduleGridMap (по ключам CellForLesson).
     *
     * @return количество уникальных дат (0 если мапа пуста)
     */
    public long getAmountDays() {
        return scheduleGridMap.keySet().stream()
                .map(CellForLesson::getDate)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /**
     * Получает список уроков в указанной ячейке, где используется заданная сущность
     *
     * @param entity проверяемая сущность (аудитория, преподаватель, группа)
     * @param cell   целевая ячейка расписания
     * @return неизменяемый список уроков, использующих сущность. Пустой список, если совпадений нет.
     * @throws NullPointerException если entity или cell == null
     */
    public List<AbstractLesson> getLessonsUsingEntity(AbstractMaterialEntity entity, CellForLesson cell) {
        Objects.requireNonNull(entity, "Сущность не может быть null");
        Objects.requireNonNull(cell, "Ячейка не может быть null");

        List<AbstractLesson> lessons = scheduleGridMap.getOrDefault(cell, Collections.emptyList());

        return lessons.stream()
                .filter(lesson -> lesson.isEntityUsed(entity))
                .collect(Collectors.toUnmodifiableList());
    }
    public ScheduleGrid deepCopy() {
        // Создаём новую сетку с теми же датами
        ScheduleGrid copy = new ScheduleGrid(START_DATE, END_DATE);

        // Копируем все занятия из каждой ячейки, используя кешированные CellForLesson
        for (Map.Entry<CellForLesson, List<AbstractLesson>> entry : this.scheduleGridMap.entrySet()) {
            CellForLesson originalCell = entry.getKey();
            List<AbstractLesson> originalLessons = entry.getValue();

            // Получаем ячейку из кеша (или создаём, если её нет)
            CellForLesson cachedCell = CellForLessonFactory.getCellByDateAndSlot(
                    originalCell.getDate(),
                    originalCell.getTimeSlotPair()
            );

            // Если ячейки нет в кеше (маловероятно, но на всякий случай)
            if (cachedCell == null) {
                cachedCell = new CellForLesson(originalCell.getDate(), originalCell.getTimeSlotPair());
            }

            // Копируем список занятий (сами занятия не клонируем, если они immutable)
            List<AbstractLesson> copiedLessons = new ArrayList<>(originalLessons);

            copy.scheduleGridMap.put(cachedCell, copiedLessons);
        }

        return copy;
    }

}
