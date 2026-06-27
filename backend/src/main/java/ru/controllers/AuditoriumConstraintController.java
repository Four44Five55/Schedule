package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.constraint.AuditoriumConstraintCreateDto;
import ru.dto.constraint.AuditoriumConstraintDto;
import ru.entity.Auditorium;
import ru.entity.constraints.AuditoriumConstraint;
import ru.repository.constraints.AuditoriumConstraintRepository;
import ru.services.AuditoriumService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/auditorium-constraints")
@RequiredArgsConstructor
public class AuditoriumConstraintController {
    private final AuditoriumConstraintRepository repository;
    private final AuditoriumService auditoriumService;
    @GetMapping
    public ResponseEntity<List<AuditoriumConstraintDto>> getAll() {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @GetMapping("/by-auditorium/{auditoriumId}")
    public ResponseEntity<List<AuditoriumConstraintDto>> getByAuditorium(@PathVariable Integer auditoriumId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getAuditorium().getId().equals(auditoriumId))
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @PostMapping
    public ResponseEntity<AuditoriumConstraintDto> create(@Valid @RequestBody AuditoriumConstraintCreateDto dto) {
        Auditorium auditorium = auditoriumService.getEntityById(dto.auditoriumId());
        AuditoriumConstraint entity = new AuditoriumConstraint();
        entity.setAuditorium(auditorium);
        entity.setKindOfConstraint(dto.kindOfConstraint());
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setDescription(dto.description());
        AuditoriumConstraint saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    private AuditoriumConstraintDto toDto(AuditoriumConstraint c) {
        return new AuditoriumConstraintDto(
                c.getId(),
                c.getAuditorium().getId(),
                c.getAuditorium().getName(),
                c.getKindOfConstraint(),
                c.getKindOfConstraint().getAbbreviationName(),
                c.getKindOfConstraint().getFullName(),
                c.getStartDate(),
                c.getEndDate(),
                c.getDescription()
        );
    }
}
