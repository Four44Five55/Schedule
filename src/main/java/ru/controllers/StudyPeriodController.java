package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.studyPeriod.StudyPeriodCreateDto;
import ru.dto.studyPeriod.StudyPeriodDto;
import ru.dto.studyPeriod.StudyPeriodUpdateDto;
import ru.services.StudyPeriodService;
import java.util.List;
import java.util.Optional;
@RestController
@RequestMapping("/api/study-periods")
@RequiredArgsConstructor
public class StudyPeriodController {
    private final StudyPeriodService studyPeriodService;

    @GetMapping
    public ResponseEntity<List<StudyPeriodDto>> getAll() {
        return ResponseEntity.ok(studyPeriodService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudyPeriodDto> getById(@PathVariable Integer id) {
        return studyPeriodService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/study-periods/active
     *
     * <p>Получить активный учебный период (содержит сегодняшнюю дату).</p>
     *
     * @return Активный период или 404, если активного периода нет
     */
    @GetMapping("/active")
    public ResponseEntity<StudyPeriodDto> getActivePeriod() {
        Optional<StudyPeriodDto> activePeriod = studyPeriodService.findActivePeriod();
        return activePeriod
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<StudyPeriodDto> create(@Valid @RequestBody StudyPeriodCreateDto dto) {
        StudyPeriodDto created = studyPeriodService.createStudyPeriod(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudyPeriodDto> update(@PathVariable Integer id, @Valid @RequestBody StudyPeriodUpdateDto dto) {
        StudyPeriodDto updated = studyPeriodService.updateStudyPeriod(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        studyPeriodService.deleteStudyPeriod(id);
        return ResponseEntity.noContent().build();
    }
}