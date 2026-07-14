package ru.controllers.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.command.CreateScheduleSessionRequest;
import ru.dto.command.LessonPlacementDto;
import ru.dto.command.LockPlacementRequest;
import ru.dto.command.MoveChainRequest;
import ru.dto.command.MoveLessonRequest;
import ru.dto.command.MoveLessonResponse;
import ru.dto.command.ReorderProblemDto;
import ru.dto.command.ScheduleSessionDto;
import ru.dto.board.PlacementBoardDto;
import ru.dto.manualPlacement.ManualPlacementRequest;
import ru.dto.manualPlacement.PlacementOptionsRequest;
import ru.dto.manualPlacement.UnplacedLessonDto;
import ru.dto.moveLesson.MoveOptionDto;
import ru.dto.order.OrderViolationDto;
import ru.services.board.BoardAxis;
import ru.services.board.PlacementBoardService;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;
import ru.exceptions.LessonMoveConflictException;
import ru.mapper.command.LessonPlacementMapper;
import ru.mapper.command.ScheduleSessionMapper;
import ru.services.LessonChainMoveService;
import ru.services.LessonMoveService;
import ru.services.LessonOrderService;
import ru.services.LessonPinService;
import ru.services.ManualPlacementService;
import ru.services.ScheduleGenerationService;
import ru.services.ScheduleSynchronizer;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller для CQRS Command Side.
 *
 * <p>Предоставляет API для:</p>
 * <ul>
 *   <li>Создания сессий редактирования</li>
 *   <li>Переноса занятий с optimistic lock</li>
 *   <li>Получения сессий и размещений</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/command")
@RequiredArgsConstructor
public class ScheduleCommandController {

    private final ScheduleGenerationService generationService;
    private final LessonMoveService lessonMoveService;
    private final LessonChainMoveService lessonChainMoveService;
    private final LessonPinService lessonPinService;
    private final ManualPlacementService manualPlacementService;
    private final PlacementBoardService placementBoardService;
    private final LessonOrderService lessonOrderService;
    private final ScheduleSynchronizer scheduleSynchronizer;
    private final ScheduleSessionMapper sessionMapper;
    private final LessonPlacementMapper placementMapper;

