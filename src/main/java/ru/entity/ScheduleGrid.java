package ru.entity;

import ru.abstracts.AbstractAuditorium;
import ru.abstracts.AbstractGrid;
import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractMaterialEntity;
import ru.entity.factories.CellForLessonFactory;
import ru.inter.IGrid;

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


}
