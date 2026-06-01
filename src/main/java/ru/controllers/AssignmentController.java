package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.assignment.AssignmentCreateDto;
import ru.dto.assignment.AssignmentDto;
import ru.dto.assignment.AssignmentUpdateDto;
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