package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.feature.FeatureCreateDto;
import ru.dto.feature.FeatureDto;
import ru.dto.feature.FeatureUpdateDto;
import ru.services.FeatureService;

import java.util.List;

@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping
    public ResponseEntity<List<FeatureDto>> getAll() {
        return ResponseEntity.ok(featureService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeatureDto> getById(@PathVariable Integer id) {
        return featureService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FeatureDto> create(@Valid @RequestBody FeatureCreateDto dto) {
        FeatureDto created = featureService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FeatureDto> update(@PathVariable Integer id, @Valid @RequestBody FeatureUpdateDto dto) {
        FeatureDto updated = featureService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        featureService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
