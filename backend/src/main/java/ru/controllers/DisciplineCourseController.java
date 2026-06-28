package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.disciplineCourse.CourseCloneRequestDto;
import ru.dto.disciplineCourse.DisciplineCourseCreateDto;
import ru.dto.disciplineCourse.DisciplineCourseDto;
import ru.dto.disciplineCourse.DisciplineCourseUpdateDto;
import ru.repository.DisciplineCourseRepository;
import ru.mapper.DisciplineCourseMapper;
import ru.services.CurriculumCloneService;
import ru.services.DisciplineCourseService;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/discipline-courses")
@RequiredArgsConstructor
public class DisciplineCourseController {
    private final DisciplineCourseService disciplineCourseService;
    private final CurriculumCloneService curriculumCloneService;
    private final DisciplineCourseRepository disciplineCourseRepository;
    private final DisciplineCourseMapper disciplineCourseMapper;
    /**
     * Список курсов. Если задан {@code studyPeriodId} — только курсы этого учебного
     * периода (для планировщика «по периоду»), иначе все курсы.
     */
    @GetMapping
    public ResponseEntity<List<DisciplineCourseDto>> getAll(
            @RequestParam(value = "studyPeriodId", required = false) Integer studyPeriodId) {
        if (studyPeriodId != null) {
            return ResponseEntity.ok(disciplineCourseService.findAllByStudyPeriod(studyPeriodId));
        }
        return ResponseEntity.ok(
                disciplineCourseRepository.findAll().stream()
                        .map(disciplineCourseMapper::toDto)
                        .collect(Collectors.toList())
        );
    }
    @GetMapping("/by-discipline/{disciplineId}")
    public ResponseEntity<List<DisciplineCourseDto>> getByDiscipline(@PathVariable Integer disciplineId) {
        return ResponseEntity.ok(disciplineCourseService.findAllCoursesByDiscipline(disciplineId));
    }
    @GetMapping("/{id}")
    public ResponseEntity<DisciplineCourseDto> getById(@PathVariable Integer id) {
        return disciplineCourseService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<DisciplineCourseDto> create(@Valid @RequestBody DisciplineCourseCreateDto dto) {
        DisciplineCourseDto created = disciplineCourseService.createCourse(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    /**
     * Клон учебного плана: глубокая копия выбранных курсов в целевой период. Атомарно —
     * дубль курса (discipline+semester) в целевом периоде откатывает всю операцию (409).
     * Глобального ControllerAdvice нет, поэтому статус маппим здесь (как в ScheduleCommandController).
     */
    @PostMapping("/clone")
    public ResponseEntity<?> clone(@Valid @RequestBody CourseCloneRequestDto dto) {
        try {
            List<DisciplineCourseDto> created = curriculumCloneService.cloneCourses(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
    @PutMapping("/{id}")
    public ResponseEntity<DisciplineCourseDto> update(@PathVariable Integer id, @Valid @RequestBody DisciplineCourseUpdateDto dto) {
        DisciplineCourseDto updated = disciplineCourseService.updateCourse(id, dto);
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        disciplineCourseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }
}
