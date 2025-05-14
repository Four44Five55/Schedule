/*
package ru.services;

import ru.abstracts.AbstractAuditorium;
import ru.abstracts.AbstractLesson;
import ru.entity.*;
import ru.entity.factories.CellForLessonFactory;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ScheduleService {
    private ScheduleGrid scheduleGrid;
    private final List<Lesson> logicSchemaStudyDiscipline;
    private Iterator<CellForLesson> cellIterator;//чтобы избежать повторного прохода по уже проверенным ячейкам
    private double maxPercentage; // Допустимый процент 4 пар

    public ScheduleService(ScheduleGrid scheduleGrid, List<Lesson> logicSchemaStudyDiscipline, List<GroupCombination> groupCombinations, Educator educator, double maxPercentage) {
        this.scheduleGrid = scheduleGrid;
        this.logicSchemaStudyDiscipline = logicSchemaStudyDiscipline;
        this.maxPercentage = maxPercentage; // Инициализируем maxPercentage
    }

    public double getMaxPercentage() {
        return maxPercentage;
    }

    public void setMaxPercentage(double maxPercentage) {
        this.maxPercentage = maxPercentage;
    }

    private int calculateFourPairsForOneCombination() {
        int numberOfForPairs = (int) ((maxPercentage / 100 * calculateTeacherWorkingHours()) / groupCombinations.size());

        return numberOfForPairs;
    }

    private int calculateTeacherWorkingHours() {

        int hoursLecture = (int) logicSchemaStudyDiscipline.stream()
                .filter(lesson -> lesson.getKindOfStudy() == KindOfStudy.LECTURE)
                .count();

        int hours = hoursLecture + groupCombinations.size() * (logicSchemaStudyDiscipline.size() - hoursLecture);
        return hours;
    }

    */
/**
     * Распределяет занятия между группами и преподавателем.
     *
     * @param startDate начальная дата расписания
     * @param endDate   конечная дата расписания
     *//*

    public void distributeLessons(LocalDate startDate, LocalDate endDate) {
        int lessonIndex = 0; // Индекс для занятий
        // Сбрасываем итератор перед началом нового распределения
        cellIterator = null;
        int sssss = calculateFourPairsForOneCombination();
        List<CellForLesson> cellForLessons = CellForLessonFactory.createCellsForDateRange(startDate, endDate);

        // Итерация по существующим ячейкам для занятий
        for (int indexCurrentCell = 0; indexCurrentCell < cellForLessons.size(); indexCurrentCell++) {
            if (lessonIndex >= logicSchemaStudyDiscipline.size()) {
                break; // Все занятия распределены
            }

            Lesson currentLesson = logicSchemaStudyDiscipline.get(lessonIndex);
            CellForLesson currentCell = cellForLessons.get(indexCurrentCell);

*/
/*            // Проверяем, есть ли уже занятие у преподавателя в этот день
            if (hasEducatorLessonOnDate(currentCell.getDate())) {
                continue; // Пропускаем день, если у преподавателя уже есть занятие
            }*//*



            if (currentLesson.getKindOfStudy() == KindOfStudy.LECTURE) {
                if (distributeLecture(currentCell, currentLesson)) {
                    lessonIndex++;
                }
            } else {
                if (distributeOtherLessons(currentCell, currentLesson, indexCurrentCell, cellForLessons, logicSchemaStudyDiscipline.size())) {
                    lessonIndex++;
                }
            }
        }
        int i = 1;
    }

    */
