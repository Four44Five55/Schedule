package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dto.moveLesson.MoveOptionDto;
import ru.dto.moveLesson.MoveSuggestionRequest;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.SchedulableResource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис поиска доступных мест для переноса.
 */
@Service
@RequiredArgsConstructor
public class MoveLessonSuggestionService {

    /**
     * Основной метод поиска доступных мест для переноса.
     * Реализует каскадную фильтрацию для максимальной производительности.
     */
    public List<MoveOptionDto> findMoveSuggestions(ScheduleWorkspace workspace, MoveSuggestionRequest request) {
        // 1. Находим занятие в текущем воркспейсе
        // (Предполагаем, что воркспейс уже загружен текущим состоянием из БД)
        Lesson targetLesson = findLesson(workspace, request.lessonId());

        // 2. ВАЖНО: Виртуально изымаем занятие из воркспейса.
        // Это освобождает ресурсы (преподавателя, группу, аудиторию),
        // чтобы мы могли проверить их доступность в других слотах.
        CellForLesson originalCell = workspace.getCellForLesson(targetLesson);
        workspace.removePlacement(targetLesson);

        // 3. Получаем исходное множество всех ячеек семестра
        List<CellForLesson> candidates = new ArrayList<>(CellForLessonFactory.getAllCells());

        // --- КАСКАДНЫЙ ФИЛЬТР ---

        // ШАГ 1: Фильтр по корневой сущности (самый быстрый)
        // Если мы смотрим расписание Группы А, то в первую очередь убираем все ячейки,
        // где Группа А уже занята чем-то другим.
        SchedulableResource rootResource = getRootResource(workspace, request);
        candidates.removeIf(cell -> !rootResource.isFree(cell));

        // ШАГ 2: Фильтр по остальным участникам занятия (Educators + Groups)
        // Если Группа А свободна, проверяем, свободен ли Преподаватель и другие группы потока.
        List<SchedulableResource> otherParticipants = getParticipantsExceptRoot(workspace, targetLesson, request.rootEntityId());
        for (SchedulableResource participant : otherParticipants) {
            if (candidates.isEmpty()) break;
            candidates.removeIf(cell -> !participant.isFree(cell));
        }

        // ШАГ 3: Фильтр по инфраструктуре (самый тяжелый)
        // Только для ячеек, где ВСЕ люди свободны, проверяем наличие подходящей аудитории.
        if (!candidates.isEmpty()) {
            candidates.removeIf(cell -> {
                // Используем существующую логику воркспейса для подбора аудитории
                // Если список пуст — значит в этот слот занятие не влезет по аудиторному фонду
                return workspace.findAvailableAuditoriumsFor(targetLesson, cell).isEmpty();
            });
        }

        // 4. Восстанавливаем воркспейс (возвращаем занятие на место)
        workspace.forcePlacement(targetLesson, originalCell, targetLesson.getAssignedAuditoriums());

        // 5. Маппим результат в DTO
        return candidates.stream()
                .map(cell -> new MoveOptionDto(cell.getDate(), cell.getTimeSlotPair()))
                .collect(Collectors.toList());
    }

    // Вспомогательные методы
    private SchedulableResource getRootResource(ScheduleWorkspace ws, MoveSuggestionRequest req) {
        return switch (req.rootEntityType()) {
            case "EDUCATOR" -> ws.getResourceManager().getEducatorResource(req.rootEntityId());
            case "GROUP" -> ws.getResourceManager().getGroupResource(req.rootEntityId());
            case "AUDITORIUM" -> ws.getResourceManager().getAuditoriumResource(req.rootEntityId());
            default -> throw new IllegalArgumentException("Unknown entity type");
        };
    }

    private List<SchedulableResource> getParticipantsExceptRoot(ScheduleWorkspace ws, Lesson lesson, Integer rootId) {
        List<SchedulableResource> participants = new ArrayList<>();
        lesson.getEducators().forEach(e -> {
            if (!e.getId().equals(rootId)) participants.add(ws.getResourceManager().getEducatorResource(e.getId()));
        });
        if (lesson.getStudyStream() != null) {
            lesson.getStudyStream().getGroups().forEach(g -> {
                if (!g.getId().equals(rootId)) participants.add(ws.getResourceManager().getGroupResource(g.getId()));
            });
        }
        return participants;
    }

    private Lesson findLesson(ScheduleWorkspace workspace, Integer lessonId) {
        // lessonId - это hashCode от placementId (UUID)
        // Ищем занятие по placementId вместо curriculumSlotId
        return workspace.getGrid().getGridMap().values().stream()
                .flatMap(List::stream)
                .filter(l -> l instanceof Lesson && ((Lesson) l).getCurriculumSlot().getId().equals(lessonId))
                .map(l -> (Lesson) l)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Занятие с lessonId=" + lessonId + " не найдено в воркспейсе"));
    }
}