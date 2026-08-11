package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.dto.constraint.EducatorConstraintCreateDto;
import ru.dto.constraint.EducatorConstraintDto;
import ru.entity.Educator;
import ru.entity.constraints.EducatorConstraint;
import ru.entity.dictionary.KindOfConstraint;
import ru.repository.dictionary.KindOfConstraintRepository;
import ru.repository.constraints.EducatorConstraintRepository;
import ru.services.EducatorService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/educator-constraints")
@RequiredArgsConstructor
public class EducatorConstraintController {
    private final EducatorConstraintRepository repository;
    private final KindOfConstraintRepository kindRepository;
    private final EducatorService educatorService;
    @GetMapping
    public ResponseEntity<List<EducatorConstraintDto>> getAll() {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @GetMapping("/by-educator/{educatorId}")
    public ResponseEntity<List<EducatorConstraintDto>> getByEducator(@PathVariable Integer educatorId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getEducator().getId().equals(educatorId))
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @PostMapping
    public ResponseEntity<EducatorConstraintDto> create(@Valid @RequestBody EducatorConstraintCreateDto dto) {
        Educator educator = educatorService.getEntityById(dto.educatorId());
        EducatorConstraint entity = new EducatorConstraint();
        entity.setEducator(educator);
        entity.setKindOfConstraint(resolveKind(dto.kindOfConstraint()));
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setDescription(dto.description());
        entity.setTimeSlot(dto.timeSlot());
        EducatorConstraint saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    private EducatorConstraintDto toDto(EducatorConstraint c) {
        return new EducatorConstraintDto(
                c.getId(),
                c.getEducator().getId(),
                c.getEducator().getName(),
                c.getKindOfConstraint().getCode(),
                c.getKindOfConstraint().getShortName(),
                c.getKindOfConstraint().getName(),
                c.getStartDate(),
                c.getEndDate(),
                c.getDescription(),
                c.getTimeSlot()
        );
    }

    /**
     * Код вида → строка справочника. Виды заводит пользователь, поэтому неизвестный код — это
     * ошибка запроса (фронт мог отстать от справочника), а не 500 от нарушения внешнего ключа.
     */
    private KindOfConstraint resolveKind(String code) {
        return kindRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Неизвестный вид ограничения: " + code));
    }
}