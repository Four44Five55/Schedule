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
    private final CurriculumSlotService curriculumSlotService;
    private final SlotChainService slotChainService;
    private final LessonSortingService lessonSorterService;
    private final GeneticAlgorithmRunner geneticAlgorithmRunner;

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
        DistributionDiscipline discipline = new DistributionDiscipline(workspace, sortedLessons, allEducators,
                slotChainService, curriculumSlotService, lessonSorterService);
        discipline.distributeLessons();


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
        DisciplineCourse firstCourse = disciplineCourseService.getEntityById(courseIds.get(0));
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
        DistributionDiscipline distributor = new DistributionDiscipline(
                workspace,
                allLessons,
                educatorService.getAllEntities(),
                slotChainService,
                curriculumSlotService,
                lessonSorterService
        );
        distributor.distributeLessons();

        return workspace;
    }

    /**
     * Переносит данные из Генома в Workspace для экспорта.
     * Обновляет и общую сетку, и состояние каждого ресурса.
     */
    private void applyGenomeToWorkspace(Genome genome, ScheduleWorkspace workspace) {
        // workspace.getGrid().clear(); // Опционально

        for (Gene gene : genome.getGenes()) {
            if (gene.getAssignedSlot() != null) {
                List<Lesson> lessons = gene.getLessons();
                List<Auditorium> geneAuditoriums = gene.getAssignedAuditoriums();
                CellForLesson slot = gene.getAssignedSlot();

                for (int i = 0; i < lessons.size(); i++) {
                    Lesson lesson = lessons.get(i);

                    // Извлекаем аудиторию для конкретного урока из списка аудиторий гена
                    List<Auditorium> lessonAuditoriums = new ArrayList<>();
                    if (geneAuditoriums != null && i < geneAuditoriums.size()) {
                        Auditorium aud = geneAuditoriums.get(i);
                        if (aud != null) lessonAuditoriums.add(aud);
                    }

                    // ВЫЗЫВАЕМ АТОМАРНУЮ ОПЕРАЦИЮ
                    workspace.forcePlacement(lesson, slot, lessonAuditoriums);
                }
            }
        }
    }

}
