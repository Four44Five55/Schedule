package ru.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.*;
import ru.entity.logicSchema.DisciplineCourse;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.distribution.DistributionDiscipline;
import ru.services.factories.CellForLessonFactory;
import ru.services.factories.LessonFactory;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.genetic.GeneticAlgorithmRunner;
import ru.services.solver.model.Gene;
import ru.services.solver.model.Genome;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final LessonSortingService lessonSorterService;
    private final GeneticAlgorithmRunner geneticAlgorithmRunner;
    private final DistributionDiscipline distributionDiscipline;

    /**
     * Основной метод, запускающий процесс генерации расписания для одного курса.
     */
    @Transactional(readOnly = true)
    public ScheduleWorkspace generateForCourse(Integer courseId) {
        DisciplineCourse course = disciplineCourseService.getEntityById(courseId);
        //создание кеша всех ячеек для периода
        CellForLessonFactory.initializeCellCache(
                course.getStudyPeriod().getStartDate(),
                course.getStudyPeriod().getEndDate()
        );

        List<Educator> allEducators = educatorService.getAllEntities();
        List<Group> allGroups = groupService.getAllEntities();
        List<Auditorium> allAuditoriums = auditoriumService.getAllEntities();
        AllConstraints allConstraints = constraintService.loadAllConstraints();


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
        List<Lesson> sortedLessons = lessonSorterService.getSortedLessons(lessonsToPlace);
        distributionDiscipline.distribute(workspace, sortedLessons, allEducators);


/*        // --- 3. ЗАПУСК АЛГОРИТМА ---
        LegacyAlgorithmRunner runner = new LegacyAlgorithmRunner(workspace, lessonsToPlace, lessonSorterService);
        runner.run(); // Запускаем адаптированный алгоритм*/

        /*// Запускаем ГА
        List<Lesson> sortedLessons = lessonSorterService.getSortedLessons(lessonsToPlace);
        Genome bestResult = geneticAlgorithmRunner.run(workspace, sortedLessons);
        // Применяем лучшее решение к реальной сетке (чтобы экспорт работал)
        applyGenomeToWorkspace(bestResult, workspace);*/


        // --- 4. ВОЗВРАТ РЕЗУЛЬТАТА ---
        return workspace;
    }

    @Transactional(readOnly = true)
    public ScheduleWorkspace generateForCourseList(List<Integer> courseIds) {
        // 1. Инициализация (берем период первого курса)
        DisciplineCourse firstCourse = disciplineCourseService.getEntityById(courseIds.getFirst());
        CellForLessonFactory.initializeCellCache(
                firstCourse.getStudyPeriod().getStartDate(),
                firstCourse.getStudyPeriod().getEndDate()
        );

        // 2. Создаем Общий Workspace
        ScheduleWorkspace workspace = new ScheduleWorkspace(
                firstCourse.getStudyPeriod().getStartDate(),
                firstCourse.getStudyPeriod().getEndDate(),
                educatorService.getAllEntities(),
                groupService.getAllEntities(),
                auditoriumService.getAllEntities(),
                constraintService.loadAllConstraints()
        );

        // 3. Загружаем ВСЕ уроки
        List<Lesson> allLessons = new ArrayList<>();
        for (Integer id : courseIds) {
            List<Lesson> courseLessons = lessonFactory.createLessonsForCourse(id);
            // Сразу сортируем их по позиции, чтобы в DistributionDiscipline они пришли в порядке
            courseLessons.sort(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()));
            allLessons.addAll(courseLessons);
        }

        // 4. Запускаем распределение
        distributionDiscipline.distribute(workspace, allLessons, educatorService.getAllEntities());

        return workspace;
    }
}
