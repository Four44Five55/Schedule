package ru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.entity.*;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.enums.KindOfConstraints;
import ru.enums.KindOfStudy;
import ru.repositories.DisciplineRepository;
import ru.services.DisciplineCurriculumService;
import ru.services.DisciplineService;
import ru.services.DistributionDiscipline;
import ru.services.ScheduleExporter;
import ru.utils.DateUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.entity.factories.LessonFactory.createLessonsDiscipline;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        //Контекст Spring
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);




        DisciplineService disciplineService = context.getBean(DisciplineService.class);
        ScheduleExporter scheduleExporter = context.getBean(ScheduleExporter.class);
        DisciplineCurriculumService disciplineCurriculumService = context.getBean(DisciplineCurriculumService.class);
        disciplineService.fullDiagnostic();



        /*Discipline disciplineMATest =disciplineService.createDiscipline("Math", "MTH");
        System.out.println("Сохранённая дисциплина с ID: " + disciplineService.getById(3));*/

        Discipline disciplineMA = disciplineService.getById(1);
        Discipline disciplineAG = disciplineService.getById(2);

        Educator educatorMA = new Educator("Лектор А.А.");
        Educator educatorMAPractise1 = new Educator("Практик1 А.А.");
        Educator educatorAG = new Educator("Аналитик Г.А.");

        educatorMA.addDefaultPriority();
        educatorMA.addConstraint(DateUtils.parseDateFlexible("2025-09-06"), DateUtils.parseDateFlexible("2025-09-15"), KindOfConstraints.BUSINESS_TRIP);
        educatorMAPractise1.addDefaultPriority();
        educatorMAPractise1.addConstraint(DateUtils.parseDateFlexible("2025-09-20"), DateUtils.parseDateFlexible("2025-09-27"), KindOfConstraints.BUSINESS_TRIP);
        educatorAG.addConstraint(DateUtils.parseDateFlexible("2025-09-20"), DateUtils.parseDateFlexible("2025-09-30"), KindOfConstraints.VACATION);
        educatorAG.addConstraint(DateUtils.parseDateFlexible("2025-12-29"), DateUtils.parseDateFlexible("2026-01-12"), KindOfConstraints.VACATION);


        List<Group> groups = List.of
                (new Group("31", 10, new Auditorium("306-4", 110)),
                        new Group("33", 13, new Auditorium("205-4", 30)),
                        new Group("34", 14, new Auditorium("313-4", 30)),
                        new Group("35-1", 25, new Auditorium("217-4", 30)),
                        new Group("35-2", 24, new Auditorium("206-4", 39)),
                        new Group("36", 18, new Auditorium("312-4", 30)));

        List<GroupCombination> groupCombinations = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 31
                new GroupCombination(List.of(groups.get(1), groups.get(2))), // Группы 33 и 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))), // Группа 35-2
                new GroupCombination(List.of(groups.get(5)))  // Группа 36
        );

        DisciplineCurriculum curriculumMA = disciplineCurriculumService.getDisciplineCurriculumById(1);


        List<KindOfStudy> logicSchemaMA = List.of(KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.LAB_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.QUIZ);
        List<KindOfStudy> logicSchemaAG = List.of(KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.LAB_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK);


/*        Map<GroupCombination, Educator> groupCombinationEducatorMA = new HashMap<>();
        groupCombinationEducatorMA.put(groupCombinations.get(0), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(1), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(2), educatorMathPractise1);
        groupCombinationEducatorMA.put(groupCombinations.get(3), educatorMathPractise1);
        groupCombinationEducatorMA.put(groupCombinations.get(4), educatorMathPractise1);

        List<Educator> educators = List.of(educatorMathLecturer, educatorMathPractise1);*/


        Map<GroupCombination, Educator> groupCombinationEducatorMA = new HashMap<>();
        groupCombinationEducatorMA.put(groupCombinations.get(0), educatorMA);
        groupCombinationEducatorMA.put(groupCombinations.get(1), educatorMA);
        groupCombinationEducatorMA.put(groupCombinations.get(2), educatorMA);
        groupCombinationEducatorMA.put(groupCombinations.get(3), educatorMA);
        groupCombinationEducatorMA.put(groupCombinations.get(4), educatorMA);

        Map<GroupCombination, Educator> groupCombinationEducatorAG = new HashMap<>();
        groupCombinationEducatorAG.put(groupCombinations.get(0), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(1), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(2), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(3), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(4), educatorAG);


        List<Educator> educatorsMA = List.of(educatorMA);
        List<Educator> educatorsAG = List.of(educatorAG);


        //List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, logicSchemaMA, groupCombinations, educatorMA, groupCombinationEducatorMA);
        List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, curriculumMA, groupCombinations, educatorMA, groupCombinationEducatorMA);
        List<Lesson> logicSchemaStudyAG = createLessonsDiscipline(disciplineAG, Lesson.class, logicSchemaAG, groupCombinations, educatorAG, groupCombinationEducatorAG);


        ScheduleGrid scheduleGrid = new ScheduleGrid();

        DistributionDiscipline distributionDisciplineMA = new DistributionDiscipline(scheduleGrid, logicSchemaStudyMA, educatorsMA);
        distributionDisciplineMA.distributeLessons();

        DistributionDiscipline distributionDisciplineAG = new DistributionDiscipline(scheduleGrid, logicSchemaStudyAG, educatorsAG);
        distributionDisciplineAG.distributeLessons();


        //========================================================================
/*        // 2. Запуск ГА
        ScheduleChromosome bestSchedule = GeneticAlgorithm.run(
                logicSchemaStudyMA, scheduleGrid,
                100,   // Размер популяции
                50,    // Поколений
                0.1    // Вероятность мутации
        );

        //Экспорт в Excel
        for (Group group : groups) {
            ScheduleExporter.exportToExcel(bestSchedule.getScheduleGrid(), group, group.getName());
        }


        ScheduleExporter.exportToExcel(bestSchedule.getScheduleGrid(), educatorMathLecturer, educatorMathLecturer.getName());
        ScheduleExporter.exportToExcel(bestSchedule.getScheduleGrid(), educatorMathPractise1, educatorMathPractise1.getName());
        ScheduleExporter.exportToExcel(bestSchedule.getScheduleGrid(), educatorAG, educatorAG.getName());*/
        //==============================================================================================================


        //Экспорт в Excel
        for (Group group : groups) {
            ScheduleExporter.exportToExcel(scheduleGrid, group, group.getName());
        }

        System.out.println(scheduleGrid.getAmountDays());

        ScheduleExporter.exportToExcel(scheduleGrid, educatorMA, educatorMA.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorMAPractise1, educatorMAPractise1.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorAG, educatorAG.getName());


        System.out.println();

        // Закрываем контекст Spring
        context.close();
    }
}