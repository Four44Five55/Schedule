package ru.services.reindex;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Занятие класса в его ТЕКУЩЕЙ ячейке. Содержание неизменно — при пересортировке
 * меняется только дата/пара занятия (тема едет с занятием). {@code planPosition}
 * задаёт правильный порядок изучения (позиция слота в учебном плане), {@code slotId}
 * нужен для проверки сцепок.
 *
 * @param placementId  размещение
 * @param date         текущая дата
 * @param slot         текущая пара
 * @param slotId       слот учебного плана (содержание) — для проверки сцепок
 * @param planPosition позиция в плане (порядок изучения) — по ней сортируем
 */
public record TimedLesson(UUID placementId, LocalDate date, TimeSlotPair slot, int slotId, int planPosition) {
}
