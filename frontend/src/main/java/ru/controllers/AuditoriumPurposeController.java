package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.auditoriumPurpose.AuditoriumPurposeCreateDto;
import ru.dto.auditoriumPurpose.AuditoriumPurposeDto;
import ru.dto.auditoriumPurpose.AuditoriumPurposeUpdateDto;
import ru.services.AuditoriumPurposeService;

import java.util.List;

@RestController
@RequestMapping("/api/auditorium-purposes")
@RequiredArgsConstructor
public class AuditoriumPurposeController {

    private final AuditoriumPurposeService auditoriumPurposeService;

    @GetMapping
    public ResponseEntity<List<AuditoriumPurposeDto>> getAll() {
        return ResponseEntity.ok(auditoriumPurposeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditoriumPurposeDto> getById(@PathVariable Integer id) {
        return auditoriumPurposeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AuditoriumPurposeDto> create(@Valid @RequestBody AuditoriumPurposeCreateDto dto) {
        AuditoriumPurposeDto created = auditoriumPurposeService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditoriumPurposeDto> update(@PathVariable Integer id, @Valid @RequestBody AuditoriumPurposeUpdateDto dto) {
        AuditoriumPurposeDto updated = auditoriumPurposeService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        auditoriumPurposeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
