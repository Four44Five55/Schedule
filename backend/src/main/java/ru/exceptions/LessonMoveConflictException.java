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
 *
 * <p><b>Образец, с которого списан словарь {@link DomainException}.</b> Этот класс был здесь
 * единственным доменным исключением: остальные 42 ожидаемые ситуации бросались типами
 * {@code IllegalStateException}/{@code IllegalArgumentException}, то есть словарём «в коде баг».
 * Приём был верный — его просто не размножили.</p>
 */
public class LessonMoveConflictException extends DomainException {

    public LessonMoveConflictException(String reason) {
        super(reason);
    }

    @Override
    public String code() {
        return "MOVE_CONFLICT";
    }
}
