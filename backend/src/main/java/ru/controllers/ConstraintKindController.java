package ru.controllers;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.constraint.ConstraintKindDto;
import ru.dto.constraint.ConstraintKindFormDto;
import ru.services.constraints.ConstraintKindService;

import java.util.List;

/**
 * Справочник видов ограничений — командировка, отпуск, наряд, учения и что угодно ещё.
 *
 * <p>Раньше перечень жил Java-enum'ом и приезжал фронту через {@code /api/enums/all}. Он оттуда
 * убран: enum — это про значения, которыми ветвится код, а вид ограничения ничем не ветвится и
 * потому принадлежит пользователю (правило — в CLAUDE.md). Виды ЗАНЯТИЙ остаются в
 * {@code /api/enums}: от них зависит распределение.</p>
 */
@RestController
@RequestMapping("/api/constraint-kinds")
@RequiredArgsConstructor
public class ConstraintKindController {

    private final ConstraintKindService service;

    /** Все виды, включая погашенные: уже проставленное ограничение должно чем-то подписываться. */
    @GetMapping
    public ResponseEntity<List<ConstraintKindDto>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ConstraintKindFormDto form) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.create(form));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{code}")
    public ResponseEntity<?> update(@PathVariable String code,
                                    @Valid @RequestBody ConstraintKindFormDto form) {
        try {
            return ResponseEntity.ok(service.update(code, form));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Удаление. Вид, которым размечены ограничения, и вид, пришедший из кода, БД/сервис удалить
     * не дадут — отказ приходит осмысленным 409 с числом ссылающихся, а не сырой ошибкой FK.
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<?> delete(@PathVariable String code) {
        try {
            service.delete(code);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
