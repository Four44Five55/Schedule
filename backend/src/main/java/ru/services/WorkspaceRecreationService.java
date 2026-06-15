package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.*;
import ru.entity.write.LessonPlacement;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.factories.CellForLessonFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для пересоздания workspace из сохранённых placements.
 *
 * <p>Позволяет восстановить ScheduleWorkspace из LessonPlacement,
 * что необходимо для поиска вариантов переноса занятий.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceRecreationService {

    private final EducatorService educatorService;
    private final GroupService groupService;
    private final AuditoriumService auditoriumService;
    private final ConstraintService constraintService;
    private final AssignmentService assignmentService;
    private final ru.repository.write.LessonPlacementRepository placementRepo;

    /**
     * Пересоздать workspace из placements сессии.
     *
     * @param sessionId ID сессии расписания
     * @return Пересозданный workspace
     */
    @Transactional(readOnly = true)
    public ru.services.solver.ScheduleWorkspace recreateWorkspaceFromSession(UUID sessionId) {
        log.info("🔄 Пересоздание workspace для session: {}", sessionId);

        List<LessonPlacement> placements = placementRepo.findBySessionId(sessionId);

        if (placements.isEmpty()) {
            log.warn("⚠️  Сессия не содержит placements");
            return createEmptyWorkspace();
        }

        log.info("Загружено {} placements", placements.size());

        // 1. Определяем период из placements
        DateRange period = determinePeriod(placements);

        // 2. Создаём пустой workspace
        CellForLessonFactory.initializeCellCache(period.startDate, period.endDate);

        ru.services.solver.ScheduleWorkspace workspace = new ru.services.solver.ScheduleWorkspace(
            period.startDate,
            period.endDate,
            educatorService.getAllEntities(),
            groupService.getAllEntities(),
            auditoriumService.getAllEntities(),
            constraintService.loadAllConstraints()
        );

        // 3. Восстанавливаем и размещаем занятия
        int placedCount = 0;
        for (LessonPlacement placement : placements) {
            try {
                placeLessonFromPlacement(workspace, placement);
                placedCount++;
            } catch (Exception e) {
                log.error("❌ Ошибка размещения placementId={}: {}",
                    placement.getId(), e.getMessage());
            }
        }

        log.info("✅ Workspace пересоздан: {} занятий из {} размещены",
                placedCount, placements.size());

        return workspace;
    }

    /**
     * Определить период (начало и конец) из placements.
     */
    private DateRange determinePeriod(List<LessonPlacement> placements) {
        LocalDate minDate = null;
        LocalDate maxDate = null;

        for (LessonPlacement placement : placements) {
            LocalDate date = placement.getScheduledDate();
            if (minDate == null || date.isBefore(minDate)) {
                minDate = date;
            }
            if (maxDate == null || date.isAfter(maxDate)) {
                maxDate = date;
            }
        }

        if (minDate == null || maxDate == null) {
            // Если нет placements, используем период по умолчанию
            minDate = LocalDate.now();
            maxDate = minDate.plusMonths(1);
        }

        return new DateRange(minDate, maxDate);
    }

    /**
     * Разместить занятие из placement в workspace.
     */
    private void placeLessonFromPlacement(
            ru.services.solver.ScheduleWorkspace workspace,
            LessonPlacement placement) {

        Assignment assignment = placement.getAssignment();
        if (assignment == null) {
            log.warn("⚠️  Placement без assignment: {}", placement.getId());
            return;
        }

        // Создаём Lesson из Assignment
        Lesson lesson = createLessonFromAssignment(assignment);

        // Создаём ячейку
        CellForLesson cell = new CellForLesson(
            placement.getScheduledDate(),
            placement.getScheduledSlot()
        );

        // Размещаем занятие
        if (placement.getAssignedAuditoriums() != null && !placement.getAssignedAuditoriums().isEmpty()) {
            List<Auditorium> auditoriums = new java.util.ArrayList<>(placement.getAssignedAuditoriums());
            workspace.forcePlacement(lesson, cell, auditoriums);
        } else {
            // Если аудитории не назначены, передаём пустой список
            workspace.forcePlacement(lesson, cell, new java.util.ArrayList<>());
        }
    }

    /**
     * Создать Lesson из Assignment.
     */
    private Lesson createLessonFromAssignment(Assignment assignment) {
        Lesson lesson = new Lesson();
        lesson.setDisciplineCourse(assignment.getCurriculumSlot().getDisciplineCourse());
        lesson.setCurriculumSlot(assignment.getCurriculumSlot());
        lesson.setStudyStream(assignment.getStudyStream());
        lesson.setEducators(assignment.getEducators());

        // Копируем требования к аудитории
        if (assignment.getCurriculumSlot().getRequiredAuditorium() != null) {
            lesson.setRequiredAuditorium(assignment.getCurriculumSlot().getRequiredAuditorium());
        }
        if (assignment.getCurriculumSlot().getPriorityAuditorium() != null) {
            lesson.setPriorityAuditorium(assignment.getCurriculumSlot().getPriorityAuditorium());
        }
        if (assignment.getCurriculumSlot().getAllowedAuditoriumPool() != null) {
            lesson.setAllowedAuditoriumPool(assignment.getCurriculumSlot().getAllowedAuditoriumPool());
        }

        return lesson;
    }

    /**
     * Создать пустой workspace (если нет placements).
     */
    private ru.services.solver.ScheduleWorkspace createEmptyWorkspace() {
        LocalDate now = LocalDate.now();
        CellForLessonFactory.initializeCellCache(now, now.plusMonths(1));

        return new ru.services.solver.ScheduleWorkspace(
            now,
            now.plusMonths(1),
            educatorService.getAllEntities(),
            groupService.getAllEntities(),
            auditoriumService.getAllEntities(),
            constraintService.loadAllConstraints()
        );
    }

    /**
     * Вспомогательный класс для диапазона дат.
     */
    private record DateRange(LocalDate startDate, LocalDate endDate) {}
}
