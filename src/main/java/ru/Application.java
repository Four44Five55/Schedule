package ru;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.services.*;
import ru.services.solver.ScheduleWorkspace;
import ru.services.solver.model.ScheduleGrid;

import java.sql.SQLException;

@SpringBootApplication
public class Application {
    public static void main(String[] args) throws SQLException {
        SpringApplication.run(Application.class, args);
 /*       ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
        EducatorService educatorService = context.getBean(EducatorService.class);
        GroupService groupService = context.getBean(GroupService.class);
        AuditoriumService auditoriumService = context.getBean(AuditoriumService.class);
        DisciplineCourseService disciplineCourseService = context.getBean(DisciplineCourseService.class);
        ConstraintService constraintService = context.getBean(ConstraintService.class);
        LessonFactory lessonFactory = context.getBean(LessonFactory.class);

        ScheduleGenerationService service = new ScheduleGenerationService(educatorService, groupService, auditoriumService, disciplineCourseService, constraintService, lessonFactory);
        service.generateForCourse(701);
        System.out.println();
        context.close();*/
         /*  ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);


       DisciplineService disciplineService = context.getBean(DisciplineService.class);
        DisciplineCurriculumService disciplineCurriculumService = context.getBean(DisciplineCurriculumService.class);
        CurriculumSlotService curriculumSlotService = context.getBean(CurriculumSlotService.class);
        ThemeLessonService themeLessonService = context.getBean(ThemeLessonService.class);
        SlotChainService slotChainService = context.getBean(SlotChainService.class);

        Discipline disciplineAG = disciplineService.getById(2);
        Discipline disciplineIR = disciplineService.getById(3);

        Discipline disciplineIYa = disciplineService.getById(4); //Иняз
        Discipline disciplineFL = disciplineService.getById(5);//Философия
        //Культурология добавить
        Discipline disciplineMA = disciplineService.getById(11);//Математический анализ
        Discipline disciplineSGM = disciplineService.getById(7); //СГМ
        Discipline disciplineF = disciplineService.getById(8); //Физика
        Discipline disciplineBZH = disciplineService.getById(9);//Безопасность ЖД
        Discipline disciplineIT = disciplineService.getById(10);

        int idEducator = 0;

        Educator educatorIYa = new Educator(++idEducator, "Иняз П.П.");
        educatorIYa.addDefaultPriority();
        educatorIYa.getPriority().removeTimeSlot(TimeSlotPair.FIRST);
        educatorIYa.getPriority().addTimeSlot(TimeSlotPair.FOURTH);
        educatorIYa.addConstraint(DateUtils.parseDateFlexible("2025-10-16"), DateUtils.parseDateFlexible("2025-10-31"), KindOfConstraints.VACATION);

        Educator educatorFL = new Educator(++idEducator, "Философ Л.Л.");

        Educator educatorMA = new Educator(++idEducator, "Математик Л.А.");
        educatorMA.addDefaultPriority();
        educatorMA.addConstraint(DateUtils.parseDateFlexible("2025-10-20"), DateUtils.parseDateFlexible("2025-10-27"), KindOfConstraints.VACATION);

        Educator educatorSGM = new Educator(++idEducator, "СГМ Л.П.");
        educatorSGM.addDefaultPriority();

        Educator educatorF = new Educator(++idEducator, "Физик Л.Л.");
        educatorF.addDefaultPriority();

        Educator educatorBZH = new Educator(++idEducator, "Безопасный А.А.");
        educatorBZH.addDefaultPriority();

        Educator educatorIT = new Educator(++idEducator, "Информационный А.А.");
        educatorIT.addDefaultPriority();


        List<Group> groups = List.of
                (new Group(1, "31", 10, new Auditorium(1, "306-4", 110)),
                        new Group(2, "33", 13, new Auditorium(2, "205-4", 30)),
                        new Group(3, "34", 14, new Auditorium(3, "313-4", 30)),
                        new Group(4, "35-1", 25, new Auditorium(4, "217-4", 30)),
                        new Group(5, "35-2", 24, new Auditorium(5, "206-4", 39)),
                        new Group(6, "36", 18, new Auditorium(6, "312-4", 30)));

        groups.forEach(group -> {
            group.addConstraint(DateUtils.parseDateFlexible("2025-12-25"), DateUtils.parseDateFlexible("2026-01-08"), KindOfConstraints.VACATION);
        });
        groups.forEach(group -> group.addConstraint(DateUtils.parseDateFlexible("2026-01-20"), DateUtils.parseDateFlexible("2026-01-31"), KindOfConstraints.EXAM_SESSION));


        //
        DisciplineCurriculum curriculumIYa = disciplineCurriculumService.findByDisciplineId(disciplineIYa.getId());
        DisciplineCurriculum curriculumFL = disciplineCurriculumService.findByDisciplineId(disciplineFL.getId());
        DisciplineCurriculum curriculumMA = disciplineCurriculumService.findByDisciplineId(disciplineMA.getId());
        DisciplineCurriculum curriculumSGM = disciplineCurriculumService.findByDisciplineId(disciplineSGM.getId());
        DisciplineCurriculum curriculumF = disciplineCurriculumService.findByDisciplineId(disciplineF.getId());
        DisciplineCurriculum curriculumBZH = disciplineCurriculumService.findByDisciplineId(disciplineBZH.getId());
        DisciplineCurriculum curriculumIT = disciplineCurriculumService.findByDisciplineId(disciplineIT.getId());

        //
        List<CurriculumSlot> curriculumSlotListIYa = curriculumSlotService.getAllSlotsForDiscipline(disciplineIYa.getId());
        List<CurriculumSlot> curriculumSlotListFL = curriculumSlotService.getAllSlotsForDiscipline(disciplineFL.getId());
        List<CurriculumSlot> curriculumSlotListMA = curriculumSlotService.getAllSlotsForDiscipline(disciplineMA.getId());
        List<CurriculumSlot> curriculumSlotListSGM = curriculumSlotService.getAllSlotsForDiscipline(disciplineSGM.getId());
        List<CurriculumSlot> curriculumSlotListF = curriculumSlotService.getAllSlotsForDiscipline(disciplineF.getId());
        List<CurriculumSlot> curriculumSlotListBZH = curriculumSlotService.getAllSlotsForDiscipline(disciplineBZH.getId());
        List<CurriculumSlot> curriculumSlotListIT = curriculumSlotService.getAllSlotsForDiscipline(disciplineIT.getId());

        //
        curriculumIYa.setCurriculumSlots(curriculumSlotListIYa);
        curriculumFL.setCurriculumSlots(curriculumSlotListFL);
        curriculumMA.setCurriculumSlots(curriculumSlotListMA);
        curriculumSGM.setCurriculumSlots(curriculumSlotListSGM);
        curriculumF.setCurriculumSlots(curriculumSlotListF);
        curriculumBZH.setCurriculumSlots(curriculumSlotListBZH);
        curriculumIT.setCurriculumSlots(curriculumSlotListIT);


*//*        Map<GroupCombination, Educator> groupCombinationEducatorMA = new HashMap<>();
        groupCombinationEducatorMA.put(groupCombinations.get(0), educatorMA);
        groupCombinationEducatorMA.put(groupCombinations.get(1), educatorMA);
        groupCombinationEducatorMA.put(groupCombinations.get(2), educatorMathPractise1);
        groupCombinationEducatorMA.put(groupCombinations.get(3), educatorMathPractise1);
        groupCombinationEducatorMA.put(groupCombinations.get(4), educatorMathPractise1);

        List<Educator> educators = List.of(educatorMA, educatorMathPractise1);*//*


        Map<GroupCombination, Educator> groupCombinationEducatorIYa = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorFL = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorMA = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorSGM = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorF = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorBZH = new HashMap<>();
        Map<GroupCombination, Educator> groupCombinationEducatorIT = new HashMap<>();

        List<GroupCombination> groupCombinations = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 31
                new GroupCombination(List.of(groups.get(1), groups.get(2))), // Группы 33 и 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))), // Группа 35-2
                new GroupCombination(List.of(groups.get(5)))  // Группа 36
        );
        List<GroupCombination> groupCombinationsFL = List.of(
                new GroupCombination(List.of(groups.get(0), groups.get(5))), // Группа 31 и 36
                new GroupCombination(List.of(groups.get(1), groups.get(2))), // Группы 33 и 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))) // Группа 35-2
        );

        List<GroupCombination> groupCombinationsIYa = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 31
                new GroupCombination(List.of(groups.get(1))),// Группы 33
                new GroupCombination(List.of(groups.get(2))),// Группы 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))), // Группа 35-2
                new GroupCombination(List.of(groups.get(5)))  // Группа 36
        );
        List<GroupCombination> groupCombinationsIT = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 31
                new GroupCombination(List.of(groups.get(1))),// Группы 33
                new GroupCombination(List.of(groups.get(2))),// Группы 34
                new GroupCombination(List.of(groups.get(3))), // Группа 35-1
                new GroupCombination(List.of(groups.get(4))) // Группа 35-2
        );

        for (int i = 0; i < 4; i++) {
            groupCombinationEducatorFL.put(groupCombinationsFL.get(i), educatorFL);
        }

        for (int i = 0; i < 5; i++) {
            groupCombinationEducatorMA.put(groupCombinations.get(i), educatorMA);
            groupCombinationEducatorSGM.put(groupCombinations.get(i), educatorSGM);
            groupCombinationEducatorF.put(groupCombinations.get(i), educatorF);
            groupCombinationEducatorBZH.put(groupCombinations.get(i), educatorBZH);
            groupCombinationEducatorIT.put(groupCombinationsIT.get(i), educatorIT);
        }

        for (int i = 0; i < 6; i++) {
            groupCombinationEducatorIYa.put(groupCombinationsIYa.get(i), educatorIYa);
        }


        //
        List<Educator> educatorsIYa = List.of(educatorIYa);
        List<Educator> educatorsFL = List.of(educatorFL);
        List<Educator> educatorsMA = List.of(educatorMA);
        List<Educator> educatorsSGM = List.of(educatorSGM);
        List<Educator> educatorsF = List.of(educatorF);
        List<Educator> educatorsBZH = List.of(educatorBZH);
        List<Educator> educatorsIT = List.of(educatorIT);
        //
        List<Lesson> logicSchemaStudyIYa = createLessonsDiscipline(disciplineIYa, Lesson.class, curriculumIYa, groupCombinationsIYa, educatorIYa, groupCombinationEducatorIYa);
        List<Lesson> logicSchemaStudyFL = createLessonsDiscipline(disciplineFL, Lesson.class, curriculumFL, groupCombinationsFL, educatorFL, groupCombinationEducatorFL);
        List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, curriculumMA, groupCombinations, educatorMA, groupCombinationEducatorMA);
        List<Lesson> logicSchemaStudySGM = createLessonsDiscipline(disciplineSGM, Lesson.class, curriculumSGM, groupCombinations, educatorSGM, groupCombinationEducatorSGM);
        List<Lesson> logicSchemaStudyF = createLessonsDiscipline(disciplineF, Lesson.class, curriculumF, groupCombinations, educatorF, groupCombinationEducatorF);
        List<Lesson> logicSchemaStudyBZH = createLessonsDiscipline(disciplineBZH, Lesson.class, curriculumBZH, groupCombinations, educatorBZH, groupCombinationEducatorBZH);
        List<Lesson> logicSchemaStudyIT = createLessonsDiscipline(disciplineIT, Lesson.class, curriculumIT, groupCombinationsIT, educatorIT, groupCombinationEducatorIT);

        ScheduleGrid scheduleGridMain = new ScheduleGrid();
        ScheduleGrid scheduleGridIYa = new ScheduleGrid();
        ScheduleGrid scheduleGridFL = new ScheduleGrid();
        ScheduleGrid scheduleGridMA = new ScheduleGrid();
        ScheduleGrid scheduleGridSGM = new ScheduleGrid();
        ScheduleGrid scheduleGridF = new ScheduleGrid();
        ScheduleGrid scheduleGridBZH = new ScheduleGrid();
        ScheduleGrid scheduleGridIT = new ScheduleGrid();


        DistributionDisciplineUniform distributionDUIYa = new DistributionDisciplineUniform(scheduleGridIYa, logicSchemaStudyIYa, educatorsIYa, groupCombinationsIYa, slotChainService, curriculumSlotService);
        distributionDUIYa.distributeUniformLessons();

        DistributionDisciplineUniform distributionDUFL = new DistributionDisciplineUniform(scheduleGridFL, logicSchemaStudyFL, educatorsFL, groupCombinationsFL, slotChainService, curriculumSlotService);
        distributionDUFL.distributeUniformLessons();

        DistributionDisciplineUniform distributionDUMA = new DistributionDisciplineUniform(scheduleGridMA, logicSchemaStudyMA, educatorsMA, groupCombinations, slotChainService, curriculumSlotService);
        distributionDUMA.distributeUniformLessons();

        DistributionDisciplineUniform distributionDUSGM = new DistributionDisciplineUniform(scheduleGridSGM, logicSchemaStudySGM, educatorsSGM, groupCombinations, slotChainService, curriculumSlotService);
        distributionDUSGM.distributeUniformLessons();

        DistributionDisciplineUniform distributionDUF = new DistributionDisciplineUniform(scheduleGridF, logicSchemaStudyF, educatorsF, groupCombinations, slotChainService, curriculumSlotService);
        distributionDUF.distributeUniformLessons();

        DistributionDisciplineUniform distributionDUBZH = new DistributionDisciplineUniform(scheduleGridBZH, logicSchemaStudyBZH, educatorsBZH, groupCombinations, slotChainService, curriculumSlotService);
        distributionDUBZH.distributeUniformLessons();

        DistributionDisciplineUniform distributionDUIT = new DistributionDisciplineUniform(scheduleGridIT, logicSchemaStudyIT, educatorsIT, groupCombinationsIT, slotChainService, curriculumSlotService);
        distributionDUIT.distributeUniformLessons();


        UnifiedScheduleManager manager = new UnifiedScheduleManager(IGrid.START_DATE, IGrid.END_DATE, slotChainService, scheduleGridMain);

        manager.addDisciplineSchedule(disciplineFL.getName(), scheduleGridFL);
        manager.addDisciplineSchedule(disciplineMA.getName(), scheduleGridMA);
        manager.addDisciplineSchedule(disciplineSGM.getName(), scheduleGridSGM);
        manager.addDisciplineSchedule(disciplineF.getName(), scheduleGridF);
        manager.addDisciplineSchedule(disciplineBZH.getName(), scheduleGridBZH);
        manager.addDisciplineSchedule(disciplineIT.getName(), scheduleGridIT);
        manager.addDisciplineSchedule(disciplineIYa.getName(), scheduleGridIYa);

        // Выводим статистику распределения занятий
        manager.printStatistics();

        //Экспорт в Excel
        createExcelsFiles(groups, scheduleGridIYa, educatorIYa, disciplineIYa.getName());
        createExcelsFiles(groups, scheduleGridFL, educatorFL, disciplineFL.getName());
        createExcelsFiles(groups, scheduleGridMA, educatorMA, disciplineMA.getName());
        createExcelsFiles(groups, scheduleGridSGM, educatorSGM, disciplineSGM.getName());
        createExcelsFiles(groups, scheduleGridF, educatorF, disciplineF.getName());
        createExcelsFiles(groups, scheduleGridBZH, educatorBZH, disciplineBZH.getName());
        createExcelsFiles(groups, scheduleGridIT, educatorIT, disciplineIT.getName());

        System.out.println(scheduleGridMain.getAmountDays());

        List<AbstractMaterialEntity> entities = List.of(educatorIYa, educatorFL, educatorMA, educatorSGM, educatorF, educatorBZH, educatorIT);
        createExcelsFiles(groups, manager.getUnifiedSchedule(), entities, "Общее расписание");

        System.out.println();

        context.close();
    }

    private static void createExcelsFiles(List<Group> groups, ScheduleGrid scheduleGrid, Educator educator, String subDirectory) {
        for (Group group : groups) {
            ScheduleExporter.exportToExcel(scheduleGrid, group, group.getName(), subDirectory);
        }
        ScheduleExporter.exportToExcel(scheduleGrid, educator, educator.getName(), subDirectory);
    }

    private static void createExcelsFiles(List<Group> groups, ScheduleGrid scheduleGrid, List<AbstractMaterialEntity> entities, String subDirectory) {
        for (Group group : groups) {
            ScheduleExporter.exportToExcel(scheduleGrid, group, group.getName(), subDirectory);
        }
        for (AbstractMaterialEntity entity : entities) {
            ScheduleExporter.exportToExcel(scheduleGrid, entity, entity.getName(), subDirectory);
        }*/
    }

