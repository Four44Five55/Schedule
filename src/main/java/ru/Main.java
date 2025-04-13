package ru;

import ru.abstracts.AbstractLesson;
import ru.entity.*;
import ru.entity.factories.LessonFactory;
import ru.enums.KindOfStudy;
import ru.inter.IScheduleGrid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static ru.entity.factories.LessonFactory.createLessonsDiscipline;
import static ru.entity.factories.LessonFactory.createLogicStudySchemaDiscipline;


public class Main {
    public static void main(String[] args) {

        int idEducator = 0;
        Discipline disciplineMath = new Discipline("МА");


        Educator educatorMathLecturer = new Educator(++idEducator, "Лектор А.А.");
        Educator educatorMathPractise1 = new Educator(++idEducator, "Практик1 А.А.");
        Educator educatorMathPractise2 = new Educator(++idEducator, "Практик2 А.А.");

        List<Group> groups = List.of
                (new Group(1, "931", 15, new Auditorium(1, "306-4", 100)),
                        new Group(2, "933", 15, new Auditorium(2, "205-4", 30)),
                        new Group(3, "934", 15, new Auditorium(3, "313-4", 30)),
                        new Group(4, "935-1", 15, new Auditorium(4, "217-4", 30)),
                        new Group(5, "935-2", 15, new Auditorium(5, "206-4", 39)),
                        new Group(6, "936", 15, new Auditorium(6, "312-4", 30)));

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

        Map<GroupCombination, Educator> groupCombinationEducatorMap = new HashMap<>();
        groupCombinationEducatorMap.put(groupCombinations.get(0), educatorMathLecturer);
        groupCombinationEducatorMap.put(groupCombinations.get(1), educatorMathLecturer);
        groupCombinationEducatorMap.put(groupCombinations.get(2), educatorMathPractise1);
        groupCombinationEducatorMap.put(groupCombinations.get(3), educatorMathPractise1);
        groupCombinationEducatorMap.put(groupCombinations.get(4), educatorMathPractise1);


        List<Lesson> logicSchemaStudyMath = createLessonsDiscipline(disciplineMath, Lesson.class, logicSchemaMath, groupCombinations, educatorMathLecturer, groupCombinationEducatorMap);



/*        ScheduleService scheduleService = new ScheduleService(logicSchemaStudyMath, groupCombinations, educatorMathLecturer, 30.0);
        scheduleService.distributeLessons(IScheduleGrid.START_DATE, IScheduleGrid.END_DATE);


        //Экспорт в Excel
        for (Group group : groups) {
            ScheduleExporter.exportToExcel(group, group.getName());
        }


        ScheduleExporter.exportToExcel(educatorMathLecturer, educatorMathLecturer.getName());*/


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