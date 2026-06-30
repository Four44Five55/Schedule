package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.*;
import ru.entity.write.LessonPlacement;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для пересоздания workspace из сохранённых placements.
 *
 * <p>Позволяет восстановить ScheduleWorkspace из LessonPlacement,
 * что необходимо для поиска вариантов переноса занятий.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceRecreationService {

    private final EducatorService educatorService;
    private final GroupService groupService;
    private final AuditoriumService auditoriumService;
    private final ConstraintService constraintService;
    private final AssignmentService assignmentService;
    private final ru.repository.write.LessonPlacementRepository placementRepo;
    private final WorkspacePlacementSeeder placementSeeder;

    /**
     * Пересоздать workspace из placements сессии.
     *
     * @param sessionId ID сессии расписания
     * @return Пересозданный workspace
     */
    /**
     * Пересоздать workspace по конкретному размещению.
     *
     * <p>Сессию берём из самого {@link LessonPlacement}, а не извне: отображаемое
     * расписание ({@code schedule_view}) загружается без привязки к сессии, поэтому
     * sessionId с фронта может указывать на другую сессию. Единственный надёжный
     * якорь — сам placementId и его собственная сессия.</p>
     *
     * @param placementId ID размещения, для которого нужен workspace
     * @return Пересозданный workspace (с картой placementId → Lesson), либо пустой,
     *         если размещение не найдено
     */
    @Transactional(readOnly = true)
    public RecreatedWorkspace recreateWorkspaceForPlacement(UUID placementId) {
        LessonPlacement placement = placementRepo.findById(placementId).orElse(null);
        if (placement == null) {
            log.warn("⚠️  Размещение placementId={} не найдено", placementId);
            return new RecreatedWorkspace(createEmptyWorkspace(), Map.of());
        }
        return recreateWorkspaceFromSession(placement.getSession().getId());
    }

    @Transactional(readOnly = true)
    public RecreatedWorkspace recreateWorkspaceFromSession(UUID sessionId) {
        log.info("🔄 Пересоздание workspace для session: {}", sessionId);

        List<LessonPlacement> placements = placementRepo.findBySessionId(sessionId);

        if (placements.isEmpty()) {
            log.warn("⚠️  Сессия не содержит placements");
            return new RecreatedWorkspace(createEmptyWorkspace(), Map.of());
        }

        log.info("Загружено {} placements", placements.size());

        // 1. Определяем период из placements
        DateRange period = determinePeriod(placements);

        // 2. Создаём пустой workspace
        CellForLessonFactory.initializeCellCache(period.startDate, period.endDate);

        ru.services.solver.ScheduleWorkspace workspace = new ru.services.solver.ScheduleWorkspace(
            period.startDate,
            period.endDate,
            educatorService.getAllEntities(),
            groupService.getAllEntities(),
            auditoriumService.getAllEntities(),
            constraintService.loadAllConstraints()
        );

        // 3. Восстанавливаем и размещаем занятия.
        //    Связку placementId → созданный Lesson сохраняем здесь — на границе
        //    персистентности, где UUID размещения и in-memory Lesson встречаются.
        //    Сам Lesson о своём placementId ничего не знает.
        Map<UUID, Lesson> lessonByPlacementId = new HashMap<>();
        int placedCount = 0;
        for (LessonPlacement placement : placements) {
            try {
                Lesson lesson = placementSeeder.seedInto(workspace, placement);
                if (lesson != null) {
                    lessonByPlacementId.put(placement.getId(), lesson);
                    placedCount++;
                }
            } catch (Exception e) {
                log.error("❌ Ошибка размещения placementId={}: {}",
                    placement.getId(), e.getMessage());
            }
        }

        log.info("✅ Workspace пересоздан: {} занятий из {} размещены",
                placedCount, placements.size());

        return new RecreatedWorkspace(workspace, lessonByPlacementId);
    }

    /**
     * Пересоздать workspace на ЯВНЫХ рамках периода, засеяв размещения сессии.
     *
     * <p>В отличие от {@link #recreateWorkspaceFromSession} период берётся не из дат
     * размещений, а задаётся снаружи (из {@link ru.entity.StudyPeriod}). Это нужно для
     * ручной раскладки (Фаза B): в пустой сессии размещений ещё нет, а валидировать
     * целевую ячейку надо в рамках всего учебного периода (иначе кэш ячеек пуст и
     * любой слот считается «вне периода»).</p>
     *
     * @param sessionId сессия (источник уже размещённых занятий-замков)
     * @param start     начало периода планирования
     * @param end       конец периода планирования
     * @return workspace с засеянными размещениями сессии (+ карта placementId → Lesson)
     */
    @Transactional(readOnly = true)
    public RecreatedWorkspace recreateWorkspaceForPeriod(UUID sessionId, LocalDate start, LocalDate end) {
        CellForLessonFactory.initializeCellCache(start, end);

        ru.services.solver.ScheduleWorkspace workspace = new ru.services.solver.ScheduleWorkspace(
            start, end,
            educatorService.getAllEntities(),
            groupService.getAllEntities(),
            auditoriumService.getAllEntities(),
            constraintService.loadAllConstraints()
        );

        Map<UUID, Lesson> lessonByPlacementId = new HashMap<>();
        for (LessonPlacement placement : placementRepo.findBySessionId(sessionId)) {
            try {
                Lesson lesson = placementSeeder.seedInto(workspace, placement);
                if (lesson != null) {
                    lessonByPlacementId.put(placement.getId(), lesson);
                }
            } catch (Exception e) {
                log.error("❌ Ошибка посева placementId={}: {}", placement.getId(), e.getMessage());
            }
        }

        return new RecreatedWorkspace(workspace, lessonByPlacementId);
    }

    /**
     * Определить период (начало и конец) из placements.
     */
    private DateRange determinePeriod(List<LessonPlacement> placements) {
        LocalDate minDate = null;
        LocalDate maxDate = null;

        for (LessonPlacement placement : placements) {
            LocalDate date = placement.getScheduledDate();
            if (minDate == null || date.isBefore(minDate)) {
                minDate = date;
            }
            if (maxDate == null || date.isAfter(maxDate)) {
                maxDate = date;
            }
        }

        if (minDate == null || maxDate == null) {
            // Если нет placements, используем период по умолчанию
            minDate = LocalDate.now();
            maxDate = minDate.plusMonths(1);
        }

        return new DateRange(minDate, maxDate);
    }

    /**
     * Создать пустой workspace (если нет placements).
     */
    private ru.services.solver.ScheduleWorkspace createEmptyWorkspace() {
        LocalDate now = LocalDate.now();
        CellForLessonFactory.initializeCellCache(now, now.plusMonths(1));

        return new ru.services.solver.ScheduleWorkspace(
            now,
            now.plusMonths(1),
            educatorService.getAllEntities(),
            groupService.getAllEntities(),
            auditoriumService.getAllEntities(),
            constraintService.loadAllConstraints()
        );
    }

    /**
     * Вспомогательный класс для диапазона дат.
     */
    private record DateRange(LocalDate startDate, LocalDate endDate) {}

    /**
     * Результат пересоздания workspace.
     *
     * <p>Помимо самого workspace несёт карту {@code placementId → Lesson} —
     * единственное место, где персистентный UUID размещения связан с in-memory
     * занятием. Позволяет надёжно (и уникально) находить занятие для операций
     * вроде поиска вариантов переноса, не «протекая» placementId в доменный Lesson.</p>
     *
     * @param workspace           пересозданное рабочее пространство решателя
     * @param lessonByPlacementId соответствие UUID размещения → размещённый Lesson
     */
    public record RecreatedWorkspace(
            ru.services.solver.ScheduleWorkspace workspace,
            Map<UUID, Lesson> lessonByPlacementId
    ) {}
}
