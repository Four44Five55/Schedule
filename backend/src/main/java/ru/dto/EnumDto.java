package ru.dto;

/**
 * Универсальное DTO для передачи enum-значений на фронтенд.
 * Единый формат для ВСЕХ enum-ов системы.
 *
 * @param value        Программное имя (LECTURE, MONDAY, FIRST и т.д.)
 * @param label        Полное название для отображения ("Лекция", "Понедельник")
 * @param abbreviation Сокращение ("Л", "Пн")
 * @param extra        Дополнительная информация (время пары, описание и т.д.)
 */
public record EnumDto(
        String value,
        String label,
        String abbreviation,
        String extra
) {
    /**
     * Конструктор без дополнительной информации.
     */
    public EnumDto(String value, String label, String abbreviation) {
        this(value, label, abbreviation, null);
    }
}
