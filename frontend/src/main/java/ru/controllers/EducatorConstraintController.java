package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.entity.constraints.EducatorConstraint;
import ru.repository.constraints.EducatorConstraintRepository;

import java.util.List;

@RestController
@RequestMapping("/api/educator-constraints")
@RequiredArgsConstructor
public class EducatorConstraintController {

    private final EducatorConstraintRepository repository;

    @GetMapping
    public ResponseEntity<List<EducatorConstraint>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/by-educator/{educatorId}")
    public ResponseEntity<List<EducatorConstraint>> getByEducator(@PathVariable Integer educatorId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getEducator().getId().equals(educatorId))
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<EducatorConstraint> create(@Valid @RequestBody EducatorConstraint constraint) {
        EducatorConstraint created = repository.save(constraint);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
