package ru.controllers;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.orgUnit.OrgUnitCreateDto;
import ru.dto.orgUnit.OrgUnitDeletionImpactDto;
import ru.dto.orgUnit.OrgUnitDto;
import ru.dto.orgUnit.OrgUnitUpdateDto;
import ru.services.orgunit.OrgUnitService;

import java.util.List;

/**
 * Подразделения организации (факультеты, кафедры, отделы).
 *
 * <p>Отдаёт дерево <b>плоским списком</b>: подразделений десятки, а форма дерева — презентация,
 * её собирает фронт. Разрешение «все преподаватели/группы подразделения с учётом вложенности»
 * останется за бэком — это вход для выборок, а не оформление (появится вместе с фильтрами).</p>
 *
 * <p>Ошибки трактуются локально, глобального {@code @ControllerAdvice} для master-данных в
 * проекте нет (он есть только на командной стороне расписания): недопустимый родитель — 400,
 * конфликт связей или тёзка — 409, отсутствующее подразделение — 404.</p>
 */
@RestController
@RequestMapping("/api/org-units")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    @GetMapping
    public ResponseEntity<List<OrgUnitDto>> getAll() {
        return ResponseEntity.ok(orgUnitService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrgUnitDto> getById(@PathVariable Integer id) {
        return orgUnitService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody OrgUnitCreateDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(orgUnitService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Правка подразделения, включая перенос в другого родителя.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody OrgUnitUpdateDto dto) {
        try {
            return ResponseEntity.ok(orgUnitService.update(id, dto));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Предпросмотр последствий удаления: сколько вложенных подразделений, преподавателей и групп
     * ссылается на это подразделение. Любая ссылка делает удаление невозможным (RESTRICT).
     */
    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<OrgUnitDeletionImpactDto> deleteImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(orgUnitService.deleteImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            orgUnitService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // На подразделение ссылаются люди/группы/дочерние узлы: БД удалить не даст (RESTRICT).
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
