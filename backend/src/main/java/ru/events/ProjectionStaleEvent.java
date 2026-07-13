package ru.events;

import lombok.Getter;
import ru.services.projection.ProjectionSource;

import java.util.List;
import java.util.UUID;

/**
 * Снимок в read-модели устарел: изменились master-данные, которые {@code schedule_view}
 * денормализует (имя преподавателя, состав потока, название группы/аудитории, тема, вид занятия).
 *
 * <p>Парное по смыслу к {@link PlacementChangedEvent}, но по другой оси: там меняется само
 * размещение (перенос, замок), здесь — сущность, <b>снимок которой размещение несёт</b>.
 * Само расписание не трогается: даты, слоты, аудитории и замки берутся из тех же
 * {@link ru.entity.write.LessonPlacement}.</p>
 *
 * <p><b>Почему событие несёт УЖЕ РАЗРЕШЁННЫЕ id размещений, а не id сущности:</b> слушатель
 * работает после коммита, а часть источников устаревания — это удаление (группу убрали из потока,
 * аудиторию снесли). К моменту обработки связь «сущность → размещение» уже не существует, и найти
 * затронутые размещения было бы нечем. Поэтому их разрешает {@link ru.services.projection.ProjectionMaintenance}
 * в момент объявления — до того, как связь пропадёт.</p>
 *
 * @see ru.services.ScheduleSynchronizer
 * @see ProjectionSource
 */
@Getter
public class ProjectionStaleEvent {

    /** Что изменилось — только для логов и диагностики; на обработку не влияет. */
    private final ProjectionSource source;

    /** Размещения, снимок которых надо переписать. */
    private final List<UUID> placementIds;

    public ProjectionStaleEvent(ProjectionSource source, List<UUID> placementIds) {
        this.source = source;
        this.placementIds = List.copyOf(placementIds);
    }
}
