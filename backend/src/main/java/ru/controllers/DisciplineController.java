package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.discipline.DisciplineCreateDto;
import ru.dto.discipline.DisciplineDto;
import ru.dto.discipline.DisciplineUpdateDto;
import ru.services.DisciplineService;
import java.util.List;
@RestController
@RequestMapping("/api/disciplines")
@RequiredArgsConstructor
public class DisciplineController {
    private final DisciplineService disciplineService;
    @GetMapping
    public ResponseEntity<List<DisciplineDto>> getAll() {
        return ResponseEntity.ok(disciplineService.findAllDisciplines());
    }
    @GetMapping("/{id}")
    public ResponseEntity<DisciplineDto> getById(@PathVariable Integer id) {
        return disciplineService.findDisciplineById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<DisciplineDto> create(@Valid @RequestBody DisciplineCreateDto dto) {
        DisciplineDto created = disciplineService.createDiscipline(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<DisciplineDto> update(@PathVariable Integer id, @Valid @RequestBody DisciplineUpdateDto dto) {
        DisciplineDto updated = disciplineService.updateDiscipline(id, dto);
        return ResponseEntity.ok(updated);
    }
    /**
     * Удаление дисциплины. Отказ приходит осмысленным 409 с текстом (у дисциплины есть курсы), а не
     * сырым 500 — как у подразделений, корпусов и локаций.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        disciplineService.deleteDiscipline(id);
        return ResponseEntity.noContent().build();
    }
}