/**
     * Распределяет лекцию для всех групп и преподавателя.
     *
     * @param lectureCell   ячейка для лекции
     * @param currentLesson текущее занятие (лекция)
     * @return true, если лекция успешно распределена
     *//*

    private boolean distributeLecture(CellForLesson lectureCell, Lesson currentLesson) {
        // Исключения распределения лекции 4ой парой
        if (lectureCell.getTimeSlotPair() == TimeSlotPair.FOURTH) {
            return false;
        }
        // Проверка, свободен ли слот у преподавателя
        if (isBusyEntityInCell(educator, lectureCell)) {
            // Проверка, свободен ли слот у всех групп
            boolean allGroupsFree = groupCombinations.stream()
                    .flatMap(combination -> combination.getGroups().stream())
                    .allMatch(group -> isBusyEntityInCell(group, lectureCell));

            if (allGroupsFree) {


                // Проверяем, можно ли назначить лекцию на 4 пару
                if (lectureCell.getTimeSlotPair() == TimeSlotPair.FOURTH) {
                    boolean canAssignToEducator = canAssignFourthPairLesson(educator.getScheduleGridMap(), logicSchemaStudyDiscipline.size());
                    boolean canAssignToGroup = groupCombinations.stream()
                            .flatMap(combination -> combination.getGroups().stream())
                            .allMatch(group -> canAssignFourthPairLesson(group.getScheduleGridMap(), logicSchemaStudyDiscipline.size()));

                    if (!canAssignToEducator || !canAssignToGroup) {
                        return false; // Нельзя назначить лекцию на 4 пару
                    }
                }

                // Назначаем лекцию всем группам и преподавателю
                groupCombinations.forEach(combination ->
                        combination.getGroups().forEach(group ->
                                group.addLessonScheduleGridMap(lectureCell, currentLesson)));
                educator.addLessonScheduleGridMap(lectureCell, currentLesson);
                return true;
            }
        }
        return false;
    }

    private boolean distributeOtherLessons(CellForLesson lessonCell, Lesson currentLesson, int indexCurrentCell, List<CellForLesson> cellForLessons, int totalLessons) {
        int combinationIndex = 0;

        while (combinationIndex < groupCombinations.size()) {
            GroupCombination combination = groupCombinations.get(combinationIndex);

            // Ищем следующую свободную ячейку
            int nextFreeCellIndex = findNextFreeCell(indexCurrentCell, cellForLessons, combination);
            if (nextFreeCellIndex == -1) {
                break; // Свободных ячеек нет
            }

            CellForLesson freeCell = cellForLessons.get(nextFreeCellIndex);

            // Проверяем, можно ли назначить занятие на 4 пару
            if (!canAssignFourthPair(freeCell, totalLessons, combination)) {
                indexCurrentCell = nextFreeCellIndex + 1; // Пропускаем 4 пару
                continue;
            }

            assignLessonToCombinationAndEducator(freeCell, currentLesson, combination);
            combinationIndex++;
            indexCurrentCell = nextFreeCellIndex + 1; // Переходим к следующей ячейке
        }

        return combinationIndex == groupCombinations.size();
    }


    */
/**
     * Проверяет, можно ли назначить занятие на 4 пару для преподавателя и всех групп в комбинации.
     *
     * @param freeCell     ячейка для занятия
     * @param totalLessons общее количество занятий
     * @param combination  комбинация групп
     * @return true, если можно назначить занятие на 4 пару
     *//*

    private boolean canAssignFourthPair(CellForLesson freeCell, int totalLessons, GroupCombination combination) {
        if (freeCell.getTimeSlotPair() != TimeSlotPair.FOURTH) {
            return true; // Если это не 4 пара, то проверка не требуется
        }

        boolean canAssignToEducator = canAssignFourthPairLesson(educator.getScheduleGridMap(), totalLessons);
        boolean canAssignToGroup = combination.getGroups().stream()
                .allMatch(group -> canAssignFourthPairLesson(group.getScheduleGridMap(), totalLessons));
        return canAssignToEducator && canAssignToGroup;
    }

    */
/**
     * Проверяет, можно ли назначить занятие на 4 пару для объекта (преподаватель или группа).
     *
     * @param scheduleGrid расписание объекта (преподаватель или группа)
     * @param totalLessons общее количество занятий
     * @return true, если можно назначить занятие на 4 пару
     *//*

    private boolean canAssignFourthPairLesson(Map<CellForLesson, AbstractLesson> scheduleGrid, int totalLessons) {
        int fourthPairLessons = countFourthPairLessons(scheduleGrid);
        double fourthPairPercentage = (double) fourthPairLessons / totalLessons * 100;

*/
/*        System.out.println("Текущий процент занятий на 4 пару: " + fourthPairPercentage + "%");
        System.out.println("Максимальный допустимый процент: " + maxPercentage + "%");*//*


        return fourthPairPercentage < maxPercentage; // Используем поле maxPercentage
    }

    */
