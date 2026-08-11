package ru.dto.constraint;

/**
 * Вид ограничения для фронта.
 *
 * @param code      ключ; в ограничениях хранится именно он
 * @param name      полное название («Командировка»)
 * @param shortName сокращение для ячейки сетки («Ком»)
 * @param color     ключ палитры ({@code amber}, {@code sky}…) — набор CSS-классов знает фронт
 * @param sortOrder порядок в списке
 * @param active    погашенный вид не предлагается в выборе, но уже проставленный показывается
 * @param system    пришёл из кода: удалить нельзя, название и цвет правятся
 * @param usageCount сколько ограничений им размечено — цена удаления, названная заранее
 */
public record ConstraintKindDto(
        String code,
        String name,
        String shortName,
        String color,
        Integer sortOrder,
        boolean active,
        boolean system,
        long usageCount
) {
}
