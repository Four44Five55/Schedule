package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.themeLesson.ThemeLessonCreateDto;
import ru.dto.themeLesson.ThemeLessonDto;
import ru.dto.themeLesson.ThemeLessonUpdateDto;
import ru.services.ThemeLessonService;

import java.util.List;

@RestController
@RequestMapping("/api/theme-lessons")
@RequiredArgsConstructor
public class ThemeLessonController {

    private final ThemeLessonService themeLessonService;

    @GetMapping("/by-discipline/{disciplineId}")
    public ResponseEntity<List<ThemeLessonDto>> getByDiscipline(@PathVariable Integer disciplineId) {
        return ResponseEntity.ok(themeLessonService.findAllByDisciplineId(disciplineId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThemeLessonDto> getById(@PathVariable Integer id) {
        return themeLessonService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ThemeLessonDto> create(@Valid @RequestBody ThemeLessonCreateDto dto) {
        ThemeLessonDto created = themeLessonService.createTheme(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThemeLessonDto> update(@PathVariable Integer id, @Valid @RequestBody ThemeLessonUpdateDto dto) {
        ThemeLessonDto updated = themeLessonService.updateTheme(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        themeLessonService.deleteTheme(id);
        return ResponseEntity.noContent().build();
    }
}
