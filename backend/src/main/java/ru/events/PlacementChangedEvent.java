package ru.events;

import lombok.Getter;
import ru.entity.write.LessonPlacement;

import java.util.UUID;

/**
 * Событие: Размещение занятия изменено.
 *
 * <p>Публикуется при любом изменении размещения:</p>
 * <ul>
 *   <li>Перенос занятия на другую дату/время</li>
 *   <li>Изменение аудитории</li>
 *   <li>Удаление занятия</li>
 * </ul>
 *
 * <p>Запускает асинхронное обновление {@link ru.entity.read.ScheduleView}.</p>
 *
 * <p><b>Пример:</b></p>
 * <pre>
 * // Пользователь переносит занятие через фронтенд
 * POST /api/schedule/sessions/{id}/move-lesson
 * {
 *   "placementId": "...",
 *   "newDate": "2025-01-15",
 *   "newSlot": "SECOND"
 * }
 *
 * // Backend обновляет LessonPlacement (Command Side)
 * placement.updatePlacement(newDate, newSlot, user);
 * placementRepo.save(placement);
 *
 * // ✅ Публикуется событие
 * eventPublisher.publishEvent(new PlacementChangedEvent(sessionId, placementId, placement));
 *
 * // Query Side синхронизируется (асинхронно)
 * scheduleView.setDate(placement.getScheduledDate());
 * scheduleView.setSlot(placement.getScheduledSlot());
 * viewRepo.save(scheduleView);
 * </pre>
 *
 * @see ru.entity.read.ScheduleView
 * @see ru.entity.write.LessonPlacement
 * @see ru.services.ScheduleSynchronizer
 */
@Getter
public class PlacementChangedEvent {

    /**
     * ID сессии расписания.
     */
    private final UUID sessionId;

    /**
     * ID изменённого размещения.
     */
    private final UUID placementId;

    /**
     * Изменённое размещение.
     */
    private final LessonPlacement placement;

    /**
     * Тип изменения.
     */
    private final PlacementChangeType changeType;

    /**
     * Тип изменения размещения.
     */
    public enum PlacementChangeType {
        /**
         * Новое размещение создано.
         */
        CREATED,

        /**
         * Размещение обновлено (перенос, изменение аудитории).
         */
        UPDATED,

        /**
         * Размещение удалено.
         */
        DELETED
    }

    /**
     * Создаёт событие об изменении размещения.
     *
     * @param sessionId ID сессии
     * @param placementId ID размещения
     * @param placement Размещение
     * @param changeType Тип изменения
     */
    public PlacementChangedEvent(
            UUID sessionId,
            UUID placementId,
            LessonPlacement placement,
            PlacementChangeType changeType
    ) {
        this.sessionId = sessionId;
        this.placementId = placementId;
        this.placement = placement;
        this.changeType = changeType;
    }

    /**
     * Создаёт событие об обновлении размещения.
     *
     * @param sessionId ID сессии
     * @param placementId ID размещения
     * @param placement Размещение
     */
    public PlacementChangedEvent(UUID sessionId, UUID placementId, LessonPlacement placement) {
        this(sessionId, placementId, placement, PlacementChangeType.UPDATED);
    }

    /**
     * Создаёт событие об удалении размещения.
     *
     * @param sessionId ID сессии
     * @param placementId ID размещения
     */
    public PlacementChangedEvent(UUID sessionId, UUID placementId) {
        this(sessionId, placementId, null, PlacementChangeType.DELETED);
    }

    /**
     * Проверить, было ли размещение удалено.
     *
     * @return true если удалено
     */
    public boolean isDeleted() {
        return changeType == PlacementChangeType.DELETED;
    }

    /**
     * Проверить, было ли размещение создано.
     *
     * @return true если создано
     */
    public boolean isCreated() {
        return changeType == PlacementChangeType.CREATED;
    }

    /**
     * Проверить, было ли размещение обновлено.
     *
     * @return true если обновлено
     */
    public boolean isUpdated() {
        return changeType == PlacementChangeType.UPDATED;
    }
}
