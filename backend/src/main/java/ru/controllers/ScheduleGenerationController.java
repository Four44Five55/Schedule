package ru.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.dto.ScheduledLessonDto;
import ru.dto.ScheduleResultDto;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.entity.write.ScheduleSession;
import ru.entity.write.LessonPlacement;
import ru.repository.read.ScheduleViewRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.EducatorService;
import ru.services.GroupService;
import ru.services.ScheduleGenerationService;
import ru.services.ScheduleResponseService;
import ru.services.solver.ScheduleWorkspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleGenerationController {

    private final ScheduleGenerationService generationService;
    private final EducatorService educatorService;
    private final GroupService groupService;
    private final ScheduleResponseService responseService;
    private final ScheduleViewRepository viewRepository;
    private final LessonPlacementRepository placementRepo;

    /**
     * Запускает генерацию расписания для списка курсов и возвращает
     * все размещённые занятия в плоском формате для отображения на фронтенде.
     *
     * <p><b>ГИБРИДНЫЙ ПОДХОД (Solution #3):</b></p>
     * <ol>
     *   <li>Генерирует расписание с сохранением в БД (Command Side)</li>
     *   <li>Читает данные напрямую из Command Side (lesson_placement)</li>
     *   <li>Возвращает данные фронтенду мгновенно (без ожидания Query Side)</li>
     *   <li>Query Side синхронизируется асинхронно в фоне (для будущих запросов)</li>
     * </ol>
     *
     * <p><b>Преимущества:</b></p>
     * <ul>
     *   <li>✅ Нет race conditions (данные гарантированно есть)</li>
     *   <li>⚡ Мгновенный ответ (не ждем синхронизации)</li>
     *   <li>🔄 Query Side синхронизируется в фоне (@Async)</li>
     * </ul>
     *
     * @param request DTO с IDs курсов. Пример: {"courseIds": [701, 702, 703]}
     * @return Список размещённых занятий (ScheduledLessonDto).
     */
    @PostMapping("/generate")
    @Transactional  // ✅ ВАЖНО: Вся транзакция в одном методе
    public ResponseEntity<ScheduleResultDto> generateForCourses(@RequestBody Map<String, List<Integer>> request) {
        List<Integer> courseIds = request.get("courseIds");
        if (courseIds == null || courseIds.isEmpty()) return ResponseEntity.badRequest().build();

        // 1. ЗАПУСКАЕМ ГЕНЕРАЦИЮ (создает Session, Placements и публикует Event)
        ScheduleSession session = generationService.generateSchedule(
                "Генерация " + System.currentTimeMillis(),
                courseIds,
                "admin"
        );

        // 2. ✅ ЧИТАЕМ ИЗ COMMAND SIDE ( placements - уже сохранены в БД!)
        // Получаем placements сессии (внутри той же транзакции - данные гарантированно есть)
        List<LessonPlacement> placements = placementRepo.findBySessionId(session.getId());

        // 3. ✅ СТРОИМ СЕТКУ (Grid) напрямую из Placements
        // Используем новый гибридный метод buildGridFromPlacements()
        Map<String, List<ScheduledLessonDto>> grid = responseService.buildGridFromPlacements(placements);

        // 4. ПЛОСКИЙ СПИСОК (для совместимости)
        List<ScheduledLessonDto> allLessons = grid.values().stream()
                .flatMap(List::stream)
                .toList();

        // 5. ФОРМИРУЕМ ОТВЕТ (9 аргументов)
        ScheduleResultDto result = new ScheduleResultDto(
                "generated",
                allLessons,
                grid,
                allLessons.size(),
                0,
                "2026-02-09", // Даты можно вытянуть из периода курса
                "2026-07-31",
                grid.size(), // total slots
                allLessons.size() // used slots
        );

        // 6. После возврата метода: COMMIT → AFTER_COMMIT → @Async синхронизация Query Side
        // Query Side заполнится в фоне для будущих запросов
        return ResponseEntity.ok(result);
    }

    /**
     * Запускает генерацию для одного курса.
     * Делегирует в {@link #generateForCourses(Map)}.
     */
    @PostMapping("/generate/{courseId}")
    @Transactional  // ✅ ВАЖНО: Транзакция нужна для чтения placements
    public ResponseEntity<ScheduleResultDto> generateForCourse(@PathVariable Integer courseId) {
        Map<String, List<Integer>> request = new HashMap<>();
        request.put("courseIds", List.of(courseId));
        return generateForCourses(request);
    }

    /**
     * Извлекает из ScheduleWorkspace все размещённые занятия и преобразует их в DTO.
     */
    private List<ScheduledLessonDto> extractScheduledLessons(ScheduleWorkspace workspace) {
        List<ScheduledLessonDto> result = new ArrayList<>();

        // Группируем преподавателей и группы для быстрого доступа
        Map<Integer, Educator> educatorMap = educatorService.getAllEntities().stream()
                .collect(Collectors.toMap(Educator::getId, e -> e));
        Map<Integer, Group> groupMap = groupService.getAllEntities().stream()
                .collect(Collectors.toMap(Group::getId, g -> g));

        int lessonId = 0;

        for (var entry : workspace.getGrid().getGridMap().entrySet()) {
            CellForLesson cell = entry.getKey();
            List<ru.abstracts.AbstractLesson> abstractLessons = entry.getValue();

            for (var abstractLesson : abstractLessons) {
                if (!(abstractLesson instanceof Lesson lesson)) continue;

                lessonId++;

                String disciplineName = lesson.getDisciplineCourse().getDiscipline().getName();
                String disciplineAbbr = lesson.getDisciplineCourse().getDiscipline().getAbbreviation();

                String themeNumber = lesson.getCurriculumSlot().getThemeLesson() != null
                        ? lesson.getCurriculumSlot().getThemeLesson().getThemeNumber() : null;
                String themeTitle = lesson.getCurriculumSlot().getThemeLesson() != null
                        ? lesson.getCurriculumSlot().getThemeLesson().getTitle() : null;

                List<String> educatorNames = lesson.getEducators().stream()
                        .map(e -> educatorMap.containsKey(e.getId()) ? educatorMap.get(e.getId()).getName() : e.getName())
                        .collect(Collectors.toList());
                List<Integer> educatorIds = lesson.getEducators().stream()
                        .map(Educator::getId)
                        .collect(Collectors.toList());

                String streamName = lesson.getStudyStream() != null
                        ? lesson.getStudyStream().getName() : null;

                List<String> groupNames = lesson.getStudyStream() != null && lesson.getStudyStream().getGroups() != null
                        ? lesson.getStudyStream().getGroups().stream()
                        .map(Group::getName)
                        .collect(Collectors.toList())
                        : List.of();

                List<String> auditoriumNames = lesson.getAssignedAuditoriums() != null
                        ? lesson.getAssignedAuditoriums().stream()
                        .map(a -> a.getName())
                        .collect(Collectors.toList())
                        : List.of();
                List<Integer> auditoriumIds = lesson.getAssignedAuditoriums() != null
                        ? lesson.getAssignedAuditoriums().stream()
                        .map(a -> a.getId())
                        .collect(Collectors.toList())
                        : List.of();

                var dto = new ScheduledLessonDto(
                        lessonId,
                        cell.getDate(),
                        cell.getTimeSlotPair(),
                        disciplineName,
                        disciplineAbbr,
                        lesson.getKindOfStudy(),
                        lesson.getKindOfStudy().getFullName(),
                        lesson.getKindOfStudy().getAbbreviationName(),
                        lesson.getCurriculumSlot().getPosition(),
                        themeNumber,
                        themeTitle,
                        educatorIds,
                        educatorNames,
                        streamName,
                        groupNames,
                        auditoriumNames,
                        auditoriumIds,
                        null, // workspace-путь не знает UUID размещения (Command Side)
                        lesson.getCurriculumSlot() != null ? lesson.getCurriculumSlot().getId() : null
                );
                result.add(dto);
            }
        }

        return result;
    }
}