package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Assignment;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.SlotChain;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.events.PlacementChangedEvent;
import ru.repository.SlotChainRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.session.ScheduleSessionGate;
import ru.services.reindex.CellMove;
import ru.services.reindex.ReorderPlan;
import ru.services.reindex.ReorderProblem;
import ru.services.reindex.TimedLesson;
import ru.services.reindex.TrackReorderStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        // Класс однородности: та же сессия + курс + поток + набор преподавателей.
        List<LessonPlacement> classPlacements = placementRepo.findBySessionId(session.getId()).stream()
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

        // Применяем переезды — только дата/пара, аудитории занятия сохраняем.
        Map<UUID, LessonPlacement> byId = classPlacements.stream()
                .collect(Collectors.toMap(LessonPlacement::getId, p -> p));
        List<LessonPlacement> changed = new ArrayList<>();
        for (CellMove move : plan.moves()) {
            LessonPlacement p = byId.get(move.placementId());
            p.updatePlacement(move.date(), move.slot(), new HashSet<>(p.getAssignedAuditoriums()), user);
            changed.add(p);
        }

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

        log.info("↕ Пересортировка в план: класс={} занятий, переехало={}, флагов={}",
                classPlacements.size(), changed.size(), plan.problems().size());

        return new ReorderResult(session, plan.problems());
    }

    private static Set<Integer> educatorIdsOf(Assignment a) {
        return a.getEducators().stream().map(e -> e.getId()).collect(Collectors.toSet());
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
