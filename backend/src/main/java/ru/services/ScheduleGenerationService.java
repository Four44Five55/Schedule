package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.*;
import ru.services.constraints.ConstraintService;
import ru.services.distribution.DistributionDiscipline;
import ru.services.factories.CellForLessonFactory;
import ru.services.generation.GenerationScope;
import ru.services.generation.GenerationScopeResolver;
import ru.services.solver.ScheduleWorkspace;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private final EducatorService educatorService;
    private final GroupService groupService;
    private final AuditoriumService auditoriumService;
    private final ConstraintService constraintService;
    private final GenerationScopeResolver scopeResolver;
    private final DistributionDiscipline distributionDiscipline;
    private final AssignmentService assignmentService;
    private final WorkspacePlacementSeeder placementSeeder;
    private final StudyPeriodService studyPeriodService;

    // ========== NEW DEPENDENCIES (Phase 3: CQRS Integration) ==========
    private final ru.repository.write.ScheduleSessionRepository sessionRepo;
    private final ru.repository.write.LessonPlacementRepository placementRepo;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Порог хранения архивных сессий (дней). Архивные сессии старше этого возраста удаляются
     * при следующей генерации ({@link #purgeOldArchivedSessions()}). {@code <= 0} — чистка выключена.
     */
    @Value("${schedule.session.archive-retention-days:30}")
    private int archiveRetentionDays;

    /**
     * Строит workspace и запускает распределение для уже разрешённой области генерации.
     *
     * <p>Единый источник дат — {@link GenerationScope#period()}: и кэш ячеек, и сам
     * workspace инициализируются календарными рамками выбранного периода (раньше
     * период неявно брался из «первого курса», а конец семестра был захардкожен).</p>
     */
    private ScheduleWorkspace generateWorkspace(
            GenerationScope scope,
            List<ru.entity.write.LessonPlacement> lockedPlacements
    ) {
        return generateWorkspace(scope, lockedPlacements, scope.lessons());
    }

    /**
     * Как {@link #generateWorkspace(GenerationScope, List)}, но распределяет ЗАДАННЫЙ набор
     * занятий (напр. только лекции или только практики — для per-kind инкрементальной генерации).
     * Засев неподвижных (Фаза 0) и рамки периода — те же.
     */
    private ScheduleWorkspace generateWorkspace(
            GenerationScope scope,
            List<ru.entity.write.LessonPlacement> lockedPlacements,
            List<Lesson> lessonsToDistribute
    ) {
        StudyPeriod period = scope.period();

        // Кэш всех ячеек на рамки периода
        CellForLessonFactory.initializeCellCache(period.getStartDate(), period.getEndDate());

        ScheduleWorkspace workspace = new ScheduleWorkspace(
                period.getStartDate(),
                period.getEndDate(),
                educatorService.getAllEntities(),
                groupService.getAllEntities(),
                auditoriumService.getAllEntities(),
                constraintService.loadAllConstraints()
        );

        // Фаза 0 (Фича 2): засеваем закреплённые занятия (пины) в workspace принудительно.
        // Их ресурсы становятся занятыми → распределитель раскладывает остальное «вокруг».
        // markPrePlacedLocked (внутри distribute) пометит их распределёнными+закреплёнными:
        // фазы 1–2 их пропустят (по бизнес-ключу Lesson), оптимизатор не сдвинет.
        List<Lesson> prePlaced = new java.util.ArrayList<>();
        for (ru.entity.write.LessonPlacement locked : lockedPlacements) {
            Lesson seeded = placementSeeder.seedInto(workspace, locked);
            if (seeded != null) {
                prePlaced.add(seeded);
            }
        }
        if (!prePlaced.isEmpty()) {
            log.info("Фаза 0: засеяно {} закреплённых занятий", prePlaced.size());
        }

        distributionDiscipline.distribute(
                workspace, lessonsToDistribute, educatorService.getAllEntities(), prePlaced);
        return workspace;
    }

    // ========== ========== ========== ========== ==========
    // PHASE 3: CQRS INTEGRATION - NEW METHODS
    // ========== ========== ========== ========== ==========

    /**
     * ✅ НОВЫЙ МЕТОД: Создать пустую сессию для редактирования расписания.
     *
     * @param name Название сессии
     * @param user Пользователь
     * @return Созданная сессия
     */
    @Transactional
    public ru.entity.write.ScheduleSession createScheduleSession(String name, String user) {
        ru.entity.write.ScheduleSession session = new ru.entity.write.ScheduleSession(name, user);
        return sessionRepo.save(session);
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Генерация расписания с сохранением в БД (CQRS Command Side).
     *
     * <p>Процесс:</p>
     * <ol>
     *   <li>Создаёт сессию</li>
     *   <li>Генерирует workspace (существующая логика)</li>
     *   <li>Сохраняет placements в БД</li>
     *   <li>Публикует событие для синхронизации Query Side</li>
     * </ol>
     *
     * @param name Название расписания
     * @param studyPeriodId Учебный период генерации (источник дат и набора курсов)
     * @param courseIds Опциональный поднабор курсов периода (пусто/null — все курсы периода)
     * @param user Пользователь
     * @return Сессия сгенерированного расписания
     */
    @Transactional
    public ru.entity.write.ScheduleSession generateSchedule(
            String name,
            Integer studyPeriodId,
            List<Integer> courseIds,
            String user
    ) {
        log.info("Генерация расписания: name={}, period={}, courses={}", name, studyPeriodId, courseIds);

        // Новая сессия → нет закреплённых занятий (пинов): обычная генерация «с нуля».
        ru.entity.write.ScheduleSession session = new ru.entity.write.ScheduleSession(name, user);
        session.updateStatus(ru.enums.SessionStatus.GENERATING, user);
        session = sessionRepo.save(session);

        return runGeneration(session, studyPeriodId, courseIds, user, List.of());
    }

    /**
     * Перегенерация существующей сессии с сохранением закреплённых занятий (Фича 2, Фаза A).
     *
     * <p>Закреплённые ({@code locked}) размещения сессии засеваются в workspace как
     * неподвижные (Фаза 0), а распределитель перераскладывает только остальное «вокруг»
     * них. Источник правды — workspace: старые размещения (включая пины) удаляются и
     * пересоздаются из сетки, при этом пины проштамповываются обратно
     * ({@code locked=true} + сохранённый {@code source}). Так нет дублей пинов, а событие
     * несёт полный набор → проектор перестраивает view с замками.</p>
     *
     * @param sessionId     перегенерируемая сессия (источник пинов)
     * @param studyPeriodId период генерации
     * @param courseIds     опциональный поднабор курсов
     * @param user          автор изменения
     */
    @Transactional
    public ru.entity.write.ScheduleSession regenerateKeepingLocked(
            java.util.UUID sessionId,
            Integer studyPeriodId,
            List<Integer> courseIds,
            String user
    ) {
        log.info("Перегенерация с сохранением замков: sessionId={}, period={}, courses={}",
                sessionId, studyPeriodId, courseIds);

        ru.entity.write.ScheduleSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена: " + sessionId));

        List<ru.entity.write.LessonPlacement> locked = placementRepo.findBySessionId(sessionId).stream()
                .filter(ru.entity.write.LessonPlacement::isLocked)
                .toList();
        log.info("Закреплённых занятий к сохранению: {}", locked.size());

        session.updateStatus(ru.enums.SessionStatus.GENERATING, user);
        session = sessionRepo.save(session);

        return runGeneration(session, studyPeriodId, courseIds, user, locked);
    }

    /**
     * АДДИТИВНАЯ генерация одного курса (дисциплины) — инкрементальная сборка расписания.
     *
     * <p>В отличие от {@link #runGeneration} (удаляет всё незапертое и переписывает scope),
     * здесь <b>ничего существующего не удаляется</b>: ВСЕ уже стоящие размещения сессии
     * засеваются в workspace как неподвижные (Фаза 0), а распределитель раскладывает только
     * <b>неразмещённые</b> занятия курса «вокруг» них. Так можно собирать расписание по одной
     * дисциплине за раз, не теряя ручные корректировки и работу по другим дисциплинам, и не
     * запирая всё подряд между шагами. Генерируется дисциплина целиком (все виды) — чтобы
     * учитывались равномерность и интервалы между лекциями (двухфазный распределитель).</p>
     *
     * <p>Гранулярность «лекции/практики» достигается очисткой по виду
     * ({@link #clearPlacements}): сгенерировать дисциплину → очистить практики → поправить
     * лекции вручную → сгенерировать снова (практики лягут заново вокруг лекций).</p>
     *
     * @param sessionId     сессия периода
     * @param studyPeriodId учебный период (даты)
     * @param courseId      курс (дисциплина в периоде)
     * @param kinds         опциональный фильтр по видам (напр. только {@code LECTURE}, или
     *                      «практики» = все виды кроме лекций); {@code null}/пусто — вся дисциплина
     * @param educatorIds   опциональный фильтр по преподавателям: раскладываются только занятия,
     *                      которые ведёт кто-то из них; {@code null}/пусто — все преподаватели курса.
     *                      ⚠️ Сужая охват, вы сужаете и «кругозор» распределителя: равномерность и
     *                      интервалы считаются только по взятым занятиям, остальные для него —
     *                      неподвижные обстоятельства (они засеяны как есть)
     * @param user          автор
     * @return сессия с добавленными размещениями
     */
    @Transactional
    public ru.entity.write.ScheduleSession generateCourseAdditive(
            java.util.UUID sessionId, Integer studyPeriodId, Integer courseId,
            java.util.List<ru.enums.KindOfStudy> kinds, java.util.List<Integer> educatorIds, String user) {
        log.info("Аддитивная генерация курса: sessionId={}, period={}, course={}, kinds={}, educators={}",
                sessionId, studyPeriodId, courseId, kinds, educatorIds);

        ru.entity.write.ScheduleSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена: " + sessionId));

        // Все существующие размещения — засев Фазы 0 (неподвижные обстоятельства).
        List<ru.entity.write.LessonPlacement> existing = placementRepo.findBySessionId(sessionId);
        java.util.Set<AssignmentKey> existingKeys = existing.stream()
                .map(ScheduleGenerationService::keyOf)
                .collect(java.util.stream.Collectors.toSet());

        // Область = один курс. Опциональные фильтры (вид занятия, преподаватель) сужают ЦЕЛЕВОЙ
        // набор, но не засев: всё остальное всё равно стоит на местах и учитывается как занятое.
        GenerationScope scope = scopeResolver.resolve(studyPeriodId, java.util.List.of(courseId));
        List<Lesson> target = scope.lessons().stream()
                .filter(l -> kinds == null || kinds.isEmpty() || kinds.contains(l.getKindOfStudy()))
                .filter(l -> matchesEducators(l, educatorIds))
                .toList();

        // Workspace с засевом ВСЕХ существующих; distribute разложит только неразмещённые
        // занятия целевого набора (уже стоящие пропускаются — они засеяны).
        ru.services.solver.ScheduleWorkspace workspace = generateWorkspace(scope, existing, target);

        // Извлекаем ТОЛЬКО новые размещения (ключа нет среди существующих) как GENERATED.
        List<ru.entity.write.LessonPlacement> created = extractNewPlacements(workspace, session, user, existingKeys);
        for (ru.entity.write.LessonPlacement p : created) {
            session.addPlacement(p);
        }
        session.setStudyPeriod(scope.period());
        session.updateStatus(ru.enums.SessionStatus.READY_FOR_EDIT, user);
        session = sessionRepo.save(session);

        // По CREATED-событию на каждое НОВОЕ размещение — проектор добавит их view, не трогая
        // существующие (в отличие от ScheduleGeneratedEvent, который перестраивает view периода).
        for (ru.entity.write.LessonPlacement p : created) {
            eventPublisher.publishEvent(new ru.events.PlacementChangedEvent(
                    session.getId(), p.getId(), p,
                    ru.events.PlacementChangedEvent.PlacementChangeType.CREATED));
        }

        log.info("✅ Аддитивно добавлено {} размещений курса {} (засеяно существующих: {})",
                created.size(), courseId, existing.size());
        return session;
    }

    /**
     * Ведёт ли занятие кто-то из указанных преподавателей. Пустой/{@code null} список — фильтра
     * нет (все). Общий предикат для генерации и очистки, чтобы охват «по преподавателю» означал
     * в обеих операциях одно и то же.
     */
    private static boolean matchesEducators(Lesson lesson, java.util.List<Integer> educatorIds) {
        if (educatorIds == null || educatorIds.isEmpty()) {
            return true;
        }
        return lesson.getEducators() != null && lesson.getEducators().stream()
                .anyMatch(e -> educatorIds.contains(e.getId()));
    }

    /**
     * Извлекает из workspace ТОЛЬКО новые размещения (ключ {@code (slot, stream)} не среди
     * {@code existingKeys}) как обычные {@code GENERATED}. Для аддитивной генерации: существующие
     * (в т.ч. засеянные) не пересоздаются.
     */
    private List<ru.entity.write.LessonPlacement> extractNewPlacements(
            ru.services.solver.ScheduleWorkspace workspace,
            ru.entity.write.ScheduleSession session,
            String user,
            java.util.Set<AssignmentKey> existingKeys) {
        List<ru.entity.write.LessonPlacement> placements = new java.util.ArrayList<>();
        var assignmentMap = getAllAssignments().stream()
                .collect(java.util.stream.Collectors.toMap(
                        a -> new AssignmentKey(a.getCurriculumSlot().getId(), a.getStudyStream().getId()),
                        a -> a, (a, b) -> a));

        for (var entry : workspace.getGrid().getGridMap().entrySet()) {
            ru.entity.CellForLesson cell = entry.getKey();
            for (var abstractLesson : entry.getValue()) {
                if (!(abstractLesson instanceof ru.entity.Lesson lesson)) continue;
                if (lesson.getCurriculumSlot() == null || lesson.getStudyStream() == null) continue;
                AssignmentKey key = new AssignmentKey(
                        lesson.getCurriculumSlot().getId(), lesson.getStudyStream().getId());
                if (existingKeys.contains(key)) continue; // уже размещено — аддитивно не трогаем
                Assignment assignment = assignmentMap.get(key);
                if (assignment == null) continue;
                ru.entity.write.LessonPlacement placement = new ru.entity.write.LessonPlacement(
                        assignment, cell.getDate(), cell.getTimeSlotPair(), session, user);
                if (lesson.getAssignedAuditoriums() != null) {
                    placement.getAssignedAuditoriums().addAll(lesson.getAssignedAuditoriums());
                }
                placements.add(placement);
            }
        }
        return placements;
    }

    /**
     * Очистка размещений сессии, КРОМЕ закреплённых ({@code locked}). Охват сужается опционально:
     * по курсу (дисциплине) и/или по виду занятия. Пусто оба → очистка всей сессии.
     *
     * <p>Ключ потока: генерация — аддитивная, поэтому «переделать» = очистить → сгенерировать
     * заново. Очистка по виду («удалить только практики курса») даёт гранулярность лекции/практики.</p>
     *
     * @param sessionId   сессия
     * @param courseId    курс (дисциплина) или {@code null} — все курсы
     * @param kinds       виды занятий к удалению или {@code null}/пусто — все виды (напр.
     *                    «кроме лекций» = все виды, кроме {@code LECTURE}; «только лекции» = {@code [LECTURE]})
     * @param educatorIds преподаватели или {@code null}/пусто — все: удаляются только занятия,
     *                    которые ведёт кто-то из них (зеркально охвату генерации)
     * @param user        автор (для логов)
     * @return сколько размещений удалено
     */
    @Transactional
    public int clearPlacements(java.util.UUID sessionId, Integer courseId,
                               java.util.List<ru.enums.KindOfStudy> kinds,
                               java.util.List<Integer> educatorIds, String user) {
        boolean allKinds = kinds == null || kinds.isEmpty();
        boolean allEducators = educatorIds == null || educatorIds.isEmpty();
        List<ru.entity.write.LessonPlacement> toDelete = placementRepo.findBySessionId(sessionId).stream()
                .filter(p -> !p.isLocked()) // замки не трогаем
                .filter(p -> courseId == null
                        || p.getAssignment().getCurriculumSlot().getDisciplineCourse().getId().equals(courseId))
                .filter(p -> allKinds
                        || kinds.contains(p.getAssignment().getCurriculumSlot().getKindOfStudy()))
                // Охват по преподавателю — по составу НАЗНАЧЕНИЯ (совместное занятие двух
                // преподавателей попадает под охват каждого из них).
                .filter(p -> allEducators || p.getAssignment().getEducators().stream()
                        .anyMatch(e -> educatorIds.contains(e.getId())))
                .toList();

        List<java.util.UUID> ids = toDelete.stream().map(ru.entity.write.LessonPlacement::getId).toList();
        placementRepo.deleteAll(toDelete);
        // DELETED-события → проектор удалит соответствующие view.
        for (java.util.UUID id : ids) {
            eventPublisher.publishEvent(new ru.events.PlacementChangedEvent(sessionId, id));
        }
        log.info("🧹 Очистка сессии {}: удалено {} размещений (course={}, kinds={}, educators={}, кроме замков)",
                sessionId, ids.size(), courseId, allKinds ? "все" : kinds,
                allEducators ? "все" : educatorIds);
        return ids.size();
    }

    /**
     * Ядро генерации: строит workspace (с засевом пинов, если есть), переписывает
     * размещения сессии из сетки и публикует событие. Общее для генерации «с нуля»
     * (пустой {@code lockedPlacements}) и перегенерации вокруг замков.
     */
    private ru.entity.write.ScheduleSession runGeneration(
            ru.entity.write.ScheduleSession session,
            Integer studyPeriodId,
            List<Integer> courseIds,
            String user,
            List<ru.entity.write.LessonPlacement> lockedPlacements
    ) {
        try {
            // Отпечаток пинов ДО удаления: ключ (слот+поток) → исходный source.
            // По нему при экстракте проштампуем пересозданные пины (locked + source).
            java.util.Map<AssignmentKey, ru.enums.PlacementSource> lockedFingerprint =
                    lockedPlacements.stream().collect(java.util.stream.Collectors.toMap(
                            ScheduleGenerationService::keyOf,
                            ru.entity.write.LessonPlacement::getSource,
                            (a, b) -> a));

            // 1. Область генерации + workspace (внутри — Фаза 0: засев замков).
            GenerationScope scope = scopeResolver.resolve(studyPeriodId, courseIds);

            // Путь 2: привязываем сессию к периоду — архивация и проекция скоупятся по нему.
            session.setStudyPeriod(scope.period());

            ru.services.solver.ScheduleWorkspace workspace = generateWorkspace(scope, lockedPlacements);

            // 2. Удаляем ВСЕ старые размещения сессии (включая пины — пересоздадим из сетки).
            //    addPlacement ниже зовётся после flush, поэтому ленивая коллекция сессии
            //    инициализируется уже пустой (orphanRemoval не воскрешает удалённые).
            List<ru.entity.write.LessonPlacement> oldPlacements = placementRepo.findBySessionId(session.getId());
            if (!oldPlacements.isEmpty()) {
                log.info("🗑️ Удаляем {} старых размещений", oldPlacements.size());
                placementRepo.deleteAll(oldPlacements);
                placementRepo.flush(); // deletes до inserts: иначе UNIQUE(session, assignment) конфликтует
            }

            // 3. Извлекаем размещения из сетки; пины — со штампом locked/source.
            List<ru.entity.write.LessonPlacement> placements = extractPlacementsFromWorkspace(
                    workspace, session, user, lockedFingerprint);

            for (ru.entity.write.LessonPlacement placement : placements) {
                session.addPlacement(placement);
            }

            // 4. Статус → готово к редактированию.
            session.updateStatus(ru.enums.SessionStatus.READY_FOR_EDIT, user);
            session = sessionRepo.save(session);

            // 5. Новое расписание ЗАМЕНЯЕТ прежнее В РАМКАХ ПЕРИОДА: архивируем остальные
            //    активные сессии этого периода (Путь 2). Сессии других семестров не трогаем.
            archivePreviousSessions(session.getId(), scope.period().getId(), user);

            // 5.1 Разовая чистка «протухшего» архива. Планировщика нет (приложение не работает
            //     постоянно), поэтому цепляемся к генерации — она заведомо идёт при живом приложении.
            purgeOldArchivedSessions();

            // 6. Событие с ПОЛНЫМ набором (пины + сгенерированное) + рамки периода →
            //    проектор перестроит view только для этого периода.
            eventPublisher.publishEvent(new ru.events.ScheduleGeneratedEvent(
                    session.getId(), placements,
                    scope.period().getStartDate(), scope.period().getEndDate()));

            log.info("✅ Расписание сгенерировано: sessionId={}, placementsCount={}, пинов={}",
                    session.getId(), placements.size(), lockedFingerprint.size());

            return session;

        } catch (Exception e) {
            session.updateStatus(ru.enums.SessionStatus.INITIALIZED, user);
            sessionRepo.save(session);
            throw new RuntimeException("Ошибка генерации расписания", e);
        }
    }

    /**
     * Ключ соответствия занятия ↔ назначения: пара {@code (curriculumSlotId, studyStreamId)}.
     * Уникальна (один слот + один поток = одно назначение = одно занятие), поэтому надёжно
     * связывает in-memory сетку с {@link Assignment} и с отпечатком пинов.
     */
    private record AssignmentKey(Integer curriculumSlotId, Integer studyStreamId) {}

    /** Ключ закреплённого размещения (по его {@link Assignment}). */
    private static AssignmentKey keyOf(ru.entity.write.LessonPlacement placement) {
        Assignment a = placement.getAssignment();
        return new AssignmentKey(a.getCurriculumSlot().getId(), a.getStudyStream().getId());
    }

    /**
     * Архивирует все активные сессии, кроме указанной.
     *
     * <p>Поддерживает инвариант «одно живое расписание»: после генерации новая
     * сессия остаётся единственной активной, прежние переходят в
     * {@link ru.enums.SessionStatus#ARCHIVED} и больше не участвуют ни в
     * {@link #getOrCreateEditableSession}, ни в проекции read-model.</p>
     *
     * <p>Используется существующий жизненный цикл статусов, а не удаление —
     * история сохраняется; периодическая чистка архива выполняется отдельно
     * ({@code ScheduleSessionRepository.deleteOldArchivedSessions}).</p>
     *
     * @param keepSessionId сессия, которую оставляем активной
     * @param user          пользователь, выполняющий действие
     */
    private void archivePreviousSessions(java.util.UUID keepSessionId, Integer periodId, String user) {
        // Путь 2: архивируем только сессии ЭТОГО периода; без периода (легаси) — все.
        List<ru.entity.write.ScheduleSession> active = (periodId != null)
                ? sessionRepo.findActiveSessionsByPeriod(periodId, ru.enums.SessionStatus.ARCHIVED)
                : sessionRepo.findActiveSessions(ru.enums.SessionStatus.ARCHIVED);

        List<ru.entity.write.ScheduleSession> previous = active.stream()
                .filter(s -> !s.getId().equals(keepSessionId))
                .toList();

        if (previous.isEmpty()) {
            return;
        }

        previous.forEach(s -> s.updateStatus(ru.enums.SessionStatus.ARCHIVED, user));
        sessionRepo.saveAll(previous);
        log.info("🗄️  Архивировано прежних активных сессий периода: {}", previous.size());
    }

    /**
     * Разовая чистка «протухших» архивных сессий (замена планировщика для непостоянно
     * работающего приложения — см. вызов в {@link #runGeneration}).
     *
     * <p>Удаляет только сессии в статусе {@link ru.enums.SessionStatus#ARCHIVED}, не обновлявшиеся
     * дольше {@link #archiveRetentionDays} дней; связанные {@code lesson_placement} уходят каскадом
     * по FK ({@code ON DELETE CASCADE}). Только что заархивированные сессии под порог не попадают
     * (их {@code updatedAt} — сейчас). При {@code archiveRetentionDays <= 0} чистка отключена.</p>
     */
    private void purgeOldArchivedSessions() {
        if (archiveRetentionDays <= 0) {
            return;
        }
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusDays(archiveRetentionDays);
        int removed = sessionRepo.deleteOldArchivedSessions(ru.enums.SessionStatus.ARCHIVED, threshold);
        if (removed > 0) {
            log.info("🧹 Удалено протухших архивных сессий (старше {} дн.): {}", archiveRetentionDays, removed);
        }
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Получить сессию по ID.
     *
     * @param sessionId ID сессии
     * @return Сессия
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ru.entity.write.ScheduleSession> getScheduleSession(java.util.UUID sessionId) {
        return sessionRepo.findById(sessionId);
    }

    /**
     * Получить сессию «живого» расписания, пригодную для редактирования.
     *
     * <p>Берёт самую свежую неархивную сессию, у которой есть размещения
     * (т.е. реально отображаемое расписание), и при необходимости переоткрывает
     * её для редактирования (статус → READY_FOR_EDIT). Это позволяет править
     * расписание в любой момент учебного процесса <b>без повторной генерации</b>.</p>
     *
     * @param user пользователь, выполняющий действие
     * @return Сессия, готовая к редактированию, или пустой Optional, если расписания нет
     */
    @Transactional
    public java.util.Optional<ru.entity.write.ScheduleSession> getOrCreateEditableSession(String user) {
        java.util.Optional<ru.entity.write.ScheduleSession> candidate =
                sessionRepo.findActiveSessions(ru.enums.SessionStatus.ARCHIVED).stream()
                        .filter(s -> placementRepo.countBySessionId(s.getId()) > 0)
                        .findFirst(); // findActiveSessions отсортирован по updatedAt DESC

        candidate.ifPresent(session -> {
            // Переоткрываем для редактирования, если сессия не в редактируемом статусе.
            if (!session.getStatus().isEditable()) {
                session.updateStatus(ru.enums.SessionStatus.READY_FOR_EDIT, user);
                sessionRepo.save(session);
            }
        });

        return candidate;
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Получить placements сессии.
     *
     * @param sessionId ID сессии
     * @return Список размещений
     */
    @Transactional(readOnly = true)
    public List<ru.entity.write.LessonPlacement> getPlacements(java.util.UUID sessionId) {
        return placementRepo.findBySessionId(sessionId);
    }

    /**
     * Получить или создать рабочую сессию для учебного периода (Путь 2, Фаза B).
     *
     * <p>Возвращает свежую неархивную сессию периода (переоткрыв для редактирования при
     * необходимости), а если её нет — создаёт пустой черновик, привязанный к периоду.
     * Это «вход» для ручной раскладки: ставить занятия в семестр, для которого расписание
     * ещё не генерировалось, не трогая другие семестры.</p>
     *
     * @param studyPeriodId учебный период
     * @param user          пользователь
     * @return рабочая сессия периода
     */
    @Transactional
    public ru.entity.write.ScheduleSession getOrCreateSessionForPeriod(Integer studyPeriodId, String user) {
        List<ru.entity.write.ScheduleSession> active =
                sessionRepo.findActiveSessionsByPeriod(studyPeriodId, ru.enums.SessionStatus.ARCHIVED);

        if (!active.isEmpty()) {
            ru.entity.write.ScheduleSession session = active.get(0); // отсортированы по updatedAt DESC
            if (!session.getStatus().isEditable()) {
                session.updateStatus(ru.enums.SessionStatus.READY_FOR_EDIT, user);
                sessionRepo.save(session);
            }
            return session;
        }

        StudyPeriod period = studyPeriodService.getEntityById(studyPeriodId);
        ru.entity.write.ScheduleSession session =
                new ru.entity.write.ScheduleSession("Расписание: " + period.getName(), user);
        session.setStudyPeriod(period);
        session.updateStatus(ru.enums.SessionStatus.READY_FOR_EDIT, user);
        return sessionRepo.save(session);
    }

    /**
     * Перенос занятия вынесен в {@link LessonMoveService}: он пересоздаёт workspace и
     * повторно валидирует все ресурсы (включая аудиторию) перед записью.
     */

    /**
     * ✅ НОВЫЙ МЕТОД: Удалить сессию.
     *
     * @param sessionId ID сессии
     */
    @Transactional
    public void deleteScheduleSession(java.util.UUID sessionId) {
        sessionRepo.deleteById(sessionId);
        log.info("🗑️  Сессия удалена: sessionId={}", sessionId);
    }

    // ========== HELPER METHODS ==========

    /**
     * ✅ НОВЫЙ МЕТОД: Извлечь placements из workspace.
     *
     * @param workspace Workspace
     * @param session Сессия
     * @param user Пользователь
     * @return Список placements
     */
    private List<ru.entity.write.LessonPlacement> extractPlacementsFromWorkspace(
            ru.services.solver.ScheduleWorkspace workspace,
            ru.entity.write.ScheduleSession session,
            String user,
            java.util.Map<AssignmentKey, ru.enums.PlacementSource> lockedFingerprint
    ) {
        List<ru.entity.write.LessonPlacement> placements = new java.util.ArrayList<>();

        // Загружаем все Assignment для быстрого поиска.
        // Ключ (curriculumSlotId + studyStreamId) уникален: один слот может иметь несколько
        // assignment для разных потоков/групп.
        List<Assignment> allAssignments = getAllAssignments();
        var assignmentMap = allAssignments.stream()
            .collect(java.util.stream.Collectors.toMap(
                a -> new AssignmentKey(a.getCurriculumSlot().getId(), a.getStudyStream().getId()),
                a -> a
            ));

        // Извлекаем все размещённые уроки из ScheduleGrid
        for (var entry : workspace.getGrid().getGridMap().entrySet()) {
            ru.entity.CellForLesson cell = entry.getKey();
            List<ru.abstracts.AbstractLesson> lessons = entry.getValue();

            for (var abstractLesson : lessons) {
                if (!(abstractLesson instanceof ru.entity.Lesson lesson)) continue;

                // Валидация
                if (lesson.getCurriculumSlot() == null || lesson.getStudyStream() == null) {
                    log.warn("⚠️  Lesson без curriculumSlot или studyStream, пропускаем");
                    continue;
                }

                // Ищем Assignment по (curriculumSlotId + studyStreamId)
                AssignmentKey key = new AssignmentKey(
                    lesson.getCurriculumSlot().getId(),
                    lesson.getStudyStream().getId()
                );
                Assignment assignment = assignmentMap.get(key);
                if (assignment == null) {
                    log.warn("⚠️  Assignment не найден для curriculumSlotId={}, studyStreamId={}",
                        lesson.getCurriculumSlot().getId(),
                        lesson.getStudyStream().getId());
                    continue;
                }

                // ✅ Создаём Placement (один Lesson → один Placement).
                // Если ключ был среди закреплённых — пересоздаём как пин (locked=true)
                // с сохранённым источником; иначе обычное сгенерированное размещение.
                ru.enums.PlacementSource lockedSource = lockedFingerprint.get(key);
                ru.entity.write.LessonPlacement placement = (lockedSource != null)
                    ? new ru.entity.write.LessonPlacement(
                        assignment, cell.getDate(), cell.getTimeSlotPair(), session, user, lockedSource, true)
                    : new ru.entity.write.LessonPlacement(
                        assignment, cell.getDate(), cell.getTimeSlotPair(), session, user);

                // Добавляем аудитории
                if (lesson.getAssignedAuditoriums() != null) {
                    placement.getAssignedAuditoriums().addAll(lesson.getAssignedAuditoriums());
                }

                placements.add(placement);
            }
        }

        log.info("✅ Извлечено {} placements из workspace ({} пинов)",
                placements.size(), lockedFingerprint.size());
        return placements;
    }

    /**
     * Вспомогательный метод: Получить все Assignment из БД.
     */
    private List<Assignment> getAllAssignments() {
        return assignmentService.getAllEntities();
    }
}