/**
     * Подсчитывает количество занятий на 4 пару для объекта (преподаватель или группа).
     *
     * @param scheduleGrid расписание объекта (преподаватель или группа)
     * @return количество занятий на 4 пару
     *//*

    private int countFourthPairLessons(Map<CellForLesson, AbstractLesson> scheduleGrid) {
        return (int) scheduleGrid.entrySet().stream()
                .filter(entry -> entry.getKey().getTimeSlotPair() == TimeSlotPair.FOURTH) // Фильтруем по 4 паре
                .filter(entry -> entry.getValue() != null) // Игнорируем пустые ячейки (null)
                .count();
    }

    */
/**
     * Находит следующую свободную ячейку для занятия.
     *
     * @param startIndex     начальный индекс для поиска
     * @param cellForLessons список всех ячеек
     * @param combination    комбинация групп
     * @return индекс следующей свободной ячейки или -1, если свободных ячеек нет
     *//*

    private int findNextFreeCell(int startIndex, List<CellForLesson> cellForLessons, GroupCombination combination) {
        // Инициализируем итератор, если он еще не был создан
        if (cellIterator == null) {
            cellIterator = cellForLessons.listIterator(startIndex);
        }

        // Используем итератор для поиска следующей свободной ячейки
        while (cellIterator.hasNext()) {
            CellForLesson cell = cellIterator.next();
            if (isSlotFreeForCombination(combination, cell) && isBusyEntityInCell(educator, cell)) {
                return cellForLessons.indexOf(cell); // Возвращаем индекс свободной ячейки
            }
        }

        return -1; // Свободных ячеек нет
    }

    */
/**
     * Проверяет, свободен ли слот для всех групп в комбинации.
     *
     * @param combination комбинация групп
     * @param cell        ячейка
     * @return true, если слот свободен
     *//*

    private boolean isSlotFreeForCombination(GroupCombination combination, CellForLesson cell) {
        return combination.getGroups().stream()
                .allMatch(group -> isBusyEntityInCell(group, cell));
    }

    */
/**
     * Назначает занятие комбинации групп и преподавателю.
     *
     * @param cell        ячейка для занятия
     * @param lesson      занятие
     * @param combination комбинация групп
     *//*

    private void assignLessonToCombinationAndEducator(CellForLesson cell, Lesson lesson, GroupCombination combination) {
        combination.getGroups().forEach(group -> group.addLessonScheduleGridMap(cell, lesson));
        educator.addLessonScheduleGridMap(cell, lesson);
    }

    */
/**
     * Проверяет, занят ли указанный объект (преподаватель, группа или аудитория) в заданной ячейке расписания.
     *
     * @param cell   Ячейка расписания для проверки
     * @param entity Объект для проверки (Educator, Group, GroupCombination или AbstractAuditorium)
     * @return true, если объект занят в указанной ячейке, иначе false
     * @throws IllegalArgumentException если передан неподдерживаемый тип объекта
     *//*

    public <T> boolean isBusyEntityInCell(T entity, CellForLesson cell) {
        if (entity == null || cell == null) {
            return false; // или можно выбросить IllegalArgumentException
        }

        List<AbstractLesson> lessonsInCell = scheduleGrid.getListLessonInCell(cell);
        if (lessonsInCell == null || lessonsInCell.isEmpty()) {
            return false;
        }

        return lessonsInCell.stream().anyMatch(lesson -> {
            if (entity instanceof Educator) {
                return lesson.getEducators().contains(entity);
            } else if (entity instanceof GroupCombination) {
                return lesson.getGroups().contains(entity);
            } else if (entity instanceof Group) { // Дополнительная проверка для Group
                return lesson.getGroups().stream()
                        .flatMap(comb -> comb.getGroups().stream())
                        .anyMatch(group -> group.equals(entity));
            } else if (entity instanceof AbstractAuditorium) {
                return entity.equals(lesson.getAuditorium());
            } else {
                throw new IllegalArgumentException("Неподдерживаемый тип объекта: " + entity.getClass());
            }
        });
    }

}*/
