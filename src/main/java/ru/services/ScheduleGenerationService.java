package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.Auditorium;
import ru.entity.Educator;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.entity.logicSchema.DisciplineCourse;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.factories.LessonFactory;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.legacy.LegacyAlgorithmRunner;
import ru.services.solver.model.ScheduleGrid;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private final EducatorService educatorService;
    private final GroupService groupService;
    private final AuditoriumService auditoriumService;
    private final DisciplineCourseService disciplineCourseService;
    private final ConstraintService constraintService;
    private final LessonFactory lessonFactory;

    /**
     * Основной метод, запускающий процесс генерации расписания для одного курса.
     */
    @Transactional(readOnly = true)
    public ScheduleGrid generateForCourse(Integer courseId) {

        // --- 1. ПОДГОТОВКА ДАННЫХ (через сервисы) ---
        List<Educator> allEducators = educatorService.getAllEntities();
        List<Group> allGroups = groupService.getAllEntities();
        List<Auditorium> allAuditoriums = auditoriumService.getAllEntities();
        AllConstraints allConstraints = constraintService.loadAllConstraints();
        DisciplineCourse course = disciplineCourseService.getEntityById(courseId);

        List<Lesson> lessonsToPlace = lessonFactory.createLessonsForCourse(courseId);

        // --- 2. ИНИЦИАЛИЗАЦИЯ ЯДРА РЕШАТЕЛЯ ---
        ScheduleWorkspace workspace = new ScheduleWorkspace(
                course.getStudyPeriod().getStartDate(),
                course.getStudyPeriod().getEndDate(),
                allEducators,
                allGroups,
                allAuditoriums,
                allConstraints
        );

        // --- 3. ЗАПУСК АЛГОРИТМА ---
        LegacyAlgorithmRunner runner = new LegacyAlgorithmRunner(workspace, lessonsToPlace);
        runner.run(); // Запускаем адаптированный алгоритм

        // --- 4. ВОЗВРАТ РЕЗУЛЬТАТА ---
        return workspace.getGrid();
    }
}
