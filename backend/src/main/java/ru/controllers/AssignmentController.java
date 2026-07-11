package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.assignment.ApplyAssignmentToCourseDto;
import ru.dto.assignment.AssignmentCreateDto;
import ru.dto.assignment.AssignmentDto;
import ru.dto.assignment.AssignmentUpdateDto;
import ru.dto.assignment.RemoveAssignmentsFromCourseDto;
import ru.dto.assignment.RemoveAssignmentsImpactDto;
import ru.mapper.AssignmentMapper;
import ru.repository.AssignmentRepository;
import ru.services.AssignmentService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    private final AssignmentService assignmentService;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentMapper assignmentMapper;
    @GetMapping
    public ResponseEntity<List<AssignmentDto>> getAll() {
        return ResponseEntity.ok(
                assignmentRepository.findAll().stream()
                        .map(assignmentMapper::toDto)
                        .collect(Collectors.toList())
        );
    }
    @GetMapping("/by-course/{courseId}")
    public ResponseEntity<List<AssignmentDto>> getByCourse(@PathVariable Integer courseId) {
        return ResponseEntity.ok(assignmentService.findAllDtosByCourseId(courseId));
    }
    @GetMapping("/{id}")
    public ResponseEntity<AssignmentDto> getById(@PathVariable Integer id) {
        return assignmentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<List<AssignmentDto>> create(@Valid @RequestBody AssignmentCreateDto dto) {
        List<AssignmentDto> created = assignmentService.createAssignments(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    /**
     * Назначить поток+преподавателей на все занятия курса (типовой случай — один
     * преподаватель на весь курс). overwrite управляет уже назначенными слотами.
     */
    @PostMapping("/apply-to-course")
    public ResponseEntity<List<AssignmentDto>> applyToCourse(@Valid @RequestBody ApplyAssignmentToCourseDto dto) {
        List<AssignmentDto> result = assignmentService.applyToCourse(
                dto.courseId(), dto.studyStreamId(), dto.educatorIds(), dto.overwrite(), dto.slotIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    /**
     * Предпросмотр массового снятия «однотипных» назначений: сколько назначений подпадёт
     * под критерий (поток + состав преподавателей ∩ охват) и сколько среди них размещено.
     */
    @PostMapping("/remove-from-course/impact")
    public ResponseEntity<RemoveAssignmentsImpactDto> removeFromCourseImpact(
            @Valid @RequestBody RemoveAssignmentsFromCourseDto dto) {
        return ResponseEntity.ok(assignmentService.removeImpact(
                dto.courseId(), dto.studyStreamId(), dto.educatorIds(), dto.slotIds()));
    }

    /**
     * Массово снять «однотипные» назначения (зеркало apply-to-course): удаляет назначения
     * с тем же потоком и составом преподавателей в пределах выбранных занятий, чистит и
     * размещения (FK-каскад), и read-модель. Возвращает число удалённых.
     */
    @PostMapping("/remove-from-course")
    public ResponseEntity<Integer> removeFromCourse(@Valid @RequestBody RemoveAssignmentsFromCourseDto dto) {
        int removed = assignmentService.removeFromCourse(
                dto.courseId(), dto.studyStreamId(), dto.educatorIds(), dto.slotIds());
        return ResponseEntity.ok(removed);
    }

    /**
     * Предпросмотр последствий удаления одного назначения: сколько его занятий стоит в
     * расписании и сколько из них закреплено (замок). Размещения уносит FK-каскад, поэтому
     * ручная раскладка теряется без спроса — фронт показывает это в подтверждении.
     */
    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<RemoveAssignmentsImpactDto> deleteImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(assignmentService.deleteImpact(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssignmentDto> update(@PathVariable Integer id, @Valid @RequestBody AssignmentUpdateDto dto) {
        AssignmentDto updated = assignmentService.updateAssignment(id, dto);
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}