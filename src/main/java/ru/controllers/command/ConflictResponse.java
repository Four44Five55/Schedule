package ru.controllers.command;

/**
 * Ответ при конфликте optimistic lock при попытке редактирования устаревшей версии расписания.
 *
 * <h3>Когда возвращается:</h3>
 * <p>Этот ответ возвращается, когда пользователь пытается изменить расписание,
 * используя устаревшую версию {@code version} (optimistic lock conflict).</p>
 *
 * <h3>Структура ответа:</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 *   <tr><th>Поле</th><th>Тип</th><th>Описание</th></tr>
 *   <tr><td>{@code error}</td><td>{@code String}</td><td>Код ошибки (например, "OPTIMISTIC_LOCK_CONFLICT")</td></tr>
 *   <tr><td>{@code message}</td><td>{@code String}</td><td>Сообщение для пользователя</td></tr>
 *   <tr><td>{@code currentVersion}</td><td>{@code Long}</td><td>Актуальная версия расписания (для обновления UI)</td></tr>
 * </table>
 *
 * <h3>HTTP Status:</h3>
 * <p>Возвращается с HTTP статусом {@code 409 Conflict}.</p>
 *
 * <h3>Пример ответа:</h3>
 * <pre>{@code
 * HTTP/1.1 409 Conflict
 * {
 *   "error": "OPTIMISTIC_LOCK_CONFLICT",
 *   "message": "Расписание было изменено другим пользователем. Обновите страницу.",
 *   "currentVersion": 7
 * }
 * }</pre>
 *
 * <h3>Что должен делать клиент:</h3>
 * <ol>
 *   <li>Отобразить сообщение пользователю</li>
 *   <li>Предложить обновить страницу</li>
 *   <li>Использовать {@code currentVersion} для следующей попытки редактирования</li>
 * </ol>
 *
 * @see org.springframework.orm.ObjectOptimisticLockingFailureException
 * @see ru.entity.write.ScheduleSession#getVersion()
 */
public record ConflictResponse(
    /** Код ошибки (например, "OPTIMISTIC_LOCK_CONFLICT") */
    String error,

    /** Сообщение для пользователя */
    String message,

    /** Актуальная версия расписания (для обновления UI) */
    Long currentVersion
) {
}
