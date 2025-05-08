package ru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.entity.*;
import ru.enums.KindOfConstraints;
import ru.enums.KindOfStudy;
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
        SpringApplication.run(Application.class, args);



/*        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:schedule_db.db");


        Discipline disciplineMA = null;

        try (Connection connection = dataSource.getConnection()) {

            // Работа с репозиторием
            CurriculumRepository repo = new CurriculumRepository(connection);
            disciplineMA = new CurriculumRepository(connection).loadDiscipline(1);


            //Discipline disciplineMA = repo.loadDiscipline(1);
            DisciplineCurriculum curriculumMA = repo.findCurriculumByDisciplineId(1);

            // Вывод результатов
            CurriculumPrinter.print(curriculumMA);


        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }*/


        int idEducator = 0;
        //Discipline disciplineMath = new Discipline("МА");
        Discipline disciplineMA = new Discipline(1, "Математический анализ", "МА");
        Discipline disciplineAG = new Discipline(2, "Аналитическая геометрия и линейная алгебра", "АГ");

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


        List<Lesson> logicSchemaStudyMA = createLessonsDiscipline(disciplineMA, Lesson.class, logicSchemaMA, groupCombinations, educatorMathLecturer, groupCombinationEducatorMA);
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

        ScheduleExporter.exportToExcel(scheduleGrid, educatorMathLecturer, educatorMathLecturer.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorMathPractise1, educatorMathPractise1.getName());
        ScheduleExporter.exportToExcel(scheduleGrid, educatorAG, educatorAG.getName());


        System.out.println();


    }
}