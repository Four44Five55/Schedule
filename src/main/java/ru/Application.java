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
import java.time.DayOfWeek;
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
        Educator educatorMathLecturer = new Educator(++idEducator, "Математик Л.А.");
        educatorMathLecturer.addDefaultPriority();
        educatorMathLecturer.addConstraint(DateUtils.parseDateFlexible("2025-09-06"), DateUtils.parseDateFlexible("2025-09-15"), KindOfConstraints.BUSINESS_TRIP);

        Educator educatorMathPractise1 = new Educator(++idEducator, "Практик1 А.А.");
        educatorMathPractise1.addDefaultPriority();
        educatorMathPractise1.addConstraint(DateUtils.parseDateFlexible("2025-09-20"), DateUtils.parseDateFlexible("2025-09-27"), KindOfConstraints.BUSINESS_TRIP);

        Educator educatorAG = new Educator(++idEducator, "Аналитик Л.А.");
        educatorAG.addConstraint(DateUtils.parseDateFlexible("2025-09-09"), DateUtils.parseDateFlexible("2025-09-20"), KindOfConstraints.VACATION);
        educatorAG.addConstraint(DateUtils.parseDateFlexible("2025-09-01"), DateUtils.parseDateFlexible("2026-01-26"), KindOfConstraints.VACATION, DayOfWeek.MONDAY);

        Educator educatorIR = new Educator(++idEducator, "Историк Л.Ф.");


        List<Group> groups = List.of
                (new Group(1, "31", 10, new Auditorium(1, "306-4", 110)),
                        new Group(2, "33", 13, new Auditorium(2, "205-4", 30)),
                        new Group(3, "34", 14, new Auditorium(3, "313-4", 30)),
                        new Group(4, "35-1", 25, new Auditorium(4, "217-4", 30)),
                        new Group(5, "35-2", 24, new Auditorium(5, "206-4", 39)),
                        new Group(6, "36", 18, new Auditorium(6, "312-4", 30)));

        groups.forEach(group -> {group.addConstraint(DateUtils.parseDateFlexible("2025-12-25"), DateUtils.parseDateFlexible("2026-01-08"), KindOfConstraints.VACATION);});
        groups.forEach(group -> group.addConstraint(DateUtils.parseDateFlexible("2026-01-20"), DateUtils.parseDateFlexible("2026-01-31"), KindOfConstraints.EXAM_SESSION));

        List<GroupCombination> groupCombinations = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 31
                new GroupCombination(List.of(groups.get(1), groups.get(2))), // Группы 33 и 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))), // Группа 35-2
                new GroupCombination(List.of(groups.get(5)))  // Группа 36
        );


        DisciplineCurriculum curriculumMA = disciplineCurriculumService.getById(1);
        DisciplineCurriculum curriculumAG = disciplineCurriculumService.getById(3);
        DisciplineCurriculum curriculumIR = disciplineCurriculumService.getById(2);

        List<CurriculumSlot> curriculumSlotListMA = curriculumSlotService.getAllSlotsForDiscipline(curriculumMA);
        List<CurriculumSlot> curriculumSlotListAG = curriculumSlotService.getAllSlotsForDiscipline(curriculumAG);
        List<CurriculumSlot> curriculumSlotListIR = curriculumSlotService.getAllSlotsForDiscipline(curriculumIR);


        curriculumMA.setCurriculumSlots(curriculumSlotListMA);
        curriculumAG.setCurriculumSlots(curriculumSlotListAG);
        curriculumIR.setCurriculumSlots(curriculumSlotListIR);


/*        Map<GroupCombination, Educator> groupCombinationEducatorMA = new HashMap<>();
        groupCombinationEducatorMA.put(groupCombinations.get(0), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(1), educatorMathLecturer);
        groupCombinationEducatorMA.put(groupCombinations.get(2), educatorMathPractise1);
        groupCombinationEducatorMA.put(groupCombinations.get(3), educatorMathPractise1);
        groupCombinationEducatorMA.put(groupCombinations.get(4), educatorMathPractise1);

        List<Educator> educators = List.of(educatorMathLecturer, educatorMathPractise1);*/


        Map<GroupCombination, Educator> groupCombinationEducatorMA = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorAG = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorIR = new HashMap<>();

        for (int i = 0; i < 5; i++) {
            groupCombinationEducatorMA.put(groupCombinations.get(i), educatorMathLecturer);
            groupCombinationEducatorAG.put(groupCombinations.get(i), educatorAG);
            groupCombinationEducatorIR.put(groupCombinations.get(i), educatorIR);
        }


        List<Educator> educatorsMA = List.of(educatorMathLecturer);
        List<Educator> educatorsAG = List.of(educatorAG);
        List<Educator> educatorsIR = List.of(educatorIR);


        List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, curriculumMA, groupCombinations, educatorMathLecturer, groupCombinationEducatorMA);
        List<Lesson> logicSchemaStudyAG = createLessonsDiscipline(disciplineAG, Lesson.class, curriculumAG, groupCombinations, educatorAG, groupCombinationEducatorAG);
        List<Lesson> logicSchemaStudyIR = createLessonsDiscipline(disciplineIR, Lesson.class, curriculumIR, groupCombinations, educatorIR, groupCombinationEducatorIR);

        ScheduleGrid scheduleGrid = new ScheduleGrid();

        DistributionDiscipline distributionDisciplineMA = new DistributionDiscipline(scheduleGrid, logicSchemaStudyMA, educatorsMA, slotChainService, curriculumSlotService);
        distributionDisciplineMA.distributeLessons();

        DistributionDiscipline distributionDisciplineAG = new DistributionDiscipline(scheduleGrid, logicSchemaStudyAG, educatorsAG, slotChainService, curriculumSlotService);
        distributionDisciplineAG.distributeLessons();

        DistributionDiscipline distributionDisciplineIR = new DistributionDiscipline(scheduleGrid, logicSchemaStudyIR, educatorsIR, slotChainService, curriculumSlotService);
        distributionDisciplineIR.distributeLessons();

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
        //ScheduleExporter.exportToExcel(scheduleGrid, educatorMathPractise1, educatorMathPractise1.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorAG, educatorAG.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorIR, educatorIR.getName());

        System.out.println();

        context.close();
    }
}