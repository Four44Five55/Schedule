package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.assignment.AssignmentCreateDto;
import ru.dto.assignment.AssignmentDto;
import ru.dto.assignment.AssignmentUpdateDto;
import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.mapper.AssignmentMapper;
import ru.repository.AssignmentRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public List<AssignmentDto> createAssignments(AssignmentCreateDto createDto) {
        // 1. Находим родительский слот через его сервис
        CurriculumSlot slot = curriculumSlotService.findEntityById(createDto.curriculumSlotId());

        List<Assignment> createdAssignments = new ArrayList<>();

        for (AssignmentCreateDto.AssignmentDetail detail : createDto.assignments()) {
            // 2. Находим связанные сущности через их сервисы
            StudyStream stream = studyStreamService.findEntityById(detail.studyStreamId());
            List<Educator> educators = educatorService.findAllEntitiesByIds(detail.educatorIds());

            // 3. Создаем и наполняем новую сущность Assignment
            Assignment newAssignment = new Assignment();
            newAssignment.setCurriculumSlot(slot);
            newAssignment.setStudyStream(stream);
            newAssignment.setEducators(new HashSet<>(educators));

            createdAssignments.add(assignmentRepository.save(newAssignment));
        }

        return assignmentMapper.toDtoList(createdAssignments);
    }

    @Transactional
    public AssignmentDto updateAssignment(Integer assignmentId, AssignmentUpdateDto updateDto) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден."));

        // Находим новые связанные сущности через сервисы
        StudyStream stream = studyStreamService.findEntityById(updateDto.studyStreamId());
        List<Educator> educators = educatorService.findAllEntitiesByIds(updateDto.educatorIds());

        // Обновляем поля
        assignment.setStudyStream(stream);
        assignment.setEducators(new HashSet<>(educators));

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return assignmentMapper.toDto(updatedAssignment);
    }

    // Остальные методы (delete, findById, findAll...) остаются без изменений,
    // так как они работают только со своим репозиторием.
    // ...
    @Transactional
    public void deleteAssignment(Integer assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден.");
        }
        assignmentRepository.deleteById(assignmentId);
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

    @Transactional(readOnly = true)
    public List<Assignment> findAllEntitiesByCourseId(Integer courseId) {
        return assignmentRepository.findAllByCourseIdWithDetails(courseId);
    }
}
