package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.entity.constraints.AuditoriumConstraint;
import ru.repository.constraints.AuditoriumConstraintRepository;

import java.util.List;

@RestController
@RequestMapping("/api/auditorium-constraints")
@RequiredArgsConstructor
public class AuditoriumConstraintController {

    private final AuditoriumConstraintRepository repository;

    @GetMapping
    public ResponseEntity<List<AuditoriumConstraint>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/by-auditorium/{auditoriumId}")
    public ResponseEntity<List<AuditoriumConstraint>> getByAuditorium(@PathVariable Integer auditoriumId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getAuditorium().getId().equals(auditoriumId))
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<AuditoriumConstraint> create(@Valid @RequestBody AuditoriumConstraint constraint) {
        AuditoriumConstraint created = repository.save(constraint);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
