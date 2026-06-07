package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.curriculumSlot.CurriculumSlotCreateDto;
import ru.dto.curriculumSlot.CurriculumSlotDto;
import ru.dto.curriculumSlot.CurriculumSlotUpdateDto;
import ru.mapper.CurriculumSlotMapper;
import ru.repository.CurriculumSlotRepository;
import ru.services.CurriculumSlotService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/curriculum-slots")
@RequiredArgsConstructor
public class CurriculumSlotController {

    private final CurriculumSlotService curriculumSlotService;

    @GetMapping
    public ResponseEntity<List<CurriculumSlotDto>> getAll() {
        return ResponseEntity.ok(curriculumSlotService.findAll());
    }

    @GetMapping("/by-course/{courseId}")
    public ResponseEntity<List<CurriculumSlotDto>> getByCourse(@PathVariable Integer courseId) {
        return ResponseEntity.ok(curriculumSlotService.findByCourseId(courseId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CurriculumSlotDto> getById(@PathVariable Integer id) {
        // Сервис выбросит исключение, если не найдено (обработка через ControllerAdvice)
        // Либо можно оставить Optional, если вы предпочитаете обработку в контроллере
        return ResponseEntity.ok(curriculumSlotService.getDtoById(id));
    }

    @PostMapping
    public ResponseEntity<CurriculumSlotDto> create(@Valid @RequestBody CurriculumSlotCreateDto dto) {
        CurriculumSlotDto created = curriculumSlotService.createSlot(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CurriculumSlotDto> update(@PathVariable Integer id, @Valid @RequestBody CurriculumSlotUpdateDto dto) {
        CurriculumSlotDto updated = curriculumSlotService.updateSlot(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        curriculumSlotService.deleteSlot(id);
        return ResponseEntity.noContent().build();
    }
}
