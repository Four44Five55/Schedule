package ru.services;

import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.entity.ScheduleGrid;
import ru.entity.factories.CellForLessonFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DistributionDiscipline {
    ScheduleGrid scheduleGrid;
    List<Lesson> lessons;
    Map<Educator, List<AbstractLesson>> lessonsMapEducator = new HashMap<>();

    public DistributionDiscipline(ScheduleGrid scheduleGrid, List<Lesson> lessons) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;

    }

    private void FillMapEducatorLessons() {
        List<Educator> uniqueEducators= lessons.stream() //создание потока из списка
                .map(AbstractLesson::getEducators)// преобразование каждого занятие в список преподов
                .flatMap(List::stream) //объединение списков преподавателей кажд занятия в поток
                .distinct() //удаление дубликатов
                .toList();
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
