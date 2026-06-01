package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.entity.constraints.GroupConstraint;
import ru.repository.constraints.GroupConstraintRepository;

import java.util.List;

@RestController
@RequestMapping("/api/group-constraints")
@RequiredArgsConstructor
public class GroupConstraintController {

    private final GroupConstraintRepository repository;

    @GetMapping
    public ResponseEntity<List<GroupConstraint>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/by-group/{groupId}")
    public ResponseEntity<List<GroupConstraint>> getByGroup(@PathVariable Integer groupId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getGroup().getId().equals(groupId))
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<GroupConstraint> create(@Valid @RequestBody GroupConstraint constraint) {
        GroupConstraint created = repository.save(constraint);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
