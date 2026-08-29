/**
 * Словарь доменных исключений: то, что приложение умеет объяснить человеку.
 *
 * <p>{@code DomainException} — абстрактный корень с {@code code()} для машинного ветвления фронта;
 * наследники: {@code DuplicateException} ({@code DUPLICATE}), {@code InUseException}
 * ({@code IN_USE}, с необязательным {@code usageCount}), {@code RuleViolationException}
 * ({@code RULE_VIOLATION}), {@code LessonMoveConflictException} ({@code MOVE_CONFLICT}).
 * Наружу они выходят в форме RFC 9457 Problem Details через {@code ApiExceptionHandler}.</p>
 *
 * <p><b>Что сюда НЕ заводится.</b> Баги — «не удалось восстановить занятие», «Unknown entity type»,
 * нарушенные предусловия фабрик — остаются {@code IllegalStateException}/
 * {@code IllegalArgumentException} и обязаны лететь в 500 со стеком: их некому поймать, и показывать
 * их как замечание пользователю неправильно. Правило «где ловить, что заводить, что пропускать» —
 * в {@code docs/CONVENTIONS.md}.</p>
 *
 * <p>⚠️ Ловушки живут в паре с бросками: {@code catch (IllegalStateException)} остаётся валидным
 * Java-кодом и просто перестаёт срабатывать, когда бросок переведён на доменное исключение, —
 * компилятор об этом не скажет, а осмысленный 409 превратится в 500.</p>
 */
package ru.exceptions;
