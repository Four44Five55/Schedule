package ru.exceptions;

/**
 * Перенос занятия невозможен: целевой слот недоступен на актуальном состоянии расписания.
 *
 * <p>Бросается при повторной валидации переноса, когда один из участников занятия
 * (преподаватель, группа или аудитория) в выбранном слоте уже занят, либо слот лежит
 * вне планируемого периода.</p>
 *
 * <p>В отличие от {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
 * (устаревшая версия сессии) — это конфликт <i>ресурсов</i>, а не версий. На уровне API
 * оба отображаются в {@code 409 Conflict}, но с разными кодами ошибки.</p>
 */
public class LessonMoveConflictException extends RuntimeException {

    public LessonMoveConflictException(String reason) {
        super(reason);
    }
}
