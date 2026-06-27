package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.constraint.EducatorConstraintCreateDto;
import ru.dto.constraint.EducatorConstraintDto;
import ru.entity.Educator;
import ru.entity.constraints.EducatorConstraint;
import ru.enums.KindOfConstraints;
import ru.repository.constraints.EducatorConstraintRepository;
import ru.services.EducatorService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/educator-constraints")
@RequiredArgsConstructor
public class EducatorConstraintController {
    private final EducatorConstraintRepository repository;
    private final EducatorService educatorService;
    @GetMapping
    public ResponseEntity<List<EducatorConstraintDto>> getAll() {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @GetMapping("/by-educator/{educatorId}")
    public ResponseEntity<List<EducatorConstraintDto>> getByEducator(@PathVariable Integer educatorId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getEducator().getId().equals(educatorId))
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @PostMapping
    public ResponseEntity<EducatorConstraintDto> create(@Valid @RequestBody EducatorConstraintCreateDto dto) {
        Educator educator = educatorService.getEntityById(dto.educatorId());
        EducatorConstraint entity = new EducatorConstraint();
        entity.setEducator(educator);
        entity.setKindOfConstraint(dto.kindOfConstraint());
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setDescription(dto.description());
        EducatorConstraint saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    private EducatorConstraintDto toDto(EducatorConstraint c) {
        return new EducatorConstraintDto(
                c.getId(),
                c.getEducator().getId(),
                c.getEducator().getName(),
                c.getKindOfConstraint(),
                c.getKindOfConstraint().getAbbreviationName(),
                c.getKindOfConstraint().getFullName(),
                c.getStartDate(),
                c.getEndDate(),
                c.getDescription()
        );
    }
}