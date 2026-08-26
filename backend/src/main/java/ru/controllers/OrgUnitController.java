package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.orgUnit.OrgUnitCreateDto;
import ru.dto.orgUnit.OrgUnitDeletionImpactDto;
import ru.dto.orgUnit.OrgUnitDto;
import ru.dto.orgUnit.OrgUnitScopeDto;
import ru.dto.orgUnit.OrgUnitUpdateDto;
import ru.services.orgunit.OrgUnitScopeResolver;
import ru.services.orgunit.OrgUnitService;

import java.util.List;

/**
 * Подразделения организации (факультеты, кафедры, отделы).
 *
 * <p>Отдаёт дерево <b>плоским списком</b>: подразделений десятки, а форма дерева — презентация,
 * её собирает фронт. Разрешение «все преподаватели/группы подразделения с учётом вложенности»
 * останется за бэком — это вход для выборок, а не оформление (появится вместе с фильтрами).</p>
 *
 * <p>Ошибки не трактуются здесь: доменные исключения едут в {@link ApiExceptionHandler} —
 * недопустимый родитель ({@code RuleViolationException}) становится 400, тёзка и ссылающиеся
 * связи ({@code DuplicateException} / {@code InUseException}) — 409, отсутствующее
 * подразделение ({@code NotFoundException}) — 404.</p>
 */
@RestController
@RequestMapping("/api/org-units")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;
    private final OrgUnitScopeResolver orgUnitScopeResolver;

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
    public ResponseEntity<OrgUnitDto> create(@Valid @RequestBody OrgUnitCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orgUnitService.create(dto));
    }

    /**
     * Правка подразделения, включая перенос в другого родителя.
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrgUnitDto> update(@PathVariable Integer id, @Valid @RequestBody OrgUnitUpdateDto dto) {
        return ResponseEntity.ok(orgUnitService.update(id, dto));
    }

    /**
     * Охват подразделения: id вложенных подразделений, их преподавателей и групп.
     *
     * <p>Вход для фильтров «расписание кафедры», а не отчёт: множества id ложатся в
     * {@code IN (...)} по существующим индексам. Счётчики по ветке выводятся размером множества
     * — отдельного эндпоинта для них не будет, чтобы одно число не имело двух источников.</p>
     *
     * <p>В отличие от {@code GET /api/org-units}, вложенность разворачивает <b>бэк</b>: форма
     * дерева — презентация и собирается фронтом, а охват — вход для выборок, и его владелец
     * один ({@code OrgUnitScopeResolver}).</p>
     */
    @GetMapping("/{id}/scope")
    public ResponseEntity<OrgUnitScopeDto> scope(@PathVariable Integer id) {
        return ResponseEntity.ok(orgUnitScopeResolver.scopeOf(id));
    }

    /**
     * Предпросмотр последствий удаления: сколько вложенных подразделений, преподавателей и групп
     * ссылается на это подразделение. Любая ссылка делает удаление невозможным (RESTRICT).
     */
    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<OrgUnitDeletionImpactDto> deleteImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(orgUnitService.deleteImpact(id));
    }

    /**
     * Удаление. На подразделение ссылаются люди, группы и дочерние узлы — БД удалить не даст
     * (RESTRICT), поэтому сервис проверяет заранее и бросает {@code InUseException} (409).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        orgUnitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
