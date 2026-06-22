package ru.controllers.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.command.CreateScheduleSessionRequest;
import ru.dto.command.LessonPlacementDto;
import ru.dto.command.MoveLessonRequest;
import ru.dto.command.ScheduleSessionDto;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.exceptions.LessonMoveConflictException;
import ru.mapper.command.LessonPlacementMapper;
import ru.mapper.command.ScheduleSessionMapper;
import ru.services.LessonMoveService;
import ru.services.ScheduleGenerationService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller для CQRS Command Side.
 *
 * <p>Предоставляет API для:</p>
 * <ul>
 *   <li>Создания сессий редактирования</li>
 *   <li>Переноса занятий с optimistic lock</li>
 *   <li>Получения сессий и размещений</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/command")
@RequiredArgsConstructor
public class ScheduleCommandController {

    private final ScheduleGenerationService generationService;
    private final LessonMoveService lessonMoveService;
    private final ScheduleSessionMapper sessionMapper;
    private final LessonPlacementMapper placementMapper;

    /**
     * Создать новую сессию расписания.
     *
     * POST /api/schedule/command/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<ScheduleSessionDto> createSession(@RequestBody CreateScheduleSessionRequest request) {
        log.info("Создание сессии: name={}", request.name());

        ScheduleSession session = generationService.createScheduleSession(
            request.name(),
            "admin" // TODO: из Security Context
        );

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Генерация расписания с персистентностью.
     *
     * POST /api/schedule/command/sessions/generate
     */
    @PostMapping("/sessions/generate")
    public ResponseEntity<ScheduleSessionDto> generateSchedule(@RequestBody CreateScheduleSessionRequest request) {
        log.info("Генерация расписания: name={}, courses={}", request.name(), request.courseIds());

        ScheduleSession session = generationService.generateSchedule(
            request.name(),
            request.courseIds(),
            "admin"
        );

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Получить сессию по ID.
     *
     * GET /api/schedule/command/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ScheduleSessionDto> getSession(@PathVariable UUID sessionId) {
        ScheduleSession session = generationService.getScheduleSession(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        return ResponseEntity.ok(sessionMapper.toDto(session));
    }

    /**
     * Получить сессию для редактирования «живого» расписания.
     *
     * <p>POST /api/schedule/command/sessions/editable</p>
     *
     * <p>Находит сессию текущего расписания и при необходимости переоткрывает её
     * для редактирования — без повторной генерации. Возвращает 204, если расписания
     * (ни одной сессии с размещениями) ещё нет.</p>
     */
    @PostMapping("/sessions/editable")
    public ResponseEntity<ScheduleSessionDto> getEditableSession() {
        return generationService.getOrCreateEditableSession("user")
            .map(session -> ResponseEntity.ok(sessionMapper.toDto(session)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Перенести занятие с optimistic lock.
     *
     * POST /api/schedule/command/sessions/{sessionId}/move-lesson
     */
    @PostMapping("/sessions/{sessionId}/move-lesson")
    public ResponseEntity<?> moveLesson(
        @PathVariable UUID sessionId,
        @RequestBody MoveLessonRequest request
    ) {
        log.info("Перенос занятия: placementId={}, newDate={}, version={}",
                request.placementId(), request.newDate(), request.version());

        try {
            // Аудитории подбираются на бэке (см. LessonMoveService) —
            // request.newAuditoriumIds() намеренно не используется.
            ScheduleSession session = lessonMoveService.moveLesson(
                request.placementId(),
                request.newDate(),
                request.newSlot(),
                request.version(),
                "user"
            );

            return ResponseEntity.ok(sessionMapper.toDto(session));

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("❌ Optimistic lock conflict: {}", e.getMessage());

            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(new ConflictResponse(
                    "CONFLICT",
                    "Расписание было изменено другим пользователем. Обновите страницу.",
                    currentVersionOf(sessionId)
                ));

        } catch (LessonMoveConflictException e) {
            log.warn("❌ Resource conflict при переносе: {}", e.getMessage());

            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(new ConflictResponse(
                    "RESOURCE_CONFLICT",
                    "Невозможно перенести занятие: " + e.getMessage()
                        + ". Обновите данные и выберите другой слот.",
                    currentVersionOf(sessionId)
                ));
        }
    }

    /**
     * Текущая версия сессии для тела {@link ConflictResponse} (или {@code null}, если сессии нет).
     */
    private Long currentVersionOf(UUID sessionId) {
        return generationService.getScheduleSession(sessionId)
            .map(ScheduleSession::getVersion)
            .orElse(null);
    }

    /**
     * Получить размещения в сессии.
     *
     * GET /api/schedule/command/sessions/{sessionId}/placements
     */
    @GetMapping("/sessions/{sessionId}/placements")
    public ResponseEntity<List<LessonPlacementDto>> getPlacements(@PathVariable UUID sessionId) {
        List<LessonPlacement> placements = generationService.getPlacements(sessionId);

        List<LessonPlacementDto> dtos = placements.stream()
            .map(placementMapper::toDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Удалить сессию.
     *
     * DELETE /api/schedule/command/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        generationService.deleteScheduleSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
