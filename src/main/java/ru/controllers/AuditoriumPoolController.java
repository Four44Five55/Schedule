package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.auditoriumPool.AuditoriumPoolCreateDto;
import ru.dto.auditoriumPool.AuditoriumPoolDto;
import ru.dto.auditoriumPool.AuditoriumPoolUpdateDto;
import ru.services.AuditoriumPoolService;
import java.util.List;
@RestController
@RequestMapping("/api/auditorium-pools")
@RequiredArgsConstructor
public class AuditoriumPoolController {
    private final AuditoriumPoolService auditoriumPoolService;
    @GetMapping
    public ResponseEntity<List<AuditoriumPoolDto>> getAll() {
        return ResponseEntity.ok(auditoriumPoolService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<AuditoriumPoolDto> getById(@PathVariable Integer id) {
        return auditoriumPoolService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<AuditoriumPoolDto> create(@Valid @RequestBody AuditoriumPoolCreateDto dto) {
        AuditoriumPoolDto created = auditoriumPoolService.createPool(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<AuditoriumPoolDto> update(@PathVariable Integer id, @Valid @RequestBody AuditoriumPoolUpdateDto dto) {
        AuditoriumPoolDto updated = auditoriumPoolService.updatePool(id, dto);
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        auditoriumPoolService.deletePool(id);
        return ResponseEntity.noContent().build();
    }
}
