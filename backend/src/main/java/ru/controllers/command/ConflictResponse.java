package ru.controllers.command;

/**
 * Ответ при конфликте optimistic lock при попытке редактирования устаревшей версии расписания.
 *
 * <h2>Когда возвращается:</h2>
 * <p>Этот ответ возвращается, когда пользователь пытается изменить расписание,
 * используя устаревшую версию {@code version} (optimistic lock conflict).</p>
 *
 * <h2>Структура ответа:</h2>
 * <table>
 *   <caption>Поля ответа</caption>
 *   <tr><th>Поле</th><th>Тип</th><th>Описание</th></tr>
 *   <tr><td>{@code error}</td><td>{@code String}</td><td>Код ошибки (например, "OPTIMISTIC_LOCK_CONFLICT")</td></tr>
 *   <tr><td>{@code message}</td><td>{@code String}</td><td>Сообщение для пользователя</td></tr>
 *   <tr><td>{@code currentVersion}</td><td>{@code Long}</td><td>Актуальная версия расписания (для обновления UI)</td></tr>
 * </table>
 *
 * <h2>HTTP Status:</h2>
 * <p>Возвращается с HTTP статусом {@code 409 Conflict}.</p>
 *
 * <h2>Пример ответа:</h2>
 * <pre>{@code
 * HTTP/1.1 409 Conflict
 * {
 *   "error": "OPTIMISTIC_LOCK_CONFLICT",
 *   "message": "Расписание было изменено другим пользователем. Обновите страницу.",
 *   "currentVersion": 7
 * }
 * }</pre>
 *
 * <h2>Что должен делать клиент:</h2>
 * <ol>
 *   <li>Отобразить сообщение пользователю</li>
 *   <li>Предложить обновить страницу</li>
 *   <li>Использовать {@code currentVersion} для следующей попытки редактирования</li>
 * </ol>
 *
 * @see org.springframework.orm.ObjectOptimisticLockingFailureException
 * @see ru.entity.write.ScheduleSession
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
