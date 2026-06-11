package ru.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.moveLesson.MoveOptionDto;
import ru.dto.moveLesson.MoveSuggestionRequest;
import ru.services.MoveLessonSuggestionService;
import ru.services.ScheduleGenerationService;
import ru.services.solver.ScheduleWorkspace;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleMoveController {

    private final MoveLessonSuggestionService moveService;
    private final ScheduleGenerationService generationService;

    @PostMapping("/find-move-options")
    public ResponseEntity<List<MoveOptionDto>> findOptions(@RequestBody MoveSuggestionRequest request) {
        // В реальной жизни Workspace должен храниться в кэше сессии или пересобираться
        // Для примера создаем временный на основе ID курсов
        ScheduleWorkspace workspace = generationService.getWorkspace();

        List<MoveOptionDto> options = moveService.findMoveSuggestions(workspace, request);
        return ResponseEntity.ok(options);
    }
}
