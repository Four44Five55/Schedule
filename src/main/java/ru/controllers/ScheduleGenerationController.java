package ru.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.ScheduledLessonDto;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.services.*;
import ru.services.solver.ScheduleWorkspace;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleGenerationController {

    private final ScheduleGenerationService generationService;
    private final EducatorService educatorService;
    private final GroupService groupService;

    /**
     * Запускает генерацию расписания для списка курсов и возвращает
     * все размещённые занятия в плоском формате для отображения на фронтенде.
     *
     * @param request DTO с IDs курсов. Пример: {"courseIds": [701, 702, 703]}
     * @return Список размещённых занятий (ScheduledLessonDto).
     */
    @PostMapping("/generate")
    public ResponseEntity<ScheduleResultDto> generateForCourses(@RequestBody Map<String, List<Integer>> request) {
        List<Integer> courseIds = request.get("courseIds");
        if (courseIds == null || courseIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ScheduleWorkspace workspace = generationService.generateForCourseList(courseIds);
        if (workspace == null) {
            return ResponseEntity.internalServerError().build();
        }

        // Собираем все размещённые занятия из ScheduleGrid
        List<ScheduledLessonDto> lessons = extractScheduledLessons(workspace);

        // Собираем неразмещённые занятия
        // У нас нет прямого доступа к списку lessons внутри Workspace, но
        // DistributionContext доступен через workspace? Нет, он внутри generationService.
        // Для простоты пока возвращаем только размещённые.

        int totalSlots = workspace.getGrid().getGridMap().size();
        int usedSlots = (int) workspace.getGrid().getGridMap().values().stream()
                .filter(l -> !l.isEmpty())
                .count();

        ScheduleResultDto result = new ScheduleResultDto(
                "generated",
                lessons,
                lessons.size(),
                0, // unplaced — пока 0, позже можно добавить
                workspace.getGrid().getStartDate().toString(),
                workspace.getGrid().getEndDate().toString(),
                totalSlots,
                usedSlots
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Запускает генерацию для одного курса.
     */
    @PostMapping("/generate/{courseId}")
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
                        auditoriumIds
                );
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * DTO для ответа с результатом генерации.
     */
    public record ScheduleResultDto(
            String status,
            List<ScheduledLessonDto> lessons,
            int placedCount,
            int unplacedCount,
            String startDate,
            String endDate,
            int totalSlots,
            int usedSlots
    ) {}
}