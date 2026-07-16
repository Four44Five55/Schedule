package ru.dto.auditorium;

/**
 * Здоровье одной аудитории — строка разбивки в {@link AuditoriumHealthDto}.
 *
 * <p>Разбивка по комнатам, а не общий счётчик, потому что причина обычно в самой комнате:
 * маленькая аудитория, назначенная базовой сразу трём группам, ловит конфликты пачками, а
 * приоритетная — единицами и по другой причине. По одному числу это неразличимо, а чинить
 * надо по-разному.</p>
 *
 * @param auditoriumId     комната
 * @param name             имя комнаты (для показа)
 * @param capacity         мест
 * @param conflictingCells в скольких ячейках комната занята больше чем одним занятием
 * @param doubleBooked     сколько занятий в этих ячейках стоит
 * @param overCapacity     сколько занятий не помещается в комнату
 * @param maxExcess        максимальный перебор по людям (0 — переполнений нет)
 */
public record RoomHealthDto(
        Integer auditoriumId,
        String name,
        int capacity,
        int conflictingCells,
        int doubleBooked,
        int overCapacity,
        int maxExcess
) {
}
