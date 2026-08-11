package ru.dto;

/**
 * Универсальное DTO для передачи enum-значений на фронтенд.
 * Единый формат для ВСЕХ enum-ов системы.
 *
 * @param value        Программное имя (LECTURE, MONDAY, FIRST и т.д.)
 * @param label        Полное название для отображения ("Лекция", "Понедельник")
 * @param abbreviation Сокращение ("Л", "Пн")
 * @param extra        Дополнительная информация (время пары, описание и т.д.)
 * @param category     Категория значения, если у enum-а есть классификация ({@code null} — если
 *                     нет). Нужна, чтобы фронт НЕ повторял у себя правила вида «экзамен и зачёты —
 *                     это аттестация»: такие списки уже разъезжались с бэком. Классификацией
 *                     владеет Java-enum (см. {@link ru.enums.KindOfStudy.Category}), фронт лишь
 *                     решает, каким цветом её показать.
 */
public record EnumDto(
        String value,
        String label,
        String abbreviation,
        String extra,
        String category
) {
    /**
     * Конструктор без дополнительной информации.
     */
    public EnumDto(String value, String label, String abbreviation) {
        this(value, label, abbreviation, null, null);
    }

    /**
     * Конструктор с доп. информацией, но без классификации.
     */
    public EnumDto(String value, String label, String abbreviation, String extra) {
        this(value, label, abbreviation, extra, null);
    }
}
