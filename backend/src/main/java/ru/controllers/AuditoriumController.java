package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.auditorium.AuditoriumCreateDto;
import ru.dto.auditorium.AuditoriumDeletionImpactDto;
import ru.dto.auditorium.AuditoriumDto;
import ru.dto.auditorium.AuditoriumUpdateDto;
import ru.services.AuditoriumService;
import java.util.List;
@RestController
@RequestMapping("/api/auditoriums")
@RequiredArgsConstructor
public class AuditoriumController {
    private final AuditoriumService auditoriumService;
    @GetMapping
    public ResponseEntity<List<AuditoriumDto>> getAll() {
        return ResponseEntity.ok(auditoriumService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<AuditoriumDto> getById(@PathVariable Integer id) {
        return auditoriumService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<AuditoriumDto> create(@Valid @RequestBody AuditoriumCreateDto dto) {
        AuditoriumDto created = auditoriumService.createAuditorium(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<AuditoriumDto> update(@PathVariable Integer id, @Valid @RequestBody AuditoriumUpdateDto dto) {
        AuditoriumDto updated = auditoriumService.updateAuditorium(id, dto);
        return ResponseEntity.ok(updated);
    }
    /**
     * Предпросмотр последствий удаления: занятия останутся без комнаты (в т.ч. закреплённые),
     * а если аудиторию требует учебный план — удалить её нельзя вовсе.
     */
    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<AuditoriumDeletionImpactDto> deleteImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(auditoriumService.deleteImpact(id));
    }

    /**
     * Удаление. Аудиторию, указанную в учебном плане, сервис удалить не даст: {@code InUseException}
     * едет в {@link ApiExceptionHandler} и становится 409 с числом ссылающихся.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        auditoriumService.deleteAuditorium(id);
        return ResponseEntity.noContent().build();
    }
}