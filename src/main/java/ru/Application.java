package ru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.entity.*;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.DisciplineCurriculum;
import ru.entity.logicSchema.ThemeLesson;
import ru.enums.KindOfConstraints;
import ru.enums.KindOfStudy;
import ru.repositories.ThemeLessonRepository;
import ru.services.*;
import ru.utils.DateUtils;

import java.sql.SQLException;
import java.util.*;

import static ru.entity.factories.LessonFactory.createLessonsDiscipline;

@SpringBootApplication
public class Application {
    public static void main(String[] args) throws SQLException {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);


        DisciplineService disciplineService = context.getBean(DisciplineService.class);
        DisciplineCurriculumService disciplineCurriculumService = context.getBean(DisciplineCurriculumService.class);
        CurriculumSlotService curriculumSlotService = context.getBean(CurriculumSlotService.class);
        ThemeLessonService themeLessonService = context.getBean(ThemeLessonService.class);
        SlotChainService slotChainService = context.getBean(SlotChainService.class);

        System.out.println(slotChainService.getByID(1).toString());

        Discipline disciplineMA = disciplineService.getById(1);
        Discipline disciplineAG = disciplineService.getById(2);
        Discipline disciplineIR = disciplineService.getById(3);


        int idEducator = 0;
        Educator educatorMathLecturer = new Educator(++idEducator, "Лектор А.А.");
        educatorMathLecturer.addDefaultPriority();
        educatorMathLecturer.addConstraint(DateUtils.parseDateFlexible("2025-09-06"), DateUtils.parseDateFlexible("2025-09-15"), KindOfConstraints.BUSINESS_TRIP);

        Educator educatorMathPractise1 = new Educator(++idEducator, "Практик1 А.А.");
        educatorMathPractise1.addDefaultPriority();
        educatorMathPractise1.addConstraint(DateUtils.parseDateFlexible("2025-09-20"), DateUtils.parseDateFlexible("2025-09-27"), KindOfConstraints.BUSINESS_TRIP);

        Educator educatorAG = new Educator(++idEducator, "Аналитик Г.А.");
        educatorAG.addConstraint(DateUtils.parseDateFlexible("2025-09-20"), DateUtils.parseDateFlexible("2025-09-30"), KindOfConstraints.VACATION);
        educatorAG.addConstraint(DateUtils.parseDateFlexible("2025-12-29"), DateUtils.parseDateFlexible("2026-01-12"), KindOfConstraints.VACATION);


        List<Group> groups = List.of
                (new Group(1, "31", 10, new Auditorium(1, "306-4", 110)),
                        new Group(2, "33", 13, new Auditorium(2, "205-4", 30)),
                        new Group(3, "34", 14, new Auditorium(3, "313-4", 30)),
                        new Group(4, "35-1", 25, new Auditorium(4, "217-4", 30)),
                        new Group(5, "35-2", 24, new Auditorium(5, "206-4", 39)),
                        new Group(6, "36", 18, new Auditorium(6, "312-4", 30)));

        List<GroupCombination> groupCombinations = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 31
                new GroupCombination(List.of(groups.get(1), groups.get(2))), // Группы 33 и 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))), // Группа 35-2
                new GroupCombination(List.of(groups.get(5)))  // Группа 36
        );


        DisciplineCurriculum curriculumMA = disciplineCurriculumService.getById(1);
        System.out.println(curriculumMA.toString());

        List<CurriculumSlot> curriculumSlotListMA = curriculumSlotService.getAllSlotsForDiscipline(curriculumMA);
        curriculumMA.setCurriculumSlots(curriculumSlotListMA);


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
        groupCombinationEducatorMA.put(groupCombinations.get(0), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(1), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(2), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(3), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(4), educatorMathLecturer);

        Map<GroupCombination, Educator> groupCombinationEducatorAG = new HashMap<>();
        groupCombinationEducatorAG.put(groupCombinations.get(0), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(1), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(2), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(3), educatorAG);
        groupCombinationEducatorAG.put(groupCombinations.get(4), educatorAG);


        List<Educator> educatorsMA = List.of(educatorMathLecturer);
        List<Educator> educatorsAG = List.of(educatorAG);


        //List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, logicSchemaMA, groupCombinations, educatorMathLecturer, groupCombinationEducatorMA);
        List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, curriculumMA, groupCombinations, educatorMathLecturer, groupCombinationEducatorMA);


        //List<Lesson> logicSchemaStudyAG = createLessonsDiscipline(disciplineAG, Lesson.class, logicSchemaAG, groupCombinations, educatorAG, groupCombinationEducatorAG);


        ScheduleGrid scheduleGrid = new ScheduleGrid();

        DistributionDiscipline distributionDisciplineMA = new DistributionDiscipline(scheduleGrid, logicSchemaStudyMA, educatorsMA);
        distributionDisciplineMA.distributeLessons();

        /*DistributionDiscipline distributionDisciplineAG = new DistributionDiscipline(scheduleGrid, logicSchemaStudyAG, educatorsAG);
        distributionDisciplineAG.distributeLessons();*/


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

        ScheduleExporter.exportToExcel(scheduleGrid, educatorMathLecturer, educatorMathLecturer.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorMathPractise1, educatorMathPractise1.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorAG, educatorAG.getName());


        System.out.println();

        //context.close();
    }
}