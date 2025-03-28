package ru;

import ru.entity.*;
import ru.entity.factories.LessonFactory;
import ru.enums.KindOfStudy;
import ru.inter.IScheduleGrid;
import ru.services.ScheduleExporter;
import ru.services.ScheduleService;

import java.util.List;


public class Main {
    public static void main(String[] args)  {

        int idEducator = 0;
        Discipline disciplineMath = new Discipline("МА");


        Educator educatorMath = new Educator(++idEducator, "Иванов А.А.");
        List<Group> groups = List.of
                (new Group("931", 15, new Auditorium("306-4", 100)),
                        new Group("933", 15, new Auditorium("205-4", 30)),
                        new Group("934", 15, new Auditorium("313-4", 30)),
                        new Group("935-1", 15, new Auditorium("217-4", 30)),
                        new Group("935-2", 15, new Auditorium("206-4", 39)),
                        new Group("936", 15, new Auditorium("312-4", 30)));

        List<GroupCombination> groupCombinations = List.of(
                new GroupCombination(List.of(groups.get(0))), // Группа 931
                new GroupCombination(List.of(groups.get(1), groups.get(2))), // Группы 933 и 934
                new GroupCombination(List.of(groups.get(3))), // Группа 935-1
                new GroupCombination(List.of(groups.get(4))), // Группа 935-2
                new GroupCombination(List.of(groups.get(5)))  // Группа 936
        );



        List<KindOfStudy> logicSchemaMath = List.of(KindOfStudy.LECTURE, KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE, KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK,
                KindOfStudy.LECTURE, KindOfStudy.LECTURE, KindOfStudy.LECTURE,
                KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK, KindOfStudy.PRACTICAL_WORK);

        List<Lesson> logicSchemaStudyMath = LessonFactory.createLogicStudySchemaDiscipline(disciplineMath, Lesson.class, logicSchemaMath, educatorMath);

        ScheduleService scheduleService = new ScheduleService(logicSchemaStudyMath, groupCombinations, educatorMath,30.0);
        scheduleService.distributeLessons(IScheduleGrid.START_DATE, IScheduleGrid.END_DATE);


        //Экспорт в Excel
        for (Group group : groups) {
            ScheduleExporter.exportToExcel(group, group.getName());
        }


        ScheduleExporter.exportToExcel(educatorMath, educatorMath.getName());


        System.out.println();
/*        List<String> disciplineNames = List.of("Математика", "Физика", "Программирование");
        List<String> educatorNames = List.of("Иванова И.И.", "Петрова А.А.", "Сидорова Н.Н.");
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellsForDateRange(startDate, endDate);

        // Создаем список дисциплин с помощью фабрики
        List<Discipline> disciplines = DisciplineFactory.createDisciplines(disciplineNames, Discipline.class);
        List<Educator> educators = EducatorFactory.createEducators(educatorNames, Educator.class);
        List<Lesson> lessonList= LessonFactory.createLessons(disciplines, Lesson.class,KindOfStudy.GROUP_WORK,educators);
        }*/

    }
}