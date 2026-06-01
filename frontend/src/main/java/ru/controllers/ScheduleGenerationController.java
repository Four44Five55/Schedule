package ru.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.entity.Lesson;
import ru.services.ExcelExportService;
import ru.services.ScheduleGenerationService;
import ru.services.solver.ScheduleWorkspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleGenerationController {

    private final ScheduleGenerationService generationService;

    /**
     * Запускает генерацию расписания для одного курса.
     *
     * @param courseId ID курса (DisciplineCourse)
     * @return Объект со статистикой генерации
     */
    @PostMapping("/generate/{courseId}")
    public ResponseEntity<Map<String, Object>> generateForCourse(@PathVariable Integer courseId) {
        ScheduleWorkspace workspace = generationService.generateForCourse(courseId);
        return ResponseEntity.ok(buildResponse(workspace));
    }

    /**
     * Запускает генерацию расписания для списка курсов.
     *
     * @param request DTO с IDs курсов
     * @return Объект со статистикой генерации
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateForCourses(@RequestBody List<Integer> courseIds) {
        ScheduleWorkspace workspace = generationService.generateForCourseList(courseIds);
        return ResponseEntity.ok(buildResponse(workspace));
    }

    /**
     * Возвращает результат генерации в виде плоского списка размещённых занятий.
     *
     * @param courseId ID курса
     * @return Список занятий с датами, парами, аудиториями и преподавателями
     */
    @GetMapping("/result/{courseId}")
    public ResponseEntity<List<Lesson>> getResult(@PathVariable Integer courseId) {
        ScheduleWorkspace workspace = generationService.generateForCourse(courseId);
        // Возвращаем все размещённые занятия (извините за двойную генерацию, это мок)
        List<Lesson> lessons = java.util.List.of();
        return ResponseEntity.ok(lessons);
    }

    private Map<String, Object> buildResponse(ScheduleWorkspace workspace) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "generated");
        response.put("startDate", workspace.getGrid().getStartDate().toString());
        response.put("endDate", workspace.getGrid().getEndDate().toString());
        response.put("gridSize", workspace.getGrid().getGridMap().size());
        return response;
    }
}
