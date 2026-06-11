package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.studyStream.StudyStreamCreateDto;
import ru.dto.studyStream.StudyStreamDto;
import ru.dto.studyStream.StudyStreamUpdateDto;
import ru.services.StudyStreamService;
import java.util.List;
@RestController
@RequestMapping("/api/study-streams")
@RequiredArgsConstructor
public class StudyStreamController {
    private final StudyStreamService studyStreamService;
    @GetMapping
    public ResponseEntity<List<StudyStreamDto>> getAll() {
        return ResponseEntity.ok(studyStreamService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<StudyStreamDto> getById(@PathVariable Integer id) {
        return studyStreamService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<StudyStreamDto> create(@Valid @RequestBody StudyStreamCreateDto dto) {
        StudyStreamDto created = studyStreamService.createStudyStream(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<StudyStreamDto> update(@PathVariable Integer id, @Valid @RequestBody StudyStreamUpdateDto dto) {
        StudyStreamDto updated = studyStreamService.updateStudyStream(id, dto);
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        studyStreamService.deleteStudyStream(id);
        return ResponseEntity.noContent().build();
    }
}