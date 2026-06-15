package ru.events;

import lombok.Getter;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;

import java.util.List;
import java.util.UUID;

/**
 * Событие: Расписание сгенерировано.
 *
 * <p>Публикуется после генерации расписания для синхронизации Query Side.</p>
 * <p>Запускает асинхронное обновление {@link ru.entity.read.ScheduleView}.</p>
 *
 * <p><b>Фазы генерации:</b></p>
 * <ol>
 *   <li>Сгенерированы лекции</li>
 *   <li>Созданы {@link LessonPlacement} для всех занятий</li>
 *   <li>Сохранены в {@link ScheduleSession}</li>
 *   <li>Публикуется это событие</li>
 *   <li>Query Side синхронизируется</li>
 * </ol>
 *
 * @see ru.entity.read.ScheduleView
 * @see ru.entity.write.LessonPlacement
 * @see ru.services.ScheduleSynchronizer
 */
@Getter
public class ScheduleGeneratedEvent {

    /**
     * ID сессии расписания.
     */
    private final UUID sessionId;

    /**
     * Список размещений занятий.
     */
    private final List<LessonPlacement> placements;

    /**
     * Создаёт событие о генерации расписания.
     *
     * @param sessionId ID сессии
     * @param placements Список размещений
     */
    public ScheduleGeneratedEvent(UUID sessionId, List<LessonPlacement> placements) {
        this.sessionId = sessionId;
        this.placements = placements;
    }

    /**
     * Создаёт пустое событие (если генерация не удалась).
     *
     * @param sessionId ID сессии
     */
    public ScheduleGeneratedEvent(UUID sessionId) {
        this(sessionId, List.of());
    }

    /**
     * Проверить, есть ли размещения.
     *
     * @return true если есть размещения
     */
    public boolean hasPlacements() {
        return placements != null && !placements.isEmpty();
    }

    /**
     * Получить количество размещений.
     *
     * @return Количество размещений
     */
    public int getPlacementsCount() {
        return placements != null ? placements.size() : 0;
    }
}
