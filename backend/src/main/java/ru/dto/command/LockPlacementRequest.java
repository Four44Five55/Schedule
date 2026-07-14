package ru.dto.command;

import java.util.List;
import java.util.UUID;

/**
 * Запрос на закрепление/открепление размещения (пин, Фича 2).
 *
 * @param locked       желаемое состояние: {@code true} — закрепить, {@code false} — открепить
 * @param placementIds опционально: сузить действие до подмножества цепочки якоря (эфемерный
 *                     разрыв сцепки на фронте — {@code detachedBoundaries} в
 *                     {@code AcademicGridSchedule}). Бэк проверяет принадлежность каждого id
 *                     реальной цепочке якоря — лишнее игнорируется. {@code null}/пусто —
 *                     старое поведение (закрепляется вся цепочка).
 * @param version      ожидаемая версия сессии (optimistic lock)
 */
public record LockPlacementRequest(
        boolean locked,
        List<UUID> placementIds,
        Long version
) {
}
