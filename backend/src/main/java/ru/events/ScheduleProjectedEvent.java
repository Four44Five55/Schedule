package ru.events;

import lombok.Getter;

import java.util.UUID;

/**
 * Read-модель сессии обновлена и ГОТОВА К ЧТЕНИЮ.
 *
 * <p>Публикуется {@link ru.services.ScheduleSynchronizer} — единственным писателем
 * {@code schedule_view} — после того, как строки проекции записаны. Смысл события ровно один:
 * «теперь перечитывать безопасно».</p>
 *
 * <p><b>Зачем отдельное событие, если есть {@link PlacementChangedEvent}.</b> Те события говорят
 * «данные изменились» и обрабатываются АСИНХРОННО: в момент их публикации {@code schedule_view}
 * ещё старая. Клиент, перечитавший по ним, увидел бы прежнее положение занятия — ровно от этой
 * гонки во фронте жили фиксированные паузы {@code setTimeout(1000)}. Это событие — про другое:
 * оно наступает ПОСЛЕ записи проекции, и подписчику не нужно ничего угадывать.</p>
 *
 * @see ru.services.stream.ScheduleChangeNotifier
 */
@Getter
public class ScheduleProjectedEvent {

    /** Сессия, чья проекция обновлена. */
    private final UUID sessionId;

    public ScheduleProjectedEvent(UUID sessionId) {
        this.sessionId = sessionId;
    }
}
