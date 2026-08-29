package ru.entity;

import ru.entity.constraints.ConstraintKindRef;

import java.util.HashMap;
import java.util.Map;

/**
 * Постоянные ограничения одного ресурса по ячейкам: «когда он занят не занятием».
 *
 * <p>Собственного периода у карты нет, и наследование от {@code AbstractGrid} снято 2026-08-27:
 * границ она не использовала ни разу, а доставались они из конструктора без дат — то есть из
 * захардкоженной весны 2026. Разворотом диапазона ограничения в ячейки владеет
 * {@code ConstraintServiceImpl.expandToCells}; сюда ячейки приходят уже готовыми
 * ({@code SchedulableResource.addHardConstraint}).</p>
 */
public class ConstraintsGrid {
    private final Map<CellForLesson, ConstraintKindRef> constraintsGridMap = new HashMap<>();

    /**
     * Получает карту с ограничениями
     *
     * @return {@code Map<CellForLesson, ConstraintKindRef>} — карта ячейка → вид ограничения
     */
    public Map<CellForLesson, ConstraintKindRef> getConstraintsGridMap() {
        return constraintsGridMap;
    }

    /**
     * Получает карту ограничение
     *
     * @param cell ячейка
     * @return ConstraintKindRef
     */
    public ConstraintKindRef getConstraint(CellForLesson cell) {
        return constraintsGridMap.get(cell);
    }

    /**
     * Проверяет на пустоту ячейку
     *
     * @param cell проверяемая ячейка
     * @return boolean
     */
    public boolean isFreeCell(CellForLesson cell) {
        return !constraintsGridMap.containsKey(cell);
    }

}
