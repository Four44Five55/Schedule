package ru.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.moveLesson.MoveOptionDto;
import ru.dto.moveLesson.MoveSuggestionRequest;
import ru.services.MoveLessonSuggestionService;
import ru.services.WorkspaceRecreationService;
import ru.services.solver.ScheduleWorkspace;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleMoveController {

    private final MoveLessonSuggestionService moveService;
    private final WorkspaceRecreationService workspaceRecreationService;

    @PostMapping("/find-move-options")
    public ResponseEntity<List<MoveOptionDto>> findOptions(@RequestBody MoveSuggestionRequest request) {
        log.info("Поиск вариантов для переноса: sessionId={}, lessonId={}",
                request.sessionId(), request.lessonId());

        try {
            // 1. Пересоздаем workspace из сессии
            ScheduleWorkspace workspace = workspaceRecreationService.recreateWorkspaceFromSession(
                request.sessionId()
            );

            // 2. Ищем варианты переноса
            List<MoveOptionDto> options = moveService.findMoveSuggestions(workspace, request);

            log.info("✅ Найдено {} вариантов для переноса", options.size());
            return ResponseEntity.ok(options);

        } catch (Exception e) {
            log.error("❌ Ошибка при поиске вариантов: {}", e.getMessage(), e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
