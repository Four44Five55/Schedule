package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.assignment.AssignmentCreateDto;
import ru.dto.assignment.AssignmentDto;
import ru.dto.assignment.AssignmentUpdateDto;
import ru.dto.assignment.RemoveAssignmentsImpactDto;
import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.mapper.AssignmentMapper;
import ru.repository.AssignmentRepository;
import ru.repository.read.ScheduleViewRepository;
import ru.repository.write.LessonPlacementRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для управления "Назначениями" (Assignments).
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CurriculumSlotService curriculumSlotService;
    private final StudyStreamService studyStreamService;
    private final EducatorService educatorService;
    private final AssignmentMapper assignmentMapper;
    private final LessonPlacementRepository placementRepository;
    private final ScheduleViewRepository scheduleViewRepository;

    @Transactional
    public List<AssignmentDto> createAssignments(AssignmentCreateDto createDto) {
        // 1. Находим родительский слот через его сервис
        CurriculumSlot slot = curriculumSlotService.getEntityById(createDto.curriculumSlotId());

        List<Assignment> createdAssignments = new ArrayList<>();

        for (AssignmentCreateDto.AssignmentDetail detail : createDto.assignments()) {
            // 2. Находим связанные сущности через их сервисы
            StudyStream stream = studyStreamService.getEntityById(detail.studyStreamId());
            List<Educator> educators = educatorService.getAllEntitiesByIds(detail.educatorIds());

            // 3. Создаём и сохраняем (сборка вынесена для переиспользования в applyToCourse)
            createdAssignments.add(assignmentRepository.save(buildAssignment(slot, stream, educators)));
        }

        return assignmentMapper.toDtoList(createdAssignments);
    }

    /**
     * Назначает поток + преподавателей на ВСЕ занятия курса. Удобство для типового
     * случая «один преподаватель ведёт поток через весь курс»: проставить разом,
     * а исключения потом править точечно.
     *
     * <p>Политика для уже назначенных на этот поток слотов: {@code overwrite=false}
     * (по умолчанию) — пропускаем (ручные исключения не трогаем, операция идемпотентна);
     * {@code overwrite=true} — заменяем состав преподавателей существующего назначения
     * (та же строка, ссылки не рвутся). Материализуем по слотам — отдельной
     * «курс-уровневой» сущности назначения нет.</p>
     *
     * <p>Охват: {@code slotIds} {@code null}/пусто → все слоты курса (прежнее поведение);
     * иначе — только слоты курса из этого набора (выбор по видам/конкретным занятиям
     * разворачивается во фронте). Фильтр по {@code courseId} уже отсекает чужие слоты —
     * пересечение с {@code slotIds} лишь сужает.</p>
     */
    @Transactional
    public List<AssignmentDto> applyToCourse(Integer courseId, Integer studyStreamId,
                                             List<Integer> educatorIds, boolean overwrite,
                                             List<Integer> slotIds) {
        StudyStream stream = studyStreamService.getEntityById(studyStreamId);
        List<Educator> educators = educatorService.getAllEntitiesByIds(educatorIds);
        List<CurriculumSlot> slots = curriculumSlotService.getEntitiesByCourseId(courseId);

        if (slotIds != null && !slotIds.isEmpty()) {
            Set<Integer> wanted = new HashSet<>(slotIds);
            slots = slots.stream().filter(s -> wanted.contains(s.getId())).toList();
        }

        // Уже назначенные на этот поток слоты курса — один запрос, без N+1.
        Map<Integer, Assignment> existingBySlot = assignmentRepository.findAllByCourseIdWithDetails(courseId).stream()
                .filter(a -> a.getStudyStream().getId().equals(studyStreamId))
                .collect(Collectors.toMap(a -> a.getCurriculumSlot().getId(), a -> a));

        List<Assignment> affected = new ArrayList<>();
        for (CurriculumSlot slot : slots) {
            Assignment existing = existingBySlot.get(slot.getId());
            if (existing == null) {
                affected.add(assignmentRepository.save(buildAssignment(slot, stream, educators)));
            } else if (overwrite) {
                existing.setEducators(new HashSet<>(educators));
                affected.add(assignmentRepository.save(existing));
            }
            // overwrite=false и назначение уже есть → SKIP
        }
        return assignmentMapper.toDtoList(affected);
    }

    /**
     * Массовое снятие «однотипных» назначений — зеркало {@link #applyToCourse}. Удаляет
     * назначения курса с тем же потоком И тем же составом преподавателей, что у варианта,
     * в пределах охвата {@code slotIds} (пусто → все слоты курса).
     *
     * <p>Каскад write-стороны (размещения) выполняет БД по FK. Read-модель
     * {@code schedule_view} чистим синхронно в этой же транзакции (см. {@link #purge}).</p>
     *
     * @return число удалённых назначений
     */
    @Transactional
    public int removeFromCourse(Integer courseId, Integer studyStreamId,
                                List<Integer> educatorIds, List<Integer> slotIds) {
        List<Assignment> matched = matchHomogeneous(courseId, studyStreamId, educatorIds, slotIds);
        purge(matched);
        return matched.size();
    }

    /** Предпросмотр последствий: сколько назначений подпадёт и сколько среди них размещено. */
    @Transactional(readOnly = true)
    public RemoveAssignmentsImpactDto removeImpact(Integer courseId, Integer studyStreamId,
                                                   List<Integer> educatorIds, List<Integer> slotIds) {
        List<Assignment> matched = matchHomogeneous(courseId, studyStreamId, educatorIds, slotIds);
        List<Integer> ids = matched.stream().map(Assignment::getId).toList();
        long placed = ids.isEmpty() ? 0L : placementRepository.findIdsByAssignmentIdIn(ids).size();
        return new RemoveAssignmentsImpactDto(matched.size(), placed);
    }

    /**
     * Назначения курса, «однотипные» выбранному варианту: тот же поток, тот же состав
     * преподавателей (сравнение множеств), в пределах охвата слотов.
     */
    private List<Assignment> matchHomogeneous(Integer courseId, Integer studyStreamId,
                                              List<Integer> educatorIds, List<Integer> slotIds) {
        Set<Integer> wantedEducators = educatorIds == null ? Set.of() : new HashSet<>(educatorIds);
        Set<Integer> scope = (slotIds == null || slotIds.isEmpty()) ? null : new HashSet<>(slotIds);

        return assignmentRepository.findAllByCourseIdWithDetails(courseId).stream()
                .filter(a -> a.getStudyStream().getId().equals(studyStreamId))
                .filter(a -> scope == null || scope.contains(a.getCurriculumSlot().getId()))
                .filter(a -> a.getEducators().stream().map(Educator::getId)
                        .collect(Collectors.toSet()).equals(wantedEducators))
                .toList();
    }

    /**
     * Удалить набор назначений, синхронно вычистив read-модель. Общий примитив для
     * массового и точечного удаления, чтобы {@code schedule_view} не оставляла «призраков»
     * (у неё нет FK на {@code lesson_placement}, поэтому FK-каскад БД её не трогает).
     */
    private void purge(List<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return;
        }
        List<Integer> ids = assignments.stream().map(Assignment::getId).toList();
        List<UUID> placementIds = placementRepository.findIdsByAssignmentIdIn(ids);
        if (!placementIds.isEmpty()) {
            scheduleViewRepository.deleteByPlacementIdIn(placementIds);
        }
        assignmentRepository.deleteAllById(ids);
    }

    /** Сборка сущности назначения из уже разрешённых связей (DRY для create/applyToCourse). */
    private Assignment buildAssignment(CurriculumSlot slot, StudyStream stream, List<Educator> educators) {
        Assignment newAssignment = new Assignment();
        newAssignment.setCurriculumSlot(slot);
        newAssignment.setStudyStream(stream);
        newAssignment.setEducators(new HashSet<>(educators));
        return newAssignment;
    }

    @Transactional
    public AssignmentDto updateAssignment(Integer assignmentId, AssignmentUpdateDto updateDto) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден."));

        // Находим новые связанные сущности через сервисы
        StudyStream stream = studyStreamService.getEntityById(updateDto.studyStreamId());
        List<Educator> educators = educatorService.getAllEntitiesByIds(updateDto.educatorIds());

        // Обновляем поля
        assignment.setStudyStream(stream);
        assignment.setEducators(new HashSet<>(educators));

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return assignmentMapper.toDto(updatedAssignment);
    }

    @Transactional
    public void deleteAssignment(Integer assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден."));
        // Через общий примитив — чтобы точечное удаление тоже чистило read-модель.
        purge(List.of(assignment));
    }

    @Transactional(readOnly = true)
    public Optional<AssignmentDto> findById(Integer assignmentId) {
        return assignmentRepository.findById(assignmentId).map(assignmentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findAllDtosByCourseId(Integer courseId) {
        List<Assignment> assignments = assignmentRepository.findAllByCourseIdWithDetails(courseId);
        return assignmentMapper.toDtoList(assignments);
    }
    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    @Transactional(readOnly = true)
    public List<Assignment> getAllEntitiesByCourseId(Integer courseId) {
        return assignmentRepository.findAllByCourseIdWithDetails(courseId);
    }

    @Transactional(readOnly = true)
    public Assignment getEntityById(Integer id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + id + " не найден."));
    }

    @Transactional(readOnly = true)
    public List<Assignment> getAllEntities() {
        return assignmentRepository.findAll();
    }
}
