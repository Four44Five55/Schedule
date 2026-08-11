package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.dto.constraint.GroupConstraintCreateDto;
import ru.dto.constraint.GroupConstraintDto;
import ru.entity.Group;
import ru.entity.constraints.GroupConstraint;
import ru.entity.dictionary.KindOfConstraint;
import ru.repository.dictionary.KindOfConstraintRepository;
import ru.repository.constraints.GroupConstraintRepository;
import ru.services.GroupService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/group-constraints")
@RequiredArgsConstructor
public class GroupConstraintController {
    private final GroupConstraintRepository repository;
    private final KindOfConstraintRepository kindRepository;
    private final GroupService groupService;
    @GetMapping
    public ResponseEntity<List<GroupConstraintDto>> getAll() {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @GetMapping("/by-group/{groupId}")
    public ResponseEntity<List<GroupConstraintDto>> getByGroup(@PathVariable Integer groupId) {
        return ResponseEntity.ok(
                repository.findAll().stream()
                        .filter(c -> c.getGroup().getId().equals(groupId))
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
    @PostMapping
    public ResponseEntity<GroupConstraintDto> create(@Valid @RequestBody GroupConstraintCreateDto dto) {
        Group group = groupService.getEntityById(dto.groupId());
        GroupConstraint entity = new GroupConstraint();
        entity.setGroup(group);
        entity.setKindOfConstraint(resolveKind(dto.kindOfConstraint()));
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setDescription(dto.description());
        entity.setTimeSlot(dto.timeSlot());
        GroupConstraint saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    private GroupConstraintDto toDto(GroupConstraint c) {
        return new GroupConstraintDto(
                c.getId(),
                c.getGroup().getId(),
                c.getGroup().getName(),
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
