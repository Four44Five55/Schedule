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
    MANUAL("Вручную"),

    /**
     * Привезено импортом из сторонней программы.
     *
     * <p><b>Миграции не требует:</b> колонка {@code source} — {@code VARCHAR(20)} без {@code CHECK},
     * значение живёт в этом enum'е (И-9).</p>
     *
     * <p>⚠️ Само по себе <b>не защищает импорт от перегенерации</b>: {@code regenerateKeepingLocked}
     * смотрит на {@code locked}, а не на {@code source}. Поэтому импортные размещения ставятся
     * заблокированными, а этот признак отвечает только на вопрос «откуда оно взялось».</p>
     */
    IMPORTED("Импортировано");

    private final String russianName;

    PlacementSource(String russianName) {
        this.russianName = russianName;
    }

    public String getRussianName() {
        return russianName;
    }
}
