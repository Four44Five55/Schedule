package ru.services;

import ru.abstracts.AbstractLesson;
import ru.abstracts.AbstractMaterialEntity;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;
import ru.entity.factories.CellForLessonFactory;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class DistributionDiscipline {
    ScheduleGrid scheduleGrid;
    List<Lesson> lessons;

    public DistributionDiscipline(ScheduleGrid scheduleGrid, List<Lesson> lessons) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;
    }

    public void distributeLessons() {
        ListIterator<Lesson> lessonIterator = lessons.listIterator();
        List<CellForLesson> cellForLessons = CellForLessonFactory.getAllOrderedCells();
        Iterator<CellForLesson> cellIterator = cellForLessons.iterator();

        while (lessonIterator.hasNext()) {
            Lesson lesson = lessonIterator.next();

            // Перебираем доступные ячейки
            while (cellIterator.hasNext()) {
                CellForLesson cell = cellIterator.next();

                // Проверяем занятость сущностей урока в текущей ячейке
                boolean allFree = lesson.getAllMaterialEntity().stream()
                        .allMatch(entity -> entity.isFree(scheduleGrid, cell));

                if (allFree) {
                    // Назначаем урок в ячейку
                    scheduleGrid.addLessonToCell(cell, lesson);

                    // Удаляем ячейку из доступных, если она больше не может использоваться
                    cellIterator.remove();

                    break;
                }
            }
        }


    }
}
