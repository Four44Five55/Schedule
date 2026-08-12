package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.manualPlacement.UnplacedLessonDto;
import ru.utils.GroupNameComparator;
import ru.dto.moveLesson.MoveOptionDto;
import ru.entity.Assignment;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.entity.StudyPeriod;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.entity.logicSchema.ThemeLesson;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.KindOfStudy;
import ru.enums.PlacementSource;
import ru.enums.TimeSlotPair;
import ru.events.PlacementChangedEvent;
import ru.exceptions.LessonMoveConflictException;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;
import ru.services.factories.CellForLessonFactory;
import ru.services.session.ScheduleSessionGate;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ручная раскладка занятий (Фича 2, Фаза B): что ещё не размещено + установка/снятие.
 *
 * <p>«Неразмещённое» = {@link Assignment} выбранных курсов без {@link LessonPlacement}
 * в текущей сессии. Установка валидируется тем же доменным примитивом
 * {@link ScheduleWorkspace#findPlacementOption}, что генерация и перенос (ресурсы +
 * подбор аудитории на бэке); конфликт → {@link LessonMoveConflictException} → 409.
 * Ручное размещение — это {@code MANUAL}, {@code locked} placement: генератор позже
 * засеет его (Фаза 0) и разложит остальное вокруг.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPlacementService {

    private final AssignmentService assignmentService;
    private final LessonPlacementRepository placementRepo;
    private final ScheduleSessionRepository sessionRepo;
    private final ScheduleSessionGate sessionGate;
    private final StudyPeriodService studyPeriodService;
    private final WorkspaceRecreationService workspaceRecreationService;
    private final WorkspacePlacementSeeder placementSeeder;
    private final MoveLessonSuggestionService moveSuggestionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Неразмещённые занятия выбранных курсов для палитры.
     *
     * @param sessionId сессия (источник уже размещённых); если у неё нет размещений — все курсовые
     *                  назначения считаются неразмещёнными
     * @param courseIds выбранные курсы
     * @return список неразмещённых занятий (денормализовано для группировки на фронте)
     */
    @Transactional(readOnly = true)
    public List<UnplacedLessonDto> findUnplaced(UUID sessionId, List<Integer> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }

        Set<Integer> placedAssignmentIds = placementRepo.findBySessionId(sessionId).stream()
                .map(p -> p.getAssignment().getId())
                .collect(Collectors.toSet());

        // Назначения всех курсов одним запросом (раньше — по запросу на курс).
        var assignmentsByCourse = assignmentService.getAllEntitiesByCourseIds(courseIds);

        List<UnplacedLessonDto> result = new ArrayList<>();
        for (Integer courseId : courseIds) {
            for (Assignment a : assignmentsByCourse.getOrDefault(courseId, List.of())) {
                if (placedAssignmentIds.contains(a.getId())) {
                    continue;
                }
                result.add(toDto(a, courseId));
            }
        }
        return result;
    }

    /**
     * Доступные ячейки для установки занятия из палитры (тот же подбор, что и у переноса).
     *
     * @param sessionId     сессия (засев существующих размещений → занятость ресурсов)
     * @param assignmentId  что ставим
     * @param rootType      тип корневой сущности ('EDUCATOR' | 'GROUP' | 'AUDITORIUM')
     * @param rootId        id корневой сущности (через которую открыта сетка)
     * @param studyPeriodId период (рамки ячеек)
     * @return валидные {@code (date, slot)} для установки
     */
    @Transactional(readOnly = true)
    public List<MoveOptionDto> findPlacementOptions(UUID sessionId, Integer assignmentId,
                                                    String rootType, Integer rootId, Integer studyPeriodId) {
        StudyPeriod period = studyPeriodService.getEntityById(studyPeriodId);
        ScheduleWorkspace workspace = workspaceRecreationService
                .recreateWorkspaceForPeriod(sessionId, period.getStartDate(), period.getEndDate())
                .workspace();

        Assignment assignment = assignmentService.getEntityById(assignmentId);
        Lesson lesson = placementSeeder.buildLesson(assignment);

        return moveSuggestionService.findPlacementSuggestions(workspace, lesson, rootType, rootId);
    }

    /**
     * Ручная установка занятия в слот (создаёт {@code MANUAL}, {@code locked} placement).
     *
     * @param sessionId     сессия раскладки
     * @param assignmentId  что ставим
     * @param date          дата
     * @param slotName      пара (имя {@link TimeSlotPair})
     * @param studyPeriodId период (рамки кэша ячеек/валидации)
     * @param user          автор
     * @return сессия-владелец
     * @throws LessonMoveConflictException слот вне периода / ресурс занят / нет аудитории / уже размещено
     */
    @Transactional
    public ScheduleSession place(UUID sessionId, Integer assignmentId, LocalDate date,
                                 String slotName, Integer studyPeriodId, Long expectedVersion,
                                 String user) {
        // Сессия — через единую дверь: сверка версии + подъём поколения на коммите.
        ScheduleSession session = sessionGate.forWrite(sessionId, expectedVersion);

        // Один assignment — одно размещение в сессии (идемпотентность палитры).
        boolean alreadyPlaced = placementRepo.findBySessionId(sessionId).stream()
                .anyMatch(p -> p.getAssignment().getId().equals(assignmentId));
        if (alreadyPlaced) {
            throw new LessonMoveConflictException("занятие уже размещено в этой сессии");
        }

        // Workspace на рамках периода (важно для пустой сессии) + засев существующих размещений.
        StudyPeriod period = studyPeriodService.getEntityById(studyPeriodId);
        ScheduleWorkspace workspace = workspaceRecreationService
                .recreateWorkspaceForPeriod(sessionId, period.getStartDate(), period.getEndDate())
                .workspace();

        TimeSlotPair slot = TimeSlotPair.valueOf(slotName);
        CellForLesson cell = CellForLessonFactory.getCell(date, slot);
        if (cell == null) {
            throw new LessonMoveConflictException("выбранный слот вне планируемого периода");
        }

        Assignment assignment = assignmentService.getEntityById(assignmentId);
        Lesson lesson = placementSeeder.buildLesson(assignment);

        // Та же валидация, что и при генерации/переносе: ресурсы свободны + есть аудитория.
        // HONOR_WINDOWS: ставит человек, поэтому окно промежуточной аттестации пускает в себя
        // запланированную в сессию аттестацию (генерация окон не видит).
        PlacementOption option = workspace.findPlacementOption(lesson, cell,
                ScheduleWorkspace.ConstraintPolicy.HONOR_WINDOWS);
        if (!option.isPossible()) {
            throw new LessonMoveConflictException(option.failureReason());
        }

        LessonPlacement placement = new LessonPlacement(
                assignment, date, slot, session, user, PlacementSource.MANUAL, true);
        placement.getAssignedAuditoriums().addAll(option.assignedAuditoriums());
        placementRepo.save(placement);

        eventPublisher.publishEvent(new PlacementChangedEvent(
                session.getId(), placement.getId(), placement,
                PlacementChangedEvent.PlacementChangeType.CREATED));

        log.info("✋ Ручная установка: assignmentId={}, date={}, slot={}, аудиторий={}",
                assignmentId, date, slotName, option.assignedAuditoriums().size());

        return session;
    }

    /**
     * Снять размещение (вернуть занятие в палитру неразмещённых).
     *
     * @param placementId размещение
     * @param user        автор (для аудита/логов)
     * @return сессия-владелец
     */
    @Transactional
    public ScheduleSession remove(UUID placementId, Long expectedVersion, String user) {
        LessonPlacement placement = placementRepo.findById(placementId)
                .orElseThrow(() -> new IllegalArgumentException("Размещение не найдено: " + placementId));
        ScheduleSession session = sessionGate.forWriteOf(placement, expectedVersion);
        UUID id = placement.getId();

        placementRepo.delete(placement);
        eventPublisher.publishEvent(new PlacementChangedEvent(session.getId(), id)); // DELETED

        log.info("🗑️ Снято размещение: placementId={} (user={})", id, user);
        return session;
    }

    private UnplacedLessonDto toDto(Assignment a, Integer courseId) {
        CurriculumSlot slot = a.getCurriculumSlot();
        KindOfStudy kind = slot.getKindOfStudy();
        StudyStream stream = a.getStudyStream();
        ThemeLesson theme = slot.getThemeLesson();

        // Порядок групп потока не гарантирован (коллекция) — фиксируем по коду группы
        // (уровни через «/»), сортируя как единый список, чтобы id и имена оставались
        // согласованными по индексу.
        List<Group> groups = stream.getGroups().stream()
                .sorted(java.util.Comparator.comparing(Group::getName, GroupNameComparator.INSTANCE))
                .toList();

        return new UnplacedLessonDto(
                a.getId(),
                slot.getId(),
                courseId,
                slot.getDisciplineCourse().getDiscipline().getName(),
                slot.getDisciplineCourse().getDiscipline().getAbbreviation(),
                kind.name(),
                kind.getFullName(),
                kind.getAbbreviationName(),
                slot.getPosition(),
                theme != null ? theme.getThemeNumber() : null,
                theme != null ? theme.getTitle() : null,
                stream.getId(),
                stream.getName(),
                groups.stream().map(Group::getId).toList(),
                groups.stream().map(Group::getName).toList(),
                a.getEducators().stream().map(Educator::getId).toList(),
                a.getEducators().stream().map(Educator::getName).toList()
        );
    }
}
