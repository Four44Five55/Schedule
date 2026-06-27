package ru.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.moveLesson.ChainMoveSuggestionRequest;
import ru.dto.moveLesson.MoveOptionDto;
import ru.dto.moveLesson.MoveSuggestionRequest;
import ru.entity.Lesson;
import ru.services.LessonChainMoveService;
import ru.services.MoveLessonSuggestionService;
import ru.services.WorkspaceRecreationService;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleMoveController {

    private final MoveLessonSuggestionService moveService;
    private final LessonChainMoveService chainMoveService;
    private final WorkspaceRecreationService workspaceRecreationService;

    @PostMapping("/find-move-options")
    public ResponseEntity<List<MoveOptionDto>> findOptions(@RequestBody MoveSuggestionRequest request) {
        log.info("Поиск вариантов для переноса: sessionId={}, placementId={}",
                request.sessionId(), request.placementId());

        try {
            // 1. Пересоздаём workspace по самому размещению (сессию берём из него же,
            //    а не из sessionId с фронта — он может указывать на другую сессию).
            var recreated = workspaceRecreationService.recreateWorkspaceForPlacement(
                request.placementId()
            );

            // 2. Находим целевое занятие по placementId — надёжному уникальному ключу
            Lesson targetLesson = recreated.lessonByPlacementId().get(request.placementId());
            if (targetLesson == null) {
                log.warn("⚠️  Занятие с placementId={} не найдено в воркспейсе", request.placementId());
                return ResponseEntity.ok(Collections.emptyList());
            }

            // 3. Ищем варианты переноса
            List<MoveOptionDto> options = moveService.findMoveSuggestions(
                recreated.workspace(), targetLesson, request);

            log.info("✅ Найдено {} вариантов для переноса", options.size());
            return ResponseEntity.ok(options);

        } catch (Exception e) {
            log.error("❌ Ошибка при поиске вариантов: {}", e.getMessage(), e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * Поиск стартовых ячеек, куда помещается вся цепочка занятий.
     *
     * <p>POST /api/schedule/find-chain-move-options</p>
     */
    @PostMapping("/find-chain-move-options")
    public ResponseEntity<List<MoveOptionDto>> findChainOptions(@RequestBody ChainMoveSuggestionRequest request) {
        log.info("Поиск вариантов переноса цепочки: {} звеньев", request.placementIds() != null ? request.placementIds().size() : 0);
        try {
            List<MoveOptionDto> options = chainMoveService.findChainMoveOptions(request.placementIds());
            log.info("✅ Найдено {} вариантов для цепочки", options.size());
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            log.error("❌ Ошибка при поиске вариантов цепочки: {}", e.getMessage(), e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
