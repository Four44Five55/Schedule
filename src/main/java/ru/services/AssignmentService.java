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
import ru.repository.CurriculumSlotRepository;
import ru.repository.EducatorRepository;
import ru.repository.StudyStreamRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления "Назначениями" (Assignments).
 * Отвечает за логику создания, обновления и удаления связей между
 * слотом учебного плана, потоком и преподавателями.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final StudyStreamRepository studyStreamRepository;
    private final EducatorRepository educatorRepository;
    private final AssignmentMapper assignmentMapper;

    /**
     * Создает одно или несколько назначений для одного слота учебного плана.
     * Обрабатывает сценарии "один-ко-многим" и "деление группы".
     *
     * @param createDto DTO, содержащее ID слота и список деталей назначений.
     * @return Список DTO созданных назначений.
     */
    @Transactional
    public List<AssignmentDto> createAssignments(AssignmentCreateDto createDto) {
        // 1. Находим родительский слот, к которому все привязывается
        CurriculumSlot slot = curriculumSlotRepository.findById(createDto.curriculumSlotId())
                .orElseThrow(() -> new EntityNotFoundException("CurriculumSlot с id=" + createDto.curriculumSlotId() + " не найден."));

        List<Assignment> createdAssignments = new ArrayList<>();

        // 2. Итерируемся по каждому "детальному" назначению из DTO
        for (AssignmentCreateDto.AssignmentDetail detail : createDto.assignments()) {

            // 3. Находим связанные сущности по ID
            StudyStream stream = studyStreamRepository.findById(detail.studyStreamId())
                    .orElseThrow(() -> new EntityNotFoundException("StudyStream с id=" + detail.studyStreamId() + " не найден."));

            List<Educator> educators = educatorRepository.findAllById(detail.educatorIds());
            if (educators.size() != detail.educatorIds().size()) {
                throw new EntityNotFoundException("Один или несколько преподавателей из списка ID не найдены.");
            }

            // 4. Создаем и наполняем новую сущность Assignment
            Assignment newAssignment = new Assignment();
            newAssignment.setCurriculumSlot(slot);
            newAssignment.setStudyStream(stream);
            newAssignment.setEducators(new HashSet<>(educators));

            // 5. Сохраняем в БД
            createdAssignments.add(assignmentRepository.save(newAssignment));
        }

        // 6. Маппим результат в DTO и возвращаем
        return assignmentMapper.toDtoList(createdAssignments);
    }

    /**
     * Обновляет существующее назначение.
     *
     * @param assignmentId ID обновляемого назначения.
     * @param updateDto    DTO с новыми данными.
     * @return DTO обновленного назначения.
     */
    @Transactional
    public AssignmentDto updateAssignment(Integer assignmentId, AssignmentUpdateDto updateDto) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден."));

        // Находим новые связанные сущности
        StudyStream stream = studyStreamRepository.findById(updateDto.studyStreamId())
                .orElseThrow(() -> new EntityNotFoundException("StudyStream с id=" + updateDto.studyStreamId() + " не найден."));

        List<Educator> educators = educatorRepository.findAllById(updateDto.educatorIds());
        if (educators.size() != updateDto.educatorIds().size()) {
            throw new EntityNotFoundException("Один или несколько преподавателей из списка ID не найдены.");
        }

        // Обновляем поля
        assignment.setStudyStream(stream);
        assignment.setEducators(new HashSet<>(educators));

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return assignmentMapper.toDto(updatedAssignment);
    }

    /**
     * Удаляет назначение по ID.
     */
    @Transactional
    public void deleteAssignment(Integer assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new EntityNotFoundException("Assignment с id=" + assignmentId + " не найден.");
        }
        assignmentRepository.deleteById(assignmentId);
    }

    /**
     * Находит назначение по ID.
     */
    @Transactional(readOnly = true)
    public Optional<AssignmentDto> findById(Integer assignmentId) {
        return assignmentRepository.findById(assignmentId).map(assignmentMapper::toDto);
    }

    /**
     * Находит все назначения для указанного курса с полной информацией.
     */
    @Transactional(readOnly = true)
    public List<AssignmentDto> findAllByCourseId(Integer courseId) {
        List<Assignment> assignments = assignmentRepository.findAllByCourseIdWithDetails(courseId);
        return assignmentMapper.toDtoList(assignments);
    }
}
