package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.entity.*;
import ru.entity.logicSchema.DisciplineCourse;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.distribution.DistributionDiscipline;
import ru.services.factories.CellForLessonFactory;
import ru.services.factories.LessonFactory;
import ru.services.solver.ScheduleWorkspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private final EducatorService educatorService;
    private final GroupService groupService;
    private final AuditoriumService auditoriumService;
    private final DisciplineCourseService disciplineCourseService;
    private final ConstraintService constraintService;
    private final LessonFactory lessonFactory;
    private final LessonSortingService lessonSorterService;
    private final DistributionDiscipline distributionDiscipline;
    private final AssignmentService assignmentService;

    // ========== NEW DEPENDENCIES (Phase 3: CQRS Integration) ==========
    private final ru.repository.write.ScheduleSessionRepository sessionRepo;
    private final ru.repository.write.LessonPlacementRepository placementRepo;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Основной метод, запускающий процесс генерации расписания для одного курса.
     */
    @Transactional(readOnly = true)
    public ScheduleWorkspace generateForCourse(Integer courseId) {
        DisciplineCourse course = disciplineCourseService.getEntityById(courseId);
        //создание кеша всех ячеек для периода
        CellForLessonFactory.initializeCellCache(
                course.getStudyPeriod().getStartDate(),
                course.getStudyPeriod().getEndDate()
        );

        List<Educator> allEducators = educatorService.getAllEntities();
        List<Group> allGroups = groupService.getAllEntities();
        List<Auditorium> allAuditoriums = auditoriumService.getAllEntities();
        AllConstraints allConstraints = constraintService.loadAllConstraints();


        List<Lesson> lessonsToPlace = lessonFactory.createLessonsForCourse(courseId);

        // --- 2. ИНИЦИАЛИЗАЦИЯ ЯДРА РЕШАТЕЛЯ ---
        ScheduleWorkspace workspace = new ScheduleWorkspace(
                course.getStudyPeriod().getStartDate(),
                course.getStudyPeriod().getEndDate(),
                allEducators,
                allGroups,
                allAuditoriums,
                allConstraints
        );
        List<Lesson> sortedLessons = lessonSorterService.getSortedLessons(lessonsToPlace);
        distributionDiscipline.distribute(workspace, sortedLessons, allEducators);


/*        // --- 3. ЗАПУСК АЛГОРИТМА ---
        LegacyAlgorithmRunner runner = new LegacyAlgorithmRunner(workspace, lessonsToPlace, lessonSorterService);
        runner.run(); // Запускаем адаптированный алгоритм*/

        // --- 4. ВОЗВРАТ РЕЗУЛЬТАТА ---
        return workspace;
    }

    @Transactional(readOnly = true)
    public ScheduleWorkspace generateForCourseList(List<Integer> courseIds) {
        // 1. Инициализация (берем период первого курса)
        DisciplineCourse firstCourse = disciplineCourseService.getEntityById(courseIds.getFirst());
        CellForLessonFactory.initializeCellCache(
                firstCourse.getStudyPeriod().getStartDate(),
                firstCourse.getStudyPeriod().getEndDate()
        );

        // 2. Создаем Общий Workspace
        ScheduleWorkspace workspace = new ScheduleWorkspace(
                firstCourse.getStudyPeriod().getStartDate(),
                firstCourse.getStudyPeriod().getEndDate(),
                educatorService.getAllEntities(),
                groupService.getAllEntities(),
                auditoriumService.getAllEntities(),
                constraintService.loadAllConstraints()
        );

        // 3. Загружаем ВСЕ уроки
        List<Lesson> allLessons = new ArrayList<>();
        for (Integer id : courseIds) {
            List<Lesson> courseLessons = lessonFactory.createLessonsForCourse(id);
            // Сразу сортируем их по позиции, чтобы в DistributionDiscipline они пришли в порядке
            courseLessons.sort(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()));
            allLessons.addAll(courseLessons);
        }

        // 4. Запускаем распределение
        distributionDiscipline.distribute(workspace, allLessons, educatorService.getAllEntities());

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
     * @param courseIds Список ID курсов
     * @param user Пользователь
     * @return Сессия сгенерированного расписания
     */
    @Transactional
    public ru.entity.write.ScheduleSession generateSchedule(
            String name,
            List<Integer> courseIds,
            String user
    ) {
        log.info("Генерация расписания: name={}, courses={}", name, courseIds);

        // 1. Создаём сессию
        ru.entity.write.ScheduleSession session = new ru.entity.write.ScheduleSession(name, user);
        session.updateStatus(ru.enums.SessionStatus.GENERATING, user);
        session = sessionRepo.save(session);

        try {
            // 2. Генерируем workspace
            ru.services.solver.ScheduleWorkspace workspace = generateForCourseList(courseIds);

            // 3. ✅ ОЧИЩАЕМ СТАРЫЕ PLACEMENTS (если сессия перегенерируется)
            List<ru.entity.write.LessonPlacement> oldPlacements = placementRepo.findBySessionId(session.getId());
            if (!oldPlacements.isEmpty()) {
                log.info("🗑️ Удаляем {} старых размещений", oldPlacements.size());
                placementRepo.deleteAll(oldPlacements);
                placementRepo.flush(); // ✅ ВАЖНО: Сбрасываем изменения в БД немедленно
                log.info("✅ Старые размещения удалены и сброшены в БД");
            }

            // 4. Извлекаем и сохраняем новые placements
            List<ru.entity.write.LessonPlacement> placements = extractPlacementsFromWorkspace(
                    workspace,
                    session,
                    user
            );

            for (ru.entity.write.LessonPlacement placement : placements) {
                session.addPlacement(placement);
            }

            // 5. Обновляем статус
            session.updateStatus(ru.enums.SessionStatus.READY_FOR_EDIT, user);
            session = sessionRepo.save(session);

            // 6. Публикуем событие
            eventPublisher.publishEvent(new ru.events.ScheduleGeneratedEvent(session.getId(), placements));

            log.info("✅ Расписание сгенерировано: sessionId={}, placementsCount={}",
                    session.getId(), placements.size());

            return session;

        } catch (Exception e) {
            session.updateStatus(ru.enums.SessionStatus.INITIALIZED, user);
            sessionRepo.save(session);
            throw new RuntimeException("Ошибка генерации расписания", e);
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
     * ✅ НОВЫЙ МЕТОД: Перенести занятие в сессии с optimistic lock.
     *
     * @param sessionId ID сессии
     * @param placementId ID размещения
     * @param newDate Новая дата
     * @param newSlot Новый временной слот
     * @param newAuditoriumIds Новые аудитории
     * @param expectedVersion Ожидаемая версия (для optimistic lock)
     * @param user Пользователь
     * @throws ObjectOptimisticLockingFailureException если version не совпадает
     */
    @Transactional
    public void moveLessonInSession(
            java.util.UUID sessionId,
            java.util.UUID placementId,
            java.time.LocalDate newDate,
            String newSlot,
            java.util.Set<Integer> newAuditoriumIds,
            Long expectedVersion,
            String user
    ) {
        // 1. Загружаем сессию
        ru.entity.write.ScheduleSession session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        // 2. Проверяем optimistic lock
        if (!session.getVersion().equals(expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(
                ru.entity.write.ScheduleSession.class,
                session.getId()
            );
        }

        // 3. Находим placement
        ru.entity.write.LessonPlacement placement = placementRepo.findById(placementId)
            .orElseThrow(() -> new RuntimeException("Placement not found"));

        // 4. Временно удаляем занятие из workspace
        // (TODO: загрузить workspace из snapshot или пересоздать)
        // workspace.removePlacement(lesson);

        // 5. Обновляем placement
        placement.updatePlacement(
            newDate,
            ru.enums.TimeSlotPair.valueOf(newSlot),
            new java.util.HashSet<>(),
            user
        );

        // 6. Добавляем новое размещение
        // (TODO: workspace.forcePlacement(lesson, newCell, newAuditoriums))

        // 7. Сохраняем
        placementRepo.save(placement);

        // 8. ✅ Публикуем событие для синхронизации Query Side
        eventPublisher.publishEvent(new ru.events.PlacementChangedEvent(sessionId, placementId, placement));

        log.info("✅ Занятие перенесено: placementId={}, newDate={}, newSlot={}",
                placementId, newDate, newSlot);
    }

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
            String user
    ) {
        List<ru.entity.write.LessonPlacement> placements = new java.util.ArrayList<>();

        // Загружаем все Assignment для быстрого поиска
        List<Assignment> allAssignments = getAllAssignments();

        // Создаём мапу для быстрого поиска: (curriculumSlotId + studyStreamId) → Assignment
        // Это критически важно! Один curriculumSlot может иметь несколько assignments для разных групп.
        class AssignmentKey {
            Integer curriculumSlotId;
            Integer studyStreamId;
            AssignmentKey(Integer csId, Integer ssId) { this.curriculumSlotId = csId; this.studyStreamId = ssId; }
            @Override public boolean equals(Object o) {
                if (!(o instanceof AssignmentKey)) return false;
                AssignmentKey k = (AssignmentKey) o;
                return curriculumSlotId.equals(k.curriculumSlotId) && studyStreamId.equals(k.studyStreamId);
            }
            @Override public int hashCode() { return java.util.Objects.hash(curriculumSlotId, studyStreamId); }
        }

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

                // ✅ Создаём Placement (один Lesson → один Placement)
                ru.entity.write.LessonPlacement placement = new ru.entity.write.LessonPlacement(
                    assignment,
                    cell.getDate(),
                    cell.getTimeSlotPair(),
                    session,
                    user
                );

                // Добавляем аудитории
                if (lesson.getAssignedAuditoriums() != null) {
                    placement.getAssignedAuditoriums().addAll(lesson.getAssignedAuditoriums());
                }

                placements.add(placement);
            }
        }

        log.info("✅ Извлечено {} placements из workspace", placements.size());
        return placements;
    }

    /**
     * Вспомогательный метод: Получить все Assignment из БД.
     */
    private List<Assignment> getAllAssignments() {
        return assignmentService.getAllEntities();
    }
}
