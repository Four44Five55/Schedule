package ru.services.projection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.events.ProjectionStaleEvent;
import ru.repository.write.LessonPlacementRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Единственная дверь, через которую доменные сервисы сообщают: «мои данные изменились, снимок в
 * read-модели устарел».
 *
 * <p><b>Зачем фасад.</b> {@code schedule_view} денормализует имена преподавателя, группы,
 * аудитории, тему и вид занятия. Пока про это знал только {@code AssignmentService}, все остальные
 * мутаторы master-данных тихо расходились с проекцией: переименование преподавателя не доезжало до
 * сетки и отчётов, а группа, убранная из потока, навсегда оставалась в расписании отдельной
 * строкой — занятие-призрак, которое нельзя снять, не снеся занятие у остальных групп потока.</p>
 *
 * <p>Мутатор говорит <i>что</i> изменилось ({@link ProjectionSource} + id), а не <i>как</i> это
 * отражается на расписании: поиск затронутых размещений живёт в стратегии, перепроекция — в
 * {@link ru.services.ScheduleSynchronizer} (единственный писатель read-модели). Доменные сервисы
 * не видят ни {@code ScheduleViewRepository}, ни событий (DIP).</p>
 *
 * <p><b>⚠️ Порядок вызова при удалении:</b> объявлять <b>ДО</b> удаления сущности. Событие
 * обрабатывается после коммита, и к этому моменту связь «сущность → размещение» уже исчезнет —
 * поэтому размещения разрешаются здесь, немедленно, пока связь ещё на месте.</p>
 *
 * <p>Парный инвариант — «строка проекции не переживает своё размещение» — обеспечивает не код,
 * а схема: FK {@code schedule_view → lesson_placement ON DELETE CASCADE} (миграция 017).
 * Здесь речь о другом классе: размещение живо, а снимок в нём врёт.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionMaintenance {

    private final LessonPlacementRepository placementRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Объявить об изменении одной сущности. */
    public void announce(ProjectionSource source, Integer entityId) {
        if (entityId == null) {
            return;
        }
        announce(source, List.of(entityId));
    }

    /**
     * Объявить об изменении набора сущностей одного вида.
     *
     * <p>Ничего не публикует, если затронутых размещений нет: перепроецировать нечего
     * (сущность в расписании не участвует).</p>
     *
     * @param source вид изменившейся сущности
     * @param entityIds её id
     */
    public void announce(ProjectionSource source, Collection<Integer> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return;
        }
        List<UUID> affected = source.affectedPlacements(placementRepository, entityIds);
        if (affected.isEmpty()) {
            return;
        }
        log.debug("Снимок устарел: {} {} → перепроецируем {} размещений", source, entityIds, affected.size());
        eventPublisher.publishEvent(new ProjectionStaleEvent(source, affected));
    }
}
