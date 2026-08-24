package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.studyPeriod.PeriodSignatureDto;
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

    /**
     * PUT /api/study-periods/{id}/signature
     *
     * <p>Подпись под расписанием периода: должность, регалии и ФИО того, кто его подписывает.
     * Отсюда её берёт выгрузка в Excel — один подписант на все листы периода.</p>
     *
     * <p>Отдельный эндпоинт, а не поля в общем {@code PUT /{id}}: подпись правят перед выгрузкой, и
     * ради неё не нужно присылать даты и тип периода. Выгрузка остаётся чистым чтением — она подпись
     * не сохраняет, а только читает сохранённую.</p>
     */
    @PutMapping("/{id}/signature")
    public ResponseEntity<StudyPeriodDto> updateSignature(@PathVariable Integer id,
                                                          @RequestBody PeriodSignatureDto dto) {
        return ResponseEntity.ok(studyPeriodService.updateSignature(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        studyPeriodService.deleteStudyPeriod(id);
        return ResponseEntity.noContent().build();
    }
}