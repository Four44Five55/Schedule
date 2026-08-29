package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.moveLesson.MoveOptionDto;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.TimeSlotPair;
import ru.events.PlacementChangedEvent;
import ru.exceptions.LessonMoveConflictException;
import ru.exceptions.NotFoundException;
import ru.repository.write.LessonPlacementRepository;
import ru.services.session.ScheduleSessionGate;
import ru.services.workspace.WorkspaceProvider;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Перенос «цепочки» занятий как единого целого (CQRS Command Side).
 *
 * <p>Цепочка — это занятия, идущие подряд в один день без разрыва (см. {@code SlotChain}).
 * Какие именно звенья и в каком порядке двигать (с учётом временного размыкания на фронте)
 * решает клиент: он присылает упорядоченный по времени список {@code placementIds}. Бэк
 * выполняет общий примитив: «поставить эти N размещений в N подряд идущих пар, начиная с
 * заданной, каждое — с повторной валидацией ресурсов».</p>
 *
 * <p><b>Почему звенья валидируются независимо:</b> они занимают <i>разные</i> пары одного дня,
 * то есть по времени не пересекаются и не могут конфликтовать друг с другом за ресурсы.
 * Поэтому каждое звено проверяется через {@link ScheduleWorkspace#findPlacementOption} на
 * воркспейсе, из которого изъята вся цепочка. Одиночный перенос — частный случай (N = 1).</p>
 *
 * @see LessonMoveService одиночный перенос с теми же гарантиями
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonChainMoveService {

    private final WorkspaceRecreationService workspaceRecreationService;
    /** Читающие пути берут снимок здесь: провайдер может отдать его из кэша. */
    private final WorkspaceProvider workspaceProvider;
    private final LessonPlacementRepository placementRepo;
    private final ScheduleSessionGate sessionGate;
    private final TrackReorderService trackReorderService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Найти стартовые ячейки, куда вся цепочка помещается подряд.
     *
     * @param placementIds размещения цепочки в порядке следования по времени
     * @return список стартовых ячеек (дата + пара первого звена)
     */
    @Transactional(readOnly = true)
    public List<MoveOptionDto> findChainMoveOptions(List<UUID> placementIds) {
        if (placementIds == null || placementIds.isEmpty()) return List.of();

        // Снимок одалживается у провайдера (возможно, из кэша) на время подбора.
        return workspaceProvider.withWorkspaceOfPlacement(placementIds.get(0),
                recreated -> chainOptionsOn(recreated, placementIds));
    }

    private List<MoveOptionDto> chainOptionsOn(WorkspaceRecreationService.RecreatedWorkspace recreated,
                                               List<UUID> placementIds) {
        ScheduleWorkspace workspace = recreated.workspace();

        List<Lesson> chain = resolveChainLessons(placementIds, recreated.lessonByPlacementId());
        if (chain == null) {
            // Пустой список ячеек — это УТВЕРЖДЕНИЕ «переносить некуда», по которому человек
            // принимает решение, поэтому отвечать им можно только на настоящее «некуда».
            // Здесь два разных случая, и раньше оба давали пустоту:
            //   • карта пуста — размещения уже нет (сняли, пока клиент спрашивал). Законный
            //     пустой ответ: цепочки действительно больше не существует;
            //   • карта не пуста, а звена в ней нет — восстановление сессии потеряло занятие,
            //     то есть состояние испорчено. Это сбой, и он обязан выглядеть как сбой —
            //     ровно так же, как в moveChain, где та же проверка всегда бросала.
            if (recreated.lessonByPlacementId().isEmpty()) {
                return List.of();
            }
            throw new IllegalStateException(
                    "Не удалось восстановить цепочку для подбора ячеек: " + placementIds);
        }

        // Текущее начало цепочки — чтобы не предлагать перенос «туда же».
        CellForLesson currentStart = workspace.getCellForLesson(chain.get(0));

        // Изымаем всю цепочку: её ресурсы освобождаются, ячейки выглядят свободными. Возврат на
        // место — гарантией withoutPlacements (finally), а не строкой в конце метода: раньше
        // возврата не было ВООБЩЕ, и это безобидно ровно до тех пор, пока workspace выбрасывается
        // вместе с запросом. С кэшем такой подбор стирал бы цепочку из общего снимка.
        return workspace.withoutPlacements(chain, () -> chainOptions(workspace, chain, currentStart));
    }

    /** Перебор стартовых ячеек — выполняется, когда цепочка уже изъята из сетки. */
    private List<MoveOptionDto> chainOptions(ScheduleWorkspace workspace, List<Lesson> chain,
                                             CellForLesson currentStart) {
        int n = chain.size();
        TimeSlotPair[] slots = TimeSlotPair.values();

        List<MoveOptionDto> options = new ArrayList<>();
        for (LocalDate date : workspace.getCalendar().dates()) {
            for (int start = 0; start + n <= slots.length; start++) {
                if (currentStart != null
                        && date.equals(currentStart.getDate())
                        && slots[start] == currentStart.getTimeSlotPair()) {
                    continue; // цепочка уже здесь
                }
                if (chainFits(workspace, chain, date, slots, start)) {
                    options.add(new MoveOptionDto(date, slots[start]));
                }
            }
        }
        return options;
    }

    /**
     * Перенести цепочку в подряд идущие пары, начиная с указанной, с повторной валидацией.
     *
     * @param placementIds  размещения цепочки в порядке следования по времени
     * @param newStartDate  дата переноса (весь день — один)
     * @param newStartSlot  пара первого звена; остальные — следом по времени
     * @param expectedVersion ожидаемая версия сессии (optimistic lock)
     * @param reorder       пересортировать ли трек в порядок плана после переноса;
     *                      {@code false} — режим «перенос без пересортировки»
     * @param user          автор изменения
     * @return сессия-владелец (для ответа)
     * @throws ObjectOptimisticLockingFailureException если версия сессии устарела
     * @throws LessonMoveConflictException             если цепочка не помещается или ресурс занят
     */
    @Transactional
    public LessonMoveService.MoveResult moveChain(
            List<UUID> placementIds,
            LocalDate newStartDate,
            String newStartSlot,
            Long expectedVersion,
            boolean reorder,
            String user
    ) {
        if (placementIds == null || placementIds.isEmpty()) {
            throw new IllegalArgumentException("Пустой список размещений цепочки");
        }

        // Размещения в порядке цепочки; сессию-якорь берём из первого.
        List<LessonPlacement> placements = new ArrayList<>(placementIds.size());
        for (UUID id : placementIds) {
            placements.add(placementRepo.findById(id)
                    .orElseThrow(() -> new NotFoundException("Размещение не найдено: " + id)));
        }
        // Сессия — через единую дверь: сверка версии + подъём поколения на коммите.
        ScheduleSession session = sessionGate.forWriteOf(placements.get(0), expectedVersion);

        var recreated = workspaceRecreationService.recreateWorkspaceFromSession(session.getId());
        ScheduleWorkspace workspace = recreated.workspace();

        List<Lesson> chain = resolveChainLessons(placementIds, recreated.lessonByPlacementId());
        if (chain == null) {
            throw new IllegalStateException("Не удалось восстановить цепочку для переноса");
        }

        int n = chain.size();
        TimeSlotPair[] slots = TimeSlotPair.values();
        int startOrdinal = TimeSlotPair.valueOf(newStartSlot).ordinal();
        if (startOrdinal + n > slots.length) {
            throw new LessonMoveConflictException("цепочка не помещается до конца дня");
        }

        // Изымаем всю цепочку и проверяем каждое звено на новом месте.
        // Звенья идут в разные пары, поэтому валидируются независимо.
        chain.forEach(workspace::removePlacement);

        List<PlacementOption> options = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            TimeSlotPair slot = slots[startOrdinal + k];
            CellForLesson cell = workspace.getCalendar().cellAt(newStartDate, slot)
                    .orElseThrow(() -> new LessonMoveConflictException("слот цепочки вне планируемого периода"));
            // HONOR_WINDOWS: цепочку двигает человек — см. LessonMoveService.
            PlacementOption option = workspace.findPlacementOption(chain.get(k), cell,
                    ScheduleWorkspace.ConstraintPolicy.HONOR_WINDOWS);
            if (!option.isPossible()) {
                throw new LessonMoveConflictException(option.failureReason());
            }
            options.add(option);
        }

        // Всё валидно — фиксируем перенос звеньев.
        for (int k = 0; k < n; k++) {
            TimeSlotPair slot = slots[startOrdinal + k];
            LessonPlacement placement = placements.get(k);
            placement.updatePlacement(newStartDate, slot, new HashSet<>(options.get(k).assignedAuditoriums()), user);
            placementRepo.save(placement);
            eventPublisher.publishEvent(new PlacementChangedEvent(session.getId(), placement.getId(), placement));
        }

        log.info("✅ Цепочка перенесена: {} звеньев, newStartDate={}, newStartSlot={}",
                n, newStartDate, newStartSlot);

        // Пересортировка трека — часть переноса (см. LessonMoveService, шаг 9). Якорь — голова
        // цепочки: класс однородности у всех звеньев один и тот же. Режим «без пересортировки»
        // (reorder=false) её пропускает — звенья уже валидно размещены выше.
        if (!reorder) {
            return new LessonMoveService.MoveResult(session, List.of());
        }
        var reorderResult = trackReorderService.resort(placementIds.get(0), user);
        return new LessonMoveService.MoveResult(session, reorderResult.problems());
    }

    /**
     * Восстанавливает занятия цепочки по placementId в исходном порядке.
     *
     * @return список занятий или {@code null}, если хотя бы одно не восстановилось.
     */
    private List<Lesson> resolveChainLessons(List<UUID> placementIds, java.util.Map<UUID, Lesson> byPlacementId) {
        List<Lesson> chain = new ArrayList<>(placementIds.size());
        for (UUID id : placementIds) {
            Lesson lesson = byPlacementId.get(id);
            if (lesson == null) {
                log.warn("⚠️  Звено цепочки не восстановлено: placementId={}", id);
                return null;
            }
            chain.add(lesson);
        }
        return chain;
    }

    /**
     * Помещается ли цепочка целиком, начиная с пары {@code slots[start]} указанного дня.
     */
    private boolean chainFits(ScheduleWorkspace workspace, List<Lesson> chain,
                              LocalDate date, TimeSlotPair[] slots, int start) {
        for (int k = 0; k < chain.size(); k++) {
            CellForLesson cell = workspace.getCalendar().cellAt(date, slots[start + k]).orElse(null);
            // Подсказка обязана совпадать с фактическим переносом, иначе зелёная ячейка приведёт
            // к 409 — поэтому политика здесь та же, что в moveChain.
            if (cell == null || !workspace.findPlacementOption(chain.get(k), cell,
                    ScheduleWorkspace.ConstraintPolicy.HONOR_WINDOWS).isPossible()) {
                return false;
            }
        }
        return true;
    }
}
