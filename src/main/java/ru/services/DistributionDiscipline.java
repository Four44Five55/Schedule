package ru.services;

import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;
import ru.entity.factories.CellForLessonFactory;
import ru.enums.TimeSlotPair;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ForkJoinPool;

public class DistributionDiscipline {
    ScheduleGrid scheduleGrid;
    List<Lesson> lessons;
    List<Educator> educators;

    public DistributionDiscipline(ScheduleGrid scheduleGrid, List<Lesson> lessons, List<Educator> educators) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;
        this.educators = educators;
    }

    public void distributeLessons() {
        for (Educator educator : educators) {
            distributeLessonsForEducator(educator);
        }
    }

    public void distributeLessonsForEducator(Educator educator) {
        ListIterator<Lesson> lessonIterator = lessons.listIterator();
        List<CellForLesson> cellForLessons = CellForLessonFactory.getAllOrderedCells();
        Iterator<CellForLesson> cellIterator = cellForLessons.iterator();


        while (lessonIterator.hasNext()) {
            Lesson lesson = lessonIterator.next();

            if (lesson.isEntityUsed(educator)) {

                // Перебираем доступные ячейки
                while (cellIterator.hasNext()) {
                    CellForLesson cell = cellIterator.next();

                    if (!(cell.getTimeSlotPair() == TimeSlotPair.FOURTH)) {
                        // Проверяем занятость сущностей занятие в текущей ячейке
                        boolean allFree = lesson.getAllMaterialEntity().stream()
                                .allMatch(entity -> entity.isFree(scheduleGrid, cell));

                        if (allFree) {
                            // Назначаем занятие в ячейку
                            scheduleGrid.addLessonToCell(cell, lesson);

                            // Удаляем ячейку из доступных, если она больше не может использоваться
                            cellIterator.remove();

                            break;
                        }
                    }
                }

            }

        }
    }

}