    /**
     * Координирует генерацию расписания и последующий экспорт в Excel для всех сущностей.
     */
    @Bean
    public CommandLineRunner commandLineRunner(ScheduleGenerationService generationService,
                                               ExcelExportService exportService,
                                               EducatorService educatorService,
                                               GroupService groupService,
                                               AuditoriumService auditoriumService) {
        return args -> {
            System.out.println("Запускаем генерацию расписания...");
            ScheduleWorkspace generatedWorkspace = generationService.generateForCourse(701);
            System.out.println("Генерация расписания завершена.");
            if (generatedWorkspace  == null) {
                System.err.println("Не удалось сгенерировать расписание. Экспорт отменен.");
                return; // Выходим, если расписание не создано
            }

            // --- ЭТАП 2: ЭКСПОРТ РАСПИСАНИЙ В EXCEL ---
            System.out.println("\nЗапускаем экспорт расписаний в Excel...");

            // Экспорт для каждого преподавателя
            educatorService.getAllEntities().forEach(educator ->
                    exportService.exportScheduleForEntity(generatedWorkspace, educator, educator.getName(), "Общее расписание")
            );

            // Экспорт для каждой группы
            groupService.getAllEntities().forEach(group ->
                    exportService.exportScheduleForEntity(generatedWorkspace, group, group.getName(), "Общее расписание")
            );

            // Экспорт для каждой аудитории
            auditoriumService.getAllEntities().forEach(auditorium ->
                    exportService.exportScheduleForEntity(generatedWorkspace, auditorium, auditorium.getName(), "Общее расписание")
            );

            System.out.println("\nРабота приложения завершена. Результаты находятся в папке 'output'.");
        };
    }
}
