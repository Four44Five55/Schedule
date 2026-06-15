package ru.services.solver.model;

import ru.abstracts.AbstractGrid;
import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Представляет собой сетку расписания как пассивную структуру данных.
 *
 * <p>Ответственность этого класса — хранить карту сопоставлений "временной слот -> список занятий".
 * Он не содержит сложной бизнес-логики, проверок доступности или внутренних индексов,
 * предоставляя простое API для добавления, удаления и получения занятий.</p>
 *
 * <p>Наследуется от {@link AbstractGrid}, чтобы иметь информацию о своих временных границах (дате начала и окончания).</p>
 */
public class ScheduleGrid extends AbstractGrid {

    /**
     * Основное хранилище, где ключ — это временной слот, а значение — список занятий в этом слоте.
     */
    private final Map<CellForLesson, List<AbstractLesson>> scheduleGridMap = new HashMap<>();

    /**
     * Конструктор по умолчанию.
     * Использует startDate и endDate из интерфейса IGrid.
     */
    public ScheduleGrid() {
        super();
        fillBlankCellLessonForSchedule();
    }

    /**
     * Создает экземпляр сетки расписания с указанием временных рамок.
     *
     * @param startDate дата начала периода.
     * @param endDate   дата окончания периода.
     */
    public ScheduleGrid(LocalDate startDate, LocalDate endDate) {
        super(startDate, endDate);
        fillBlankCellLessonForSchedule();
    }

    /**
     * Заполняет расписание занятий днями(дата и пара)
     */
    private void fillBlankCellLessonForSchedule() {
        List<CellForLesson> cellForLessons = CellForLessonFactory.getAllCells();
        for (CellForLesson cellForLesson : cellForLessons) {
            scheduleGridMap.put(cellForLesson, new ArrayList<>());
        }
    }

    /**
     * Получает неизменяемый список занятий в указанной ячейке.
     */
    public List<AbstractLesson> getLessonsIn(CellForLesson cell) {
        return scheduleGridMap.getOrDefault(cell, Collections.emptyList());
    }

    /**
     * Добавляет занятие в указанную ячейку. Не выполняет проверок на конфликты.
     *
     * @param cell   ячейка, в которую добавляется занятие.
     * @param lesson занятие для добавления.
     */
    public void add(CellForLesson cell, AbstractLesson lesson) {
        scheduleGridMap.computeIfAbsent(cell, k -> new ArrayList<>()).add(lesson);
    }

    /**
     * Удаляет указанное занятие из указанной ячейки.
     * Если после удаления список занятий в ячейке становится пустым, сама ячейка удаляется из карты.
     *
     * @param cell   ячейка, из которой удаляется занятие.
     * @param lesson занятие для удаления.
     */
    public void remove(CellForLesson cell, AbstractLesson lesson) {
        List<AbstractLesson> lessons = scheduleGridMap.get(cell);
        if (lessons != null) {
            lessons.remove(lesson);
            if (lessons.isEmpty()) {
                scheduleGridMap.remove(cell);
            }
        }
    }
    /**
     * Находит ячейку (слот), в которой размещено указанное занятие.
     * Выполняет полный перебор сетки, поэтому операция может быть медленной O(N).
     *
     * @param lesson занятие, которое ищем.
     * @return Ячейка, в которой стоит занятие, или null, если не найдено.
     */
    public CellForLesson getCellForLesson(AbstractLesson lesson) {
        for (Map.Entry<CellForLesson, List<AbstractLesson>> entry : scheduleGridMap.entrySet()) {
            if (entry.getValue().contains(lesson)) {
                return entry.getKey();
            }
        }
        return null;
    }
    /**
     * Возвращает всю карту расписания для итерации или отладки.
     */
    public Map<CellForLesson, List<AbstractLesson>> getGridMap() {
        return Collections.unmodifiableMap(scheduleGridMap);
    }
    /**
     * Очищает всю сетку расписания (все назначения).
     * Важно: Ключи (ячейки) остаются, но списки занятий внутри них очищаются.
     * Это безопаснее, чем удалять ключи, так как мы инициализируем их в конструкторе.
     */
    public void clear() {
        for (List<AbstractLesson> lessons : scheduleGridMap.values()) {
            lessons.clear();
        }
    }
}
