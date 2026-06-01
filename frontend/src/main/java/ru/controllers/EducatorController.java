package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.educator.EducatorCreateDto;
import ru.dto.educator.EducatorDto;
import ru.dto.educator.EducatorUpdateDto;
import ru.services.EducatorService;

import java.util.List;

@RestController
@RequestMapping("/api/educators")
@RequiredArgsConstructor
public class EducatorController {

    private final EducatorService educatorService;

    @GetMapping
    public ResponseEntity<List<EducatorDto>> getAll() {
        return ResponseEntity.ok(educatorService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EducatorDto> getById(@PathVariable Integer id) {
        return educatorService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EducatorDto> create(@Valid @RequestBody EducatorCreateDto dto) {
        EducatorDto created = educatorService.createEducator(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EducatorDto> update(@PathVariable Integer id, @Valid @RequestBody EducatorUpdateDto dto) {
        EducatorDto updated = educatorService.updateEducator(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        educatorService.deleteEducator(id);
        return ResponseEntity.noContent().build();
    }
}
