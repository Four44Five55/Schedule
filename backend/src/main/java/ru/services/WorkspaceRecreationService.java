package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.*;
import ru.entity.write.LessonPlacement;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;

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
    private final ru.repository.write.ScheduleSessionRepository sessionRepo;
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
        List<LessonPlacement> placements = placementRepo.findBySessionId(sessionId);

        // 1. Границы планирования — у СЕССИИ, а не у размещений (см. periodOf).
        //    Пустая сессия — это НЕ повод для «периода по умолчанию»: ручная раскладка начинается
        //    ровно с такой сессии, и палитра обязана предлагать ячейки её периода, а не месяц от
        //    сегодняшнего дня. Запасной путь остаётся только для сессии без периода и без размещений.
        DateRange period = periodOf(sessionId, placements);
        if (placements.isEmpty()) {
            log.debug("Сессия {} пуста — workspace строится на её период {}…{}",
                    sessionId, period.startDate, period.endDate);
        }

        // 2. Создаём пустой workspace на границы периода — календарь ячеек он строит от них сам
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
        int seedFailures = 0;
        for (LessonPlacement placement : placements) {
            try {
                Lesson lesson = placementSeeder.seedInto(workspace, placement);
                if (lesson != null) {
                    lessonByPlacementId.put(placement.getId(), lesson);
                }
            } catch (Exception e) {
                // Битая ссылка в данных: продолжаем, но снимок объявляем неполным — см.
                // RecreatedWorkspace#complete. Раньше пропуск оставался только в логе, и
                // отличить полный снимок от дырявого вызывающему было нечем.
                seedFailures++;
                log.error("❌ Ошибка размещения placementId={}: {}",
                    placement.getId(), e.getMessage());
            }
        }

        return new RecreatedWorkspace(workspace, lessonByPlacementId, seedFailures);
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
        ru.services.solver.ScheduleWorkspace workspace = new ru.services.solver.ScheduleWorkspace(
            start, end,
            educatorService.getAllEntities(),
            groupService.getAllEntities(),
            auditoriumService.getAllEntities(),
            constraintService.loadAllConstraints()
        );

        Map<UUID, Lesson> lessonByPlacementId = new HashMap<>();
        int seedFailures = 0;
        for (LessonPlacement placement : placementRepo.findBySessionId(sessionId)) {
            try {
                Lesson lesson = placementSeeder.seedInto(workspace, placement);
                if (lesson != null) {
                    lessonByPlacementId.put(placement.getId(), lesson);
                }
            } catch (Exception e) {
                seedFailures++;
                log.error("❌ Ошибка посева placementId={}: {}", placement.getId(), e.getMessage());
            }
        }

        return new RecreatedWorkspace(workspace, lessonByPlacementId, seedFailures);
    }

    /**
     * Границы планирования сессии: сначала её собственный период, и только потом — размещения.
     *
     * <p><b>Почему не min/max размещений</b> (так было до 2026-08-27). Границы workspace — это ответ
     * на вопрос «слот принадлежит планируемому периоду», на нём стоит отказ переноса. Считая их по
     * уже размещённым занятиям, мы отвечали «периодом» там, где на самом деле «диапазон того, что
     * уже расставлено»: перенос в пустую последнюю неделю семестра отклонялся как «вне периода»,
     * хотя неделя в периоде есть. Ручная установка тем временем строила workspace по настоящим
     * границам ({@code recreateWorkspaceForPeriod}) — два пути отвечали по-разному об одном.</p>
     *
     * <p>Размещения остаются запасным путём: у сессии может не быть периода (старые записи), и
     * тогда лучше узкие границы, чем никакие.</p>
     */
    private DateRange periodOf(UUID sessionId, List<LessonPlacement> placements) {
        DateRange fromSession = sessionRepo.findById(sessionId)
                .map(ru.entity.write.ScheduleSession::getStudyPeriod)
                .filter(java.util.Objects::nonNull)
                .map(period -> new DateRange(period.getStartDate(), period.getEndDate()))
                .orElse(null);
        if (fromSession != null && fromSession.startDate != null && fromSession.endDate != null) {
            return fromSession;
        }
        log.debug("У сессии {} нет периода — границы берём по размещениям", sessionId);
        return determinePeriod(placements); // пусто → месяц от сегодня, как было
    }

    /**
     * Определить период (начало и конец) из placements — запасной путь, см. {@link #periodOf}.
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
     * Сессия, которой принадлежит размещение; {@code null} — размещения уже нет.
     *
     * <p>Отдельный дешёвый запрос: кэш workspace ключуется сессией, а подбор вариантов приходит с
     * {@code placementId}. Ходить за целым workspace, чтобы узнать его ключ, было бы кругом.</p>
     */
    @Transactional(readOnly = true)
    public UUID sessionIdOfPlacement(UUID placementId) {
        return placementRepo.findById(placementId)
                .map(placement -> placement.getSession().getId())
                .orElse(null);
    }

    /**
     * Создать пустой workspace (если нет placements).
     */
    private ru.services.solver.ScheduleWorkspace createEmptyWorkspace() {
        LocalDate now = LocalDate.now();
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
     * <p>Третья составляющая — <b>число не восстановленных размещений</b>. Посев переживает битую
     * строку (иначе одна испорченная ссылка роняла бы весь экран расписания сессии), но факт
     * пропуска обязан доехать до вызывающего: снимок без занятия отвечает «свободно» там, где
     * занято. Числом, а не флагом, — по тем же соображениям, что {@code AuditoriumResource
     * .shortfall}: число можно и показать, и сравнить, а флаг умеет только запрещать.</p>
     *
     * @param workspace           пересозданное рабочее пространство решателя
     * @param lessonByPlacementId соответствие UUID размещения → размещённый Lesson
     * @param seedFailures        сколько размещений сессии не удалось восстановить (0 — снимок полон)
     */
    public record RecreatedWorkspace(
            ru.services.solver.ScheduleWorkspace workspace,
            Map<UUID, Lesson> lessonByPlacementId,
            int seedFailures
    ) {
        /** Снимок, у которого посев прошёл целиком (пустой workspace — частный случай). */
        public RecreatedWorkspace(ru.services.solver.ScheduleWorkspace workspace,
                                  Map<UUID, Lesson> lessonByPlacementId) {
            this(workspace, lessonByPlacementId, 0);
        }

        /**
         * Полон ли снимок. Неполный отвечает «свободно» там, где занято, — по нему можно
         * ответить на текущий вопрос (лучше, чем уронить весь экран из-за одной битой строки),
         * но <b>кэшировать его нельзя</b>: ошибка одного запроса превратилась бы в ошибку всех
         * запросов следующей минуты.
         */
        public boolean complete() {
            return seedFailures == 0;
        }
    }
}
