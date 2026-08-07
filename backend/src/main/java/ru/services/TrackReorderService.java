package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Assignment;
import ru.entity.Auditorium;
import ru.entity.Group;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.SlotChain;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.events.PlacementChangedEvent;
import ru.repository.SlotChainRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.session.ScheduleSessionGate;
import ru.services.reindex.Cell;
import ru.services.reindex.CellMove;
import ru.services.reindex.ReorderPlan;
import ru.services.reindex.ReorderProblem;
import ru.services.reindex.ReorderRoomContext;
import ru.services.reindex.ReorderRoomInput;
import ru.services.reindex.ReorderRoomPlan;
import ru.services.reindex.ReorderRoomResolver;
import ru.services.reindex.RoomSlot;
import ru.services.reindex.TimedLesson;
import ru.services.reindex.TrackReorderStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Пересортировка трека в порядок плана (Command Side). Вызывается ПОСЛЕ переноса: занятия
 * класса возвращаются в порядок изучения — перенесённое «пузырьком» встаёт на своё
 * плановое место, соседи сдвигаются на одну ячейку. Меняются только <b>даты/пары</b>
 * размещений; содержание ({@code assignment}) и аудитории не трогаются — тема едет с
 * занятием.
 *
 * <p><b>Класс однородности.</b> Сортируются только занятия с тем же <i>курсом + потоком +
 * набором преподавателей</i>, что у якорного (перенесённого). Лекции/потоковые занятия
 * (другой {@code StudyStream}) и разделы других преподавателей не затрагиваются.</p>
 *
 * <p><b>Ресурсы.</b> Это перестановка занятий класса по их же ячейкам, участники не
 * меняются, поэтому новых конфликтов групп/преподавателей не возникает — валидация
 * доступности не нужна. Расчёт — в памяти ({@link TrackReorderStrategy}).</p>
 *
 * <p><b>Сцепки (P2):</b> при разъезде на несоседние ячейки размещения всё равно
 * раскладываются, а пара помечается {@link ReorderProblem.Reason#CHAIN_BROKEN}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackReorderService {

    private final LessonPlacementRepository placementRepo;
    private final SlotChainRepository slotChainRepo;
    private final ScheduleSessionGate sessionGate;
    private final ApplicationEventPublisher eventPublisher;
    /** Политика комнат при перестановке — через контракт, чтобы фаза 2 подменялась без правки сервиса. */
    private final ReorderRoomResolver roomResolver;

    private final TrackReorderStrategy strategy = new TrackReorderStrategy();

    /** Результат: сессия-владелец (для версии) + флаги для предупреждений. */
    public record ReorderResult(ScheduleSession session, List<ReorderProblem> problems) {}

    /**
     * Пересортировать класс якорного занятия в порядок плана.
     *
     * @param anchorPlacementId размещение перенесённого занятия (определяет класс)
     * @param user              автор изменения (для аудита)
     * @return сессия-владелец + список «проблемных» размещений (распавшиеся сцепки)
     */
    @Transactional
    public ReorderResult resort(UUID anchorPlacementId, String user) {
        LessonPlacement anchor = placementRepo.findById(anchorPlacementId)
                .orElseThrow(() -> new IllegalArgumentException("Размещение не найдено: " + anchorPlacementId));
        ScheduleSession session = anchor.getSession();

        Assignment anchorAssignment = anchor.getAssignment();
        Integer courseId = anchorAssignment.getCurriculumSlot().getDisciplineCourse().getId();
        Integer streamId = anchorAssignment.getStudyStream().getId();
        Set<Integer> educatorIds = educatorIdsOf(anchorAssignment);

        // Все размещения сессии грузим один раз: из них и класс однородности, и снимок
        // занятости не-классных комнат для проверки жёстких требований.
        List<LessonPlacement> allSessionPlacements = placementRepo.findBySessionId(session.getId());

        // Класс однородности: та же сессия + курс + поток + набор преподавателей.
        List<LessonPlacement> classPlacements = allSessionPlacements.stream()
                .filter(p -> {
                    Assignment a = p.getAssignment();
                    return courseId.equals(a.getCurriculumSlot().getDisciplineCourse().getId())
                            && streamId.equals(a.getStudyStream().getId())
                            && educatorIds.equals(educatorIdsOf(a));
                })
                .toList();

        if (classPlacements.size() < 2) {
            return new ReorderResult(session, List.of());
        }

        List<TimedLesson> lessons = classPlacements.stream()
                .map(p -> {
                    CurriculumSlot slot = p.getAssignment().getCurriculumSlot();
                    return new TimedLesson(
                            p.getId(),
                            p.getScheduledDate(),
                            p.getScheduledSlot(),
                            slot.getId(),
                            slot.getPosition());
                })
                .toList();

        Set<Integer> servedSlotIds = lessons.stream().map(TimedLesson::slotId).collect(Collectors.toSet());
        List<int[]> chainPairs = chainPairsWithin(courseId, servedSlotIds);

        ReorderPlan plan = strategy.resort(lessons, chainPairs);

        Map<UUID, LessonPlacement> byId = classPlacements.stream()
                .collect(Collectors.toMap(LessonPlacement::getId, p -> p));

        // Куда каждое занятие класса встаёт после перестановки (не переехавшие — на месте).
        Map<UUID, Cell> targetByPlacement = new HashMap<>();
        for (LessonPlacement p : classPlacements) {
            targetByPlacement.put(p.getId(), new Cell(p.getScheduledDate(), p.getScheduledSlot()));
        }
        for (CellMove move : plan.moves()) {
            targetByPlacement.put(move.placementId(), new Cell(move.date(), move.slot()));
        }

        // Подбор комнат: взаимозаменяемые остаются с ячейкой, жёсткие едут с занятием и
        // проверяются на занятость. Всё в памяти — workspace не строим.
        ReorderRoomPlan roomPlan = resolveRooms(classPlacements, allSessionPlacements, targetByPlacement, byId);
        Map<Integer, Auditorium> roomEntities = collectRoomEntities(classPlacements);

        // Применяем переезды: дата/пара + подобранные комнаты
        List<LessonPlacement> changed = new ArrayList<>();
        for (CellMove move : plan.moves()) {
            LessonPlacement p = byId.get(move.placementId());
            Set<Auditorium> rooms = toEntities(roomPlan.roomsByPlacement().get(move.placementId()), roomEntities, p);
            p.updatePlacement(move.date(), move.slot(), rooms, user);
            changed.add(p);
        }

        List<ReorderProblem> problems = new ArrayList<>(plan.problems());
        problems.addAll(roomPlan.problems());

        if (!changed.isEmpty()) {
            // Дверь берём ТОЛЬКО когда что-то реально переехало: холостая пересортировка (а фронт
            // зовёт её после каждого переноса) не должна поднимать поколение — иначе она зря
            // сбрасывала бы кэш workspace и обесценивала версию у соседних вкладок.
            session = sessionGate.forWriteOf(anchor, null);

            placementRepo.saveAll(changed);
            // Проекция в read-модель (schedule_view) — асинхронно через onPlacementChanged.
            for (LessonPlacement p : changed) {
                eventPublisher.publishEvent(new PlacementChangedEvent(session.getId(), p.getId(), p));
            }
        }

        log.info("↕ Пересортировка в план: класс={} занятий, переехало={}, флагов сцепок={}, флагов аудиторий={}",
                classPlacements.size(), changed.size(), plan.problems().size(), roomPlan.problems().size());

        return new ReorderResult(session, problems);
    }

    private static Set<Integer> educatorIdsOf(Assignment a) {
        return a.getEducators().stream().map(e -> e.getId()).collect(Collectors.toSet());
    }

    /** Собрать pure-контекст подбора комнат из сущностей и прогнать политику. */
    private ReorderRoomPlan resolveRooms(List<LessonPlacement> classPlacements,
                                         List<LessonPlacement> allSessionPlacements,
                                         Map<UUID, Cell> targetByPlacement,
                                         Map<UUID, LessonPlacement> classById) {
        List<ReorderRoomInput> inputs = new ArrayList<>();
        for (LessonPlacement p : classPlacements) {
            Set<Integer> currentRoomIds = p.getAssignedAuditoriums().stream()
                    .map(Auditorium::getId)
                    .collect(Collectors.toSet());
            CurriculumSlot slot = p.getAssignment().getCurriculumSlot();
            boolean hard = slot.getRequiredAuditorium() != null || slot.getAllowedAuditoriumPool() != null;
            inputs.add(new ReorderRoomInput(
                    p.getId(),
                    new Cell(p.getScheduledDate(), p.getScheduledSlot()),
                    targetByPlacement.get(p.getId()),
                    currentRoomIds,
                    hard,
                    baseRoomIdOf(p)));
        }

        // Занятость не-классных размещений: они при пересортировке не двигаются, поэтому только с
        // ними и может столкнуться переезжающая жёсткая комната (внутри класса ячейки уникальны).
        Set<RoomSlot> occupiedByOthers = new HashSet<>();
        for (LessonPlacement p : allSessionPlacements) {
            if (classById.containsKey(p.getId())) {
                continue;
            }
            Cell cell = new Cell(p.getScheduledDate(), p.getScheduledSlot());
            for (Auditorium room : p.getAssignedAuditoriums()) {
                occupiedByOthers.add(new RoomSlot(room.getId(), cell));
            }
        }

        return roomResolver.resolve(new ReorderRoomContext(inputs, occupiedByOthers));
    }

    /** Базовая аудитория одной из групп потока — запасной вариант; null, если ни у одной нет. */
    private static Integer baseRoomIdOf(LessonPlacement p) {
        return p.getAssignment().getStudyStream().getGroups().stream()
                .map(Group::getBaseAuditorium)
                .filter(Objects::nonNull)
                .map(Auditorium::getId)
                .findFirst()
                .orElse(null);
    }

    /** Все аудитории, которые может назначить политика: нынешние комнаты класса + базовые. */
    private static Map<Integer, Auditorium> collectRoomEntities(List<LessonPlacement> classPlacements) {
        Map<Integer, Auditorium> byId = new HashMap<>();
        for (LessonPlacement p : classPlacements) {
            for (Auditorium room : p.getAssignedAuditoriums()) {
                byId.putIfAbsent(room.getId(), room);
            }
            for (Group g : p.getAssignment().getStudyStream().getGroups()) {
                if (g.getBaseAuditorium() != null) {
                    byId.putIfAbsent(g.getBaseAuditorium().getId(), g.getBaseAuditorium());
                }
            }
        }
        return byId;
    }

    /** id комнат → сущности; при отсутствии решения политики сохраняем текущие комнаты занятия. */
    private static Set<Auditorium> toEntities(Set<Integer> roomIds, Map<Integer, Auditorium> roomEntities,
                                              LessonPlacement fallback) {
        if (roomIds == null) {
            return new HashSet<>(fallback.getAssignedAuditoriums());
        }
        Set<Auditorium> rooms = new HashSet<>();
        for (Integer id : roomIds) {
            Auditorium room = roomEntities.get(id);
            if (room != null) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    /** Пары сцепленных слотов курса, у которых ОБА конца обслуживаются этим классом. */
    private List<int[]> chainPairsWithin(Integer courseId, Set<Integer> servedSlotIds) {
        List<int[]> pairs = new ArrayList<>();
        for (SlotChain chain : slotChainRepo.findByCourseId(courseId)) {
            Integer a = chain.getSlotA().getId();
            Integer b = chain.getSlotB().getId();
            if (servedSlotIds.contains(a) && servedSlotIds.contains(b)) {
                pairs.add(new int[]{a, b});
            }
        }
        return pairs;
    }
}
