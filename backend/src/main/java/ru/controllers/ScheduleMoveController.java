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
import ru.services.workspace.WorkspaceProvider;

import java.util.Collections;
import java.util.List;

/**
 * Подбор ячеек, куда занятие (или цепочка) помещается на актуальном расписании.
 *
 * <p><b>Пустой список здесь — это ответ, а не отказ.</b> Раньше оба метода ловили
 * {@code Exception} и отвечали {@code 200 []}, то есть переводили любой сбой в утверждение
 * «переносить некуда». Клиент верил: подсветка гасла, и человек уходил искать место в другом
 * периоде вместо того, чтобы повторить. Теперь сбой уходит в {@link ApiExceptionHandler}
 * пятисоткой с {@code correlationId}, а пустой список означает ровно то, что означает.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleMoveController {

    private final MoveLessonSuggestionService moveService;
    private final LessonChainMoveService chainMoveService;
    private final WorkspaceProvider workspaceProvider;

    @PostMapping("/find-move-options")
    public ResponseEntity<List<MoveOptionDto>> findOptions(@RequestBody MoveSuggestionRequest request) {
        // 1. Берём workspace сессии этого размещения (сессию определяет само размещение, а не
        //    sessionId с фронта — он может указывать на другую). Снимок может прийти из кэша;
        //    провайдер одалживает его на время вызова и следит, чтобы читатель вернул изъятое.
        return ResponseEntity.ok(workspaceProvider.withWorkspaceOfPlacement(request.placementId(), recreated -> {
            // 2. Находим целевое занятие по placementId — надёжному уникальному ключу.
            //    Различаем два случая, как и в подборе для цепочки: пустая карта означает, что
            //    размещения уже нет (сняли, пока клиент спрашивал) — законный пустой ответ;
            //    занятие, потерявшееся в непустой карте, — испорченное состояние, и выдавать его
            //    за «переносить некуда» нельзя.
            Lesson targetLesson = recreated.lessonByPlacementId().get(request.placementId());
            if (targetLesson == null) {
                if (recreated.lessonByPlacementId().isEmpty()) {
                    log.warn("⚠️  Размещение placementId={} уже снято — вариантов нет", request.placementId());
                    return Collections.<MoveOptionDto>emptyList();
                }
                throw new IllegalStateException(
                        "Занятие не восстановлено для размещения " + request.placementId());
            }

            // 3. Ищем варианты переноса
            return moveService.findMoveSuggestions(recreated.workspace(), targetLesson, request);
        }));
    }

    /**
     * Поиск стартовых ячеек, куда помещается вся цепочка занятий.
     *
     * <p>POST /api/schedule/find-chain-move-options</p>
     */
    @PostMapping("/find-chain-move-options")
    public ResponseEntity<List<MoveOptionDto>> findChainOptions(@RequestBody ChainMoveSuggestionRequest request) {
        log.info("Поиск вариантов переноса цепочки: {} звеньев", request.placementIds() != null ? request.placementIds().size() : 0);
        List<MoveOptionDto> options = chainMoveService.findChainMoveOptions(request.placementIds());
        log.info("✅ Найдено {} вариантов для цепочки", options.size());
        return ResponseEntity.ok(options);
    }
}
