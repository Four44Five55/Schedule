package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.dto.moveLesson.MoveOptionDto;
import ru.dto.moveLesson.MoveSuggestionRequest;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.constraints.ConstraintAdmissionRule;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.SchedulableResource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис поиска доступных мест для переноса.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoveLessonSuggestionService {

    /**
     * Основной метод поиска доступных мест для переноса.
     * Реализует каскадную фильтрацию для максимальной производительности.
     */
    public List<MoveOptionDto> findMoveSuggestions(ScheduleWorkspace workspace, Lesson targetLesson, MoveSuggestionRequest request) {
        // 1. Занятие уже найдено по placementId на границе восстановления workspace
        //    (см. WorkspaceRecreationService.RecreatedWorkspace) — это тот же объект,
        //    что лежит в сетке, поэтому операции ниже консистентны.

        // 2. ВАЖНО: Виртуально изымаем занятие из воркспейса — но только на время подбора.
        // Это освобождает ресурсы (преподавателя, группу, аудиторию), чтобы мы могли проверить
        // их доступность в других слотах. Возврат на место гарантирует withoutPlacements
        // (finally): при кэшировании workspace переживает запрос, и «изъял и не вернул» стало бы
        // потерей занятия в кэше — без единой ошибки в логе.
        CellForLesson originalCell = workspace.getCellForLesson(targetLesson);
        return workspace.withoutPlacements(List.of(targetLesson),
                () -> suggestFor(workspace, targetLesson, request, originalCell));
    }

    /** Сам каскадный подбор — выполняется, когда занятие уже изъято из сетки. */
    private List<MoveOptionDto> suggestFor(ScheduleWorkspace workspace, Lesson targetLesson,
                                           MoveSuggestionRequest request, CellForLesson originalCell) {
        // 3. Получаем исходное множество всех ячеек семестра
        List<CellForLesson> candidates = new ArrayList<>(workspace.getCalendar().cells());

        // Исключаем текущую ячейку занятия: на шаге 2 мы его виртуально изъяли,
        // поэтому его собственный слот выглядит «свободным». Предлагать перенос
        // туда, где занятие уже стоит, не нужно.
        if (originalCell != null) {
            candidates.remove(originalCell);
        }

        // --- КАСКАДНЫЙ ФИЛЬТР ---

        // ШАГ 1: Фильтр по корневой сущности (самый быстрый)
        // Если мы смотрим расписание Группы А, то в первую очередь убираем все ячейки,
        // где Группа А уже занята чем-то другим.
        // Подсказка обязана спрашивать РОВНО то же, что фактический перенос (он идёт с
        // HONOR_WINDOWS): иначе ячейка окна аттестации либо не подсветится, либо подсветится и
        // даст 409. Вход правила собирается одним общим сборщиком.
        ConstraintAdmissionRule.Admission admission = ConstraintAdmissionRule.Admission.of(targetLesson);

        SchedulableResource rootResource = getRootResource(workspace, request);
        candidates.removeIf(cell -> !rootResource.isFree(cell, admission));

        // ШАГ 2: Фильтр по остальным участникам занятия (Educators + Groups)
        // Если Группа А свободна, проверяем, свободен ли Преподаватель и другие группы потока.
        List<SchedulableResource> otherParticipants = getParticipantsExceptRoot(workspace, targetLesson, request.rootEntityId());
        for (SchedulableResource participant : otherParticipants) {
            if (candidates.isEmpty()) break;
            candidates.removeIf(cell -> !participant.isFree(cell, admission));
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

        // 4. Маппим результат в DTO. Возврат занятия на место делает withoutPlacements —
        // в finally, то есть и на исключении из любого фильтра выше.
        return candidates.stream()
                .map(cell -> new MoveOptionDto(cell.getDate(), cell.getTimeSlotPair()))
                .collect(Collectors.toList());
    }

    /**
     * Доступные ячейки для занятия, которого ещё НЕТ в сетке (ручная установка, Фаза B).
     *
     * <p>Отличие от {@link #findMoveSuggestions}: занятие не размещено, поэтому изымать
     * и восстанавливать его не нужно — просто прогоняем тот же каскадный фильтр
     * (корневой ресурс → остальные участники → аудиторный фонд) по всем ячейкам.</p>
     *
     * @param workspace воркспейс на рамках периода (с засеянными размещениями сессии)
     * @param lesson    занятие, собранное из assignment ({@code WorkspacePlacementSeeder.buildLesson})
     * @param rootType  тип корневой сущности ('EDUCATOR' | 'GROUP' | 'AUDITORIUM')
     * @param rootId    id корневой сущности (через которую открыта сетка)
     * @return валидные {@code (date, slot)} для установки
     */
    public List<MoveOptionDto> findPlacementSuggestions(ScheduleWorkspace workspace, Lesson lesson,
                                                        String rootType, Integer rootId) {
        List<CellForLesson> candidates = new ArrayList<>(workspace.getCalendar().cells());

        // Как и в findMoveSuggestions: подсветка палитры идёт по тем же правилам, что установка.
        ConstraintAdmissionRule.Admission admission = ConstraintAdmissionRule.Admission.of(lesson);

        SchedulableResource rootResource = getRootResourceByType(workspace, rootType, rootId);
        candidates.removeIf(cell -> !rootResource.isFree(cell, admission));

        List<SchedulableResource> otherParticipants = getParticipantsExceptRoot(workspace, lesson, rootId);
        for (SchedulableResource participant : otherParticipants) {
            if (candidates.isEmpty()) break;
            candidates.removeIf(cell -> !participant.isFree(cell, admission));
        }

        if (!candidates.isEmpty()) {
            candidates.removeIf(cell -> workspace.findAvailableAuditoriumsFor(lesson, cell).isEmpty());
        }

        return candidates.stream()
                .map(cell -> new MoveOptionDto(cell.getDate(), cell.getTimeSlotPair()))
                .collect(Collectors.toList());
    }

    // Вспомогательные методы
    private SchedulableResource getRootResource(ScheduleWorkspace ws, MoveSuggestionRequest req) {
        return getRootResourceByType(ws, req.rootEntityType(), req.rootEntityId());
    }

    private SchedulableResource getRootResourceByType(ScheduleWorkspace ws, String rootType, Integer rootId) {
        return switch (rootType) {
            case "EDUCATOR" -> ws.getResourceManager().getEducatorResource(rootId);
            case "GROUP" -> ws.getResourceManager().getGroupResource(rootId);
            case "AUDITORIUM" -> ws.getResourceManager().getAuditoriumResource(rootId);
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
}