package ru.enums;

/**
 * Статусы сессии редактирования расписания (CQRS Command Side).
 *
 * <p>Определяет жизненный цикл сессии:</p>
 * <ol>
 *   <li>INITIALIZED → Сессия создана</li>
 *   <li>GENERATING → Идёт генерация расписания</li>
 *   <li>READY_FOR_EDIT → Готово к ручному редактированию</li>
 *   <li>FINAL → Финальная версия (архивирована)</li>
 *   <li>ARCHIVED → В архиве (только чтение)</li>
 * </ol>
 *
 * @see ru.entity.write.ScheduleSession
 */
public enum SessionStatus {
    /**
     * Сессия инициализирована, но генерация ещё не началась.
     */
    INITIALIZED("Инициализирована"),

    /**
     * Идёт генерация расписания (алгоритм работает).
     */
    GENERATING("Генерация"),

    /**
     * Расписание сгенерировано, готово к ручному редактированию.
     * Пользователи могут перемещать занятия, менять аудитории и т.д.
     */
    READY_FOR_EDIT("Готово к редактированию"),

    /**
     * Финальная версия расписания.
     * Редактирование запрещено, только чтение.
     */
    FINAL("Финальная"),

    /**
     * Сессия архивирована.
     * Только чтение для исторических данных.
     */
    ARCHIVED("Архивирована");

    private final String russianName;

    SessionStatus(String russianName) {
        this.russianName = russianName;
    }

    public String getRussianName() {
        return russianName;
    }

    /**
     * Проверить, можно ли редактировать расписание в этом статусе.
     *
     * @return true если редактирование разрешено
     */
    public boolean isEditable() {
        return this == READY_FOR_EDIT;
    }

    /**
     * Проверить, идёт ли сейчас генерация.
     *
     * @return true если генерация в процессе
     */
    public boolean isGenerating() {
        return this == GENERATING;
    }

    /**
     * Проверить, можно ли удалить сессию.
     *
     * @return true если удаление разрешено
     */
    public boolean isDeletable() {
        return this == INITIALIZED || this == ARCHIVED;
    }
}
