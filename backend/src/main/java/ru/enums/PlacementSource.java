package ru.enums;

/**
 * Происхождение размещения занятия ({@link ru.entity.write.LessonPlacement}).
 *
 * <p>Используется вместе с флагом {@code locked} для Фичи 2 (ручное размещение / пины):
 * {@code source} говорит <i>кто</i> поставил занятие, {@code locked} — <i>можно ли</i>
 * его трогать при (ре)генерации.</p>
 *
 * <p>Намеренно enum (а не boolean): оставляет шов под будущие источники
 * ({@code IMPORTED}, {@code FIXED_BY_RULE}) без миграции схемы — только новые значения.</p>
 *
 * @see ru.entity.write.LessonPlacement
 */
public enum PlacementSource {
    /**
     * Поставлено алгоритмом-распределителем при генерации.
     */
    GENERATED("Сгенерировано"),

    /**
     * Поставлено диспетчером вручную.
     */
    MANUAL("Вручную");

    private final String russianName;

    PlacementSource(String russianName) {
        this.russianName = russianName;
    }

    public String getRussianName() {
        return russianName;
    }
}