    /**
     * Создать новую сессию расписания.
     *
     * POST /api/schedule/command/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<ScheduleSessionDto> createSession(@RequestBody CreateScheduleSessionRequest request) {
        log.info("Создание сессии: name={}", request.name());

        ScheduleSession session = generationService.createScheduleSession(
            request.name(),
            "admin" // TODO: из Security Context
        );

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Генерация расписания с персистентностью.
     *
     * POST /api/schedule/command/sessions/generate
     */
    @PostMapping("/sessions/generate")
    public ResponseEntity<ScheduleSessionDto> generateSchedule(@RequestBody CreateScheduleSessionRequest request) {
        log.info("Генерация расписания: name={}, period={}, courses={}",
                request.name(), request.studyPeriodId(), request.courseIds());

        ScheduleSession session = generationService.generateSchedule(
            request.name(),
            request.studyPeriodId(),
            request.courseIds(),
            "admin"
        );

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Перегенерация расписания с сохранением закреплённых занятий (Фича 2, Фаза A).
     *
     * <p>POST /api/schedule/command/sessions/{sessionId}/regenerate</p>
     *
     * <p>Закреплённые ({@code locked}) занятия сессии остаются на местах, распределитель
     * перераскладывает остальное «вокруг» них.</p>
     */
    @PostMapping("/sessions/{sessionId}/regenerate")
    public ResponseEntity<ScheduleSessionDto> regenerate(
        @PathVariable UUID sessionId,
        @RequestBody CreateScheduleSessionRequest request
    ) {
        log.info("Перегенерация (сохранив замки): sessionId={}, period={}, courses={}",
                sessionId, request.studyPeriodId(), request.courseIds());

        ScheduleSession session = generationService.regenerateKeepingLocked(
            sessionId,
            request.studyPeriodId(),
            request.courseIds(),
            "admin"
        );

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * АДДИТИВНАЯ генерация одного курса (дисциплины) — инкрементальная сборка.
     *
     * <p>POST /api/schedule/command/sessions/{sessionId}/generate-course</p>
     *
     * <p>Все существующие размещения сессии остаются неподвижными, раскладываются только
     * неразмещённые занятия курса «вокруг» них. Ничего уже стоящего не удаляется.</p>
     *
     * <p>Охват сужается опционально: по видам занятий и/или по преподавателям курса.</p>
     */
    @PostMapping("/sessions/{sessionId}/generate-course")
    public ResponseEntity<ScheduleSessionDto> generateCourse(
        @PathVariable UUID sessionId,
        @RequestBody ru.dto.command.GenerateCourseRequest request
    ) {
        log.info("Аддитивная генерация курса: sessionId={}, period={}, course={}, kinds={}, educators={}",
                sessionId, request.studyPeriodId(), request.courseId(), request.kinds(), request.educatorIds());

        ScheduleSession session = generationService.generateCourseAdditive(
            sessionId, request.studyPeriodId(), request.courseId(),
            request.kinds(), request.educatorIds(), request.version(), "admin");

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Очистка размещений сессии, КРОМЕ закреплённых. Охват — опционально по курсу, виду
     * и/или преподавателю (зеркально охвату генерации).
     *
     * <p>POST /api/schedule/command/sessions/{sessionId}/clear</p>
     * <p>Тело: {@code { "courseId": 705, "kinds": ["PRACTICAL_WORK"], "educatorIds": [317] }} —
     * все поля опциональны. Пусто всё → очистка всей сессии (кроме замков).</p>
     *
     * @return количество удалённых размещений + сессия с новой версией
     */
    @PostMapping("/sessions/{sessionId}/clear")
    public ResponseEntity<ru.dto.command.ClearPlacementsResponse> clearPlacements(
        @PathVariable UUID sessionId,
        @RequestBody(required = false) ru.dto.command.ClearPlacementsRequest request
    ) {
        Integer courseId = request != null ? request.courseId() : null;
        java.util.List<ru.enums.KindOfStudy> kinds = request != null ? request.kinds() : null;
        java.util.List<Integer> educatorIds = request != null ? request.educatorIds() : null;
        Long version = request != null ? request.version() : null;
        log.info("Очистка размещений: sessionId={}, course={}, kinds={}, educators={}, version={}",
                sessionId, courseId, kinds, educatorIds, version);

        ScheduleGenerationService.ClearResult result =
                generationService.clearPlacements(sessionId, courseId, kinds, educatorIds, version, "admin");

        return ResponseEntity.ok(new ru.dto.command.ClearPlacementsResponse(
                result.removed(), sessionMapper.toDto(result.session())));
    }

    /**
     * Пересобрать read-модель сессии из write-стороны (ремонт отображения).
     *
     * POST /api/schedule/command/sessions/{sessionId}/reproject
     *
     * <p>Расписание не меняется: даты, слоты и замки берутся из тех же размещений — обновляются
     * только денормализованные поля (преподаватель, группа, тема, аудитория). Нужно для данных,
     * разошедшихся до появления автоматической перепроекции при правке назначений.</p>
     *
     * @return количество перепроецированных размещений
     */
    @PostMapping("/sessions/{sessionId}/reproject")
    public ResponseEntity<Integer> reproject(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(scheduleSynchronizer.reprojectSession(sessionId));
    }

    /**
     * Находки правила порядка изучения во всём расписании сессии.
     *
     * <p>GET /api/schedule/command/sessions/{sessionId}/order-violations</p>
     *
     * <p>Два вида (см. {@link ru.services.order.OrderViolation.Kind}), оба — относительно
     * предшествующей по плану лекции (лекция поз. 20 → практика поз. 21): занятие стоит раньше
     * неё (ошибка порядка) либо слишком далеко после (предупреждение об отрыве, порог —
     * {@code schedule.order.max-lecture-gap-days}).</p>
     *
     * <p>Это <b>подсказка, а не запрет</b>: расписание валидно по ресурсам, перенос и установка
     * не блокируются. Как показывать находку — решает UI; контракт несёт только семантику.</p>
     *
     * <p>Отдаётся <b>одним запросом на всё расписание</b> (а не на каждое наведение): клиент
     * держит карту находок и перезапрашивает её после каждого изменения.</p>
     */
    @GetMapping("/sessions/{sessionId}/order-violations")
    public ResponseEntity<List<OrderViolationDto>> orderViolations(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(lessonOrderService.violationsOf(sessionId));
    }

    /**
     * Получить сессию по ID.
     *
     * GET /api/schedule/command/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ScheduleSessionDto> getSession(@PathVariable UUID sessionId) {
        ScheduleSession session = generationService.getScheduleSession(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Получить сессию для редактирования «живого» расписания.
     *
     * <p>POST /api/schedule/command/sessions/editable</p>
     *
     * <p>Находит сессию текущего расписания и при необходимости переоткрывает её
     * для редактирования — без повторной генерации. Возвращает 204, если расписания
     * (ни одной сессии с размещениями) ещё нет.</p>
     */
    @PostMapping("/sessions/editable")
    public ResponseEntity<ScheduleSessionDto> getEditableSession() {
        return generationService.getOrCreateEditableSession("user")
            .map(session -> ResponseEntity.ok(sessionMapper.toDto(session)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Получить/создать рабочую сессию для периода (Путь 2, Фаза B — ручная раскладка).
     *
     * <p>POST /api/schedule/command/sessions/for-period/{studyPeriodId}</p>
     *
     * <p>Возвращает живую сессию периода или создаёт пустой черновик. Позволяет
     * раскладывать вручную семестр, для которого расписание ещё не генерировалось.</p>
     */
    @PostMapping("/sessions/for-period/{studyPeriodId}")
    public ResponseEntity<ScheduleSessionDto> getSessionForPeriod(@PathVariable Integer studyPeriodId) {
        ScheduleSession session = generationService.getOrCreateSessionForPeriod(studyPeriodId, "user");
        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Перенести занятие с optimistic lock.
     *
     * POST /api/schedule/command/sessions/{sessionId}/move-lesson
     */
    @PostMapping("/sessions/{sessionId}/move-lesson")
    public ResponseEntity<?> moveLesson(
        @PathVariable UUID sessionId,
        @RequestBody MoveLessonRequest request
    ) {
        log.info("Перенос занятия: placementId={}, newDate={}, version={}",
                request.placementId(), request.newDate(), request.version());

        try {
            // Аудитории подбираются на бэке (см. LessonMoveService) —
            // request.newAuditoriumIds() намеренно не используется.
            // Пересортировка трека входит в саму команду (одна транзакция) — отдельного вызова
            // /reorder с фронта больше нет.
            LessonMoveService.MoveResult result = lessonMoveService.moveLesson(
                request.placementId(),
                request.newDate(),
                request.newSlot(),
                request.version(),
                "user"
            );

            return ResponseEntity.ok(moveResponse(result));

        // Конфликт версий (optimistic lock) ловит CommandExceptionHandler: он срабатывает на
        // КОММИТЕ транзакции и одинаков для всех команд — здесь ему делать нечего.
        } catch (LessonMoveConflictException e) {
            log.warn("❌ Resource conflict при переносе: {}", e.getMessage());

            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(new ConflictResponse(
                    "RESOURCE_CONFLICT",
                    "Невозможно перенести занятие: " + e.getMessage()
                        + ". Обновите данные и выберите другой слот.",
                    currentVersionOf(sessionId)
                ));
        }
    }

    /**
     * Перенести цепочку занятий как единое целое с optimistic lock.
     *
     * POST /api/schedule/command/sessions/{sessionId}/move-chain
     */
    @PostMapping("/sessions/{sessionId}/move-chain")
    public ResponseEntity<?> moveChain(
        @PathVariable UUID sessionId,
        @RequestBody MoveChainRequest request
    ) {
        log.info("Перенос цепочки: {} звеньев, newStartDate={}, version={}",
                request.placementIds() != null ? request.placementIds().size() : 0,
                request.newStartDate(), request.version());

        try {
            // Аудитории подбираются на бэке — в запросе их нет.
            LessonMoveService.MoveResult result = lessonChainMoveService.moveChain(
                request.placementIds(),
                request.newStartDate(),
                request.newStartSlot(),
                request.version(),
                "user"
            );

            return ResponseEntity.ok(moveResponse(result));

        // Конфликт версий — в CommandExceptionHandler (см. выше).
        } catch (LessonMoveConflictException e) {
            log.warn("❌ Resource conflict при переносе цепочки: {}", e.getMessage());

            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(new ConflictResponse(
                    "RESOURCE_CONFLICT",
                    "Невозможно перенести цепочку: " + e.getMessage()
                        + ". Обновите данные и выберите другой слот.",
                    currentVersionOf(sessionId)
                ));
        }
    }

    /**
     * Неразмещённые занятия выбранных курсов (палитра ручной раскладки, Фаза B).
     *
     * GET /api/schedule/command/sessions/{sessionId}/unplaced?courseIds=1,2,3
     */
    @GetMapping("/sessions/{sessionId}/unplaced")
    public ResponseEntity<List<UnplacedLessonDto>> getUnplaced(
        @PathVariable UUID sessionId,
        @RequestParam(required = false) List<Integer> courseIds
    ) {
        List<UnplacedLessonDto> unplaced = manualPlacementService.findUnplaced(
            sessionId, courseIds == null ? List.of() : courseIds);
        return ResponseEntity.ok(unplaced);
    }

    /**
     * Доска раскладки: все сущности выбранных курсов со счётчиками total/placed/unplaced
     * (сущность → дисциплина → занятие). Показывает и полностью размещённые/сгенерированные
     * сущности, в отличие от {@code /unplaced} (только очередь).
     *
     * <p>GET /api/schedule/command/sessions/{sessionId}/placement-board?courseIds=1,2&axis=GROUP</p>
     *
     * @param axis ось группировки (GROUP по умолчанию | EDUCATOR)
     */
    @GetMapping("/sessions/{sessionId}/placement-board")
    public ResponseEntity<PlacementBoardDto> placementBoard(
        @PathVariable UUID sessionId,
        @RequestParam(required = false) List<Integer> courseIds,
        @RequestParam(defaultValue = "GROUP") BoardAxis axis
    ) {
        PlacementBoardDto board = placementBoardService.build(
            sessionId, courseIds == null ? List.of() : courseIds, axis);
        return ResponseEntity.ok(board);
    }

    /**
     * Лёгкие счётчики «распределено N/M» по каждому курсу (для индикатора во вкладке генерации).
     *
     * <p>GET /api/schedule/command/sessions/{sessionId}/placement-counts?courseIds=1,2</p>
     */
    @GetMapping("/sessions/{sessionId}/placement-counts")
    public ResponseEntity<List<ru.dto.board.CoursePlacementCountDto>> placementCounts(
        @PathVariable UUID sessionId,
        @RequestParam(required = false) List<Integer> courseIds
    ) {
        return ResponseEntity.ok(placementBoardService.countsByCourse(
            sessionId, courseIds == null ? List.of() : courseIds));
    }

    /**
     * Куда можно поставить занятие из палитры — допустимые ячейки (Фаза B).
     *
     * POST /api/schedule/command/sessions/{sessionId}/placement-options
     */
    @PostMapping("/sessions/{sessionId}/placement-options")
    public ResponseEntity<List<MoveOptionDto>> placementOptions(
        @PathVariable UUID sessionId,
        @RequestBody PlacementOptionsRequest request
    ) {
        List<MoveOptionDto> options = manualPlacementService.findPlacementOptions(
            sessionId, request.assignmentId(), request.rootEntityType(),
            request.rootEntityId(), request.studyPeriodId());
        return ResponseEntity.ok(options);
    }

    /**
     * Ручная установка занятия в слот (Фаза B). Создаёт MANUAL/locked размещение.
     *
     * POST /api/schedule/command/sessions/{sessionId}/placements
     */
    @PostMapping("/sessions/{sessionId}/placements")
    public ResponseEntity<?> placeManually(
        @PathVariable UUID sessionId,
        @RequestBody ManualPlacementRequest request
    ) {
        log.info("Ручная установка: sessionId={}, assignmentId={}, date={}, slot={}",
                sessionId, request.assignmentId(), request.date(), request.slot());
        try {
            ScheduleSession session = manualPlacementService.place(
                sessionId, request.assignmentId(), request.date(), request.slot(),
                request.studyPeriodId(), request.version(), "user");
            return ResponseEntity.ok(sessionMapper.toDto(session));
        } catch (LessonMoveConflictException e) {
            log.warn("❌ Конфликт ручной установки: {}", e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(new ConflictResponse(
                    "RESOURCE_CONFLICT",
                    "Невозможно разместить занятие: " + e.getMessage() + ". Выберите другой слот.",
                    currentVersionOf(sessionId)));
        }
    }

    /**
     * Снять размещение (вернуть занятие в палитру неразмещённых).
     *
     * <p>DELETE /api/schedule/command/placements/{placementId}?version=N</p>
     *
     * <p>Версия — query-параметром, а не телом: у DELETE тело не принято (и не всеми прокси/
     * клиентами поддерживается). Семантика та же, что у остальных команд: устаревшая версия → 409.</p>
     */
    @DeleteMapping("/placements/{placementId}")
    public ResponseEntity<ScheduleSessionDto> removePlacement(
        @PathVariable UUID placementId,
        @RequestParam(required = false) Long version
    ) {
        ScheduleSession session = manualPlacementService.remove(placementId, version, "user");
        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Закрепить/открепить занятие (пин, Фича 2). Закрепляет всю цепочку занятия.
     *
     * PATCH /api/schedule/command/placements/{placementId}/lock
     */
    @PatchMapping("/placements/{placementId}/lock")
    public ResponseEntity<ScheduleSessionDto> setLock(
        @PathVariable UUID placementId,
        @RequestBody LockPlacementRequest request
    ) {
        log.info("Закрепление: placementId={}, locked={}, placementIds={}",
                placementId, request.locked(),
                request.placementIds() != null ? request.placementIds().size() : "все");

        ScheduleSession session = lessonPinService.setLock(
                placementId, request.locked(), "user", request.placementIds(), request.version());
        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Ответ переноса: сессия (с новой версией) + флаги распавшихся сцепок.
     *
     * <p>Пересортировка трека выполняется ВНУТРИ команды переноса (одна транзакция), поэтому
     * отдельного эндпоинта {@code /placements/{id}/reorder} больше нет — фронт его звал вторым
     * запросом, и между двумя транзакциями оставалось окно для конкурента.</p>
     */
    private MoveLessonResponse moveResponse(LessonMoveService.MoveResult result) {
        List<ReorderProblemDto> problems = result.problems().stream()
                .map(p -> new ReorderProblemDto(p.placementId(), p.reason().name()))
                .collect(Collectors.toList());

        return new MoveLessonResponse(sessionMapper.toDto(result.session()), problems);
    }

    /**
     * Текущая версия сессии для тела {@link ConflictResponse} (или {@code null}, если сессии нет).
     */
    private Long currentVersionOf(UUID sessionId) {
        return generationService.getScheduleSession(sessionId)
            .map(ScheduleSession::getVersion)
            .orElse(null);
    }

    /**
     * Получить размещения в сессии.
     *
     * GET /api/schedule/command/sessions/{sessionId}/placements
     */
    @GetMapping("/sessions/{sessionId}/placements")
    public ResponseEntity<List<LessonPlacementDto>> getPlacements(@PathVariable UUID sessionId) {
        List<LessonPlacement> placements = generationService.getPlacements(sessionId);

        List<LessonPlacementDto> dtos = placements.stream()
            .map(placementMapper::toDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Удалить сессию.
     *
     * DELETE /api/schedule/command/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        generationService.deleteScheduleSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
