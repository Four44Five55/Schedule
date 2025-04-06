package ru.services;

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
    private final List<Lesson> logicSchemaStudyDiscipline;
    private final List<GroupCombination> groupCombinations;
    private final Educator educator;
    private Iterator<CellForLesson> cellIterator;//чтобы избежать повторного прохода по уже проверенным ячейкам
    private double maxPercentage; // Допустимый процент 4 пар

    public ScheduleService(List<Lesson> logicSchemaStudyDiscipline, List<GroupCombination> groupCombinations, Educator educator, double maxPercentage) {
        this.logicSchemaStudyDiscipline = logicSchemaStudyDiscipline;
        this.groupCombinations = groupCombinations;
        this.educator = educator;
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

    /**
     * Распределяет занятия между группами и преподавателем.
     *
     * @param startDate начальная дата расписания
     * @param endDate   конечная дата расписания
     */
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

/*            // Проверяем, есть ли уже занятие у преподавателя в этот день
            if (hasEducatorLessonOnDate(currentCell.getDate())) {
                continue; // Пропускаем день, если у преподавателя уже есть занятие
            }*/


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
        int i=1;
    }

    /**
     * Распределяет лекцию для всех групп и преподавателя.
     *
     * @param lectureCell   ячейка для лекции
     * @param currentLesson текущее занятие (лекция)
     * @return true, если лекция успешно распределена
     */
    private boolean distributeLecture(CellForLesson lectureCell, Lesson currentLesson) {
        // Исключения распределения лекции 4ой парой
        if (lectureCell.getTimeSlotPair() == TimeSlotPair.FOURTH) {
            return false;
        }
        // Проверка, свободен ли слот у преподавателя
        if (isSlotFree(educator.getScheduleGridMap(), lectureCell)) {
            // Проверка, свободен ли слот у всех групп
            boolean allGroupsFree = groupCombinations.stream()
                    .flatMap(combination -> combination.getGroups().stream())
                    .allMatch(group -> isSlotFree(group.getScheduleGridMap(), lectureCell));

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


    /**
     * Проверяет, можно ли назначить занятие на 4 пару для преподавателя и всех групп в комбинации.
     *
     * @param freeCell     ячейка для занятия
     * @param totalLessons общее количество занятий
     * @param combination  комбинация групп
     * @return true, если можно назначить занятие на 4 пару
     */
    private boolean canAssignFourthPair(CellForLesson freeCell, int totalLessons, GroupCombination combination) {
        if (freeCell.getTimeSlotPair() != TimeSlotPair.FOURTH) {
            return true; // Если это не 4 пара, то проверка не требуется
        }

        boolean canAssignToEducator = canAssignFourthPairLesson(educator.getScheduleGridMap(), totalLessons);
        boolean canAssignToGroup = combination.getGroups().stream()
                .allMatch(group -> canAssignFourthPairLesson(group.getScheduleGridMap(), totalLessons));
        return canAssignToEducator && canAssignToGroup;
    }

    /**
     * Проверяет, можно ли назначить занятие на 4 пару для объекта (преподаватель или группа).
     *
     * @param scheduleGrid расписание объекта (преподаватель или группа)
     * @param totalLessons общее количество занятий
     * @return true, если можно назначить занятие на 4 пару
     */
    private boolean canAssignFourthPairLesson(Map<CellForLesson, AbstractLesson> scheduleGrid, int totalLessons) {
        int fourthPairLessons = countFourthPairLessons(scheduleGrid);
        double fourthPairPercentage = (double) fourthPairLessons / totalLessons * 100;

/*        System.out.println("Текущий процент занятий на 4 пару: " + fourthPairPercentage + "%");
        System.out.println("Максимальный допустимый процент: " + maxPercentage + "%");*/

        return fourthPairPercentage < maxPercentage; // Используем поле maxPercentage
    }

    /**
     * Подсчитывает количество занятий на 4 пару для объекта (преподаватель или группа).
     *
     * @param scheduleGrid расписание объекта (преподаватель или группа)
     * @return количество занятий на 4 пару
     */
    private int countFourthPairLessons(Map<CellForLesson, AbstractLesson> scheduleGrid) {
        return (int) scheduleGrid.entrySet().stream()
                .filter(entry -> entry.getKey().getTimeSlotPair() == TimeSlotPair.FOURTH) // Фильтруем по 4 паре
                .filter(entry -> entry.getValue() != null) // Игнорируем пустые ячейки (null)
                .count();
    }

    /**
     * Находит следующую свободную ячейку для занятия.
     *
     * @param startIndex     начальный индекс для поиска
     * @param cellForLessons список всех ячеек
     * @param combination    комбинация групп
     * @return индекс следующей свободной ячейки или -1, если свободных ячеек нет
     */
    private int findNextFreeCell(int startIndex, List<CellForLesson> cellForLessons, GroupCombination combination) {
        // Инициализируем итератор, если он еще не был создан
        if (cellIterator == null) {
            cellIterator = cellForLessons.listIterator(startIndex);
        }

        // Используем итератор для поиска следующей свободной ячейки
        while (cellIterator.hasNext()) {
            CellForLesson cell = cellIterator.next();
            if (isSlotFreeForCombination(combination, cell) && isSlotFree(educator.getScheduleGridMap(), cell)) {
                return cellForLessons.indexOf(cell); // Возвращаем индекс свободной ячейки
            }
        }

        return -1; // Свободных ячеек нет
    }

    /**
     * Проверяет, свободен ли слот для всех групп в комбинации.
     *
     * @param combination комбинация групп
     * @param cell        ячейка
     * @return true, если слот свободен
     */
    private boolean isSlotFreeForCombination(GroupCombination combination, CellForLesson cell) {
        return combination.getGroups().stream()
                .allMatch(group -> isSlotFree(group.getScheduleGridMap(), cell));
    }

    /**
     * Назначает занятие комбинации групп и преподавателю.
     *
     * @param cell        ячейка для занятия
     * @param lesson      занятие
     * @param combination комбинация групп
     */
    private void assignLessonToCombinationAndEducator(CellForLesson cell, Lesson lesson, GroupCombination combination) {
        combination.getGroups().forEach(group -> group.addLessonScheduleGridMap(cell, lesson));
        educator.addLessonScheduleGridMap(cell, lesson);
    }

    /**
     * Назначает занятие конкретной группе и преподавателю.
     *
     * @param cell   ячейка для занятия
     * @param lesson занятие
     * @param group  группа
     */
    private void assignLessonToGroupAndEducator(CellForLesson cell, Lesson lesson, Group group) {
        group.addLessonScheduleGridMap(cell, lesson);
        educator.addLessonScheduleGridMap(cell, lesson);
    }

    /**
     * Проверяет, свободен ли временной слот в расписании.
     *
     * @param scheduleGrid расписание
     * @param cell         ячейка
     * @return true, если слот свободен
     */
    private boolean isSlotFree(Map<CellForLesson, AbstractLesson> scheduleGrid, CellForLesson cell) {
        return scheduleGrid.get(cell) == null;
    }

    /**
     * Проверяет, есть ли у преподавателя занятие в указанный день.
     *
     * @param date дата
     * @return true, если занятие есть
     */
    private boolean hasEducatorLessonOnDate(LocalDate date) {
        return educator.getScheduleGridMap().keySet().stream()
                .anyMatch(cell -> cell.getDate().equals(date));
    }
}