package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.events.PlacementChangedEvent;
import ru.repository.write.LessonPlacementRepository;
import ru.services.session.ScheduleSessionGate;
import ru.exceptions.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Закрепление/открепление размещений (пины, Фича 2 — Фаза A).
 *
 * <p>Пин — это флаг {@code locked} на {@link LessonPlacement}: распределитель не двигает
 * и не удаляет закреплённое при регенерации (засев Фазы 0). Источник ({@code source})
 * при закреплении НЕ меняется — он отражает, кто поставил занятие (алгоритм/диспетчер),
 * а {@code locked} — намерение «не трогать».</p>
 *
 * <p><b>Гранулярность — цепочка.</b> Сцепка занятий неразрывна, поэтому закрепление
 * любого её звена закрепляет всю цепочку (иначе регенерация разорвёт её: засеяно одно
 * звено, хвост разложится отдельным блоком). Цепочка определяется через
 * {@link SlotChainService#getFullChain} по слотам одного потока в пределах сессии.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonPinService {

    private final LessonPlacementRepository placementRepo;
    private final SlotChainService slotChainService;
    private final ScheduleSessionGate sessionGate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Закрепить/открепить размещение — с опциональным сужением до подмножества цепочки
     * (эфемерный разрыв сцепки на фронте, см. {@link ru.dto.command.LockPlacementRequest}).
     *
     * <p>Бэк остаётся источником истины о том, что вообще является цепочкой: сначала считает
     * полную цепочку якоря через {@link SlotChainService#getFullChain} как обычно, а затем,
     * если передан {@code explicitPlacementIds}, оставляет только пересечение — т.е. клиент
     * может лишь <b>сузить</b> множество, но не «изобрести» связь, которой нет в БД.</p>
     *
     * @param placementId          якорное размещение
     * @param locked                целевое состояние закрепления
     * @param user                  автор изменения (для аудита)
     * @param explicitPlacementIds {@code null}/пусто — закрепить всю цепочку (старое поведение);
     *                              иначе — только те размещения цепочки, чьи id входят в список
     * @return сессия-владелец (с актуальными данными для ответа)
     */
    @Transactional
    public ScheduleSession setLock(UUID placementId, boolean locked, String user,
                                    List<UUID> explicitPlacementIds, Long expectedVersion) {
        LessonPlacement anchor = placementRepo.findById(placementId)
                .orElseThrow(() -> new NotFoundException("Размещение не найдено: " + placementId));
        ScheduleSession session = sessionGate.forWriteOf(anchor, expectedVersion);

        List<LessonPlacement> fullChain = chainPlacements(anchor, session.getId());
        List<LessonPlacement> chain = (explicitPlacementIds == null || explicitPlacementIds.isEmpty())
                ? fullChain
                : fullChain.stream()
                    .filter(p -> explicitPlacementIds.contains(p.getId()))
                    .toList();

        for (LessonPlacement p : chain) {
            p.setLock(locked, user);
        }
        placementRepo.saveAll(chain);

        // Проекция в read-модель: applyPinMetadata прогонится в onPlacementChanged.
        for (LessonPlacement p : chain) {
            eventPublisher.publishEvent(new PlacementChangedEvent(session.getId(), p.getId(), p));
        }

        log.info("🔒 Закрепление={} для {}/{} размещений цепочки (якорь={})",
                locked, chain.size(), fullChain.size(), placementId);

        return session;
    }

    /**
     * Все размещения цепочки якоря в пределах сессии: тот же поток + слоты одной сцепки.
     * Для незацепленного занятия {@code getFullChain} вернёт один слот → ровно якорь.
     */
    private List<LessonPlacement> chainPlacements(LessonPlacement anchor, UUID sessionId) {
        Integer anchorSlotId = anchor.getAssignment().getCurriculumSlot().getId();
        Integer streamId = anchor.getAssignment().getStudyStream().getId();
        List<Integer> chainSlotIds = slotChainService.getFullChain(anchorSlotId);

        return placementRepo.findBySessionId(sessionId).stream()
                .filter(p -> chainSlotIds.contains(p.getAssignment().getCurriculumSlot().getId()))
                .filter(p -> streamId.equals(p.getAssignment().getStudyStream().getId()))
                .toList();
    }
}
