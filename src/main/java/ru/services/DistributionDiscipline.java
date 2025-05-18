package ru.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.entity.*;
import ru.entity.factories.CellForLessonFactory;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class DistributionDiscipline {
    SlotChainService slotChainService;
    CurriculumSlotService curriculumSlotService;
    ScheduleGrid scheduleGrid;
    List<Lesson> lessons;
    List<Educator> educators;
    List<Lesson> distributedLessons = new ArrayList<>();


    public DistributionDiscipline(ScheduleGrid scheduleGrid, List<Lesson> lessons, List<Educator> educators, SlotChainService slotChainService, CurriculumSlotService curriculumSlotService) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;
        this.educators = educators;
        this.slotChainService = slotChainService;
        this.curriculumSlotService = curriculumSlotService;
    }

    public void distributeLessons() {
        for (Educator educator : educators) {
            distributeLessonsForEducator(educator);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(DistributionDiscipline.class);

    /*public void distributeLessonsForEducator(Educator educator) {
        ListIterator<Lesson> lessonIterator = lessons.listIterator();
        List<CellForLesson> cellForLessons = CellForLessonFactory.getAllOrderedCells();
        Iterator<CellForLesson> cellIterator = cellForLessons.iterator();

        while (lessonIterator.hasNext()) {
            Lesson lesson = lessonIterator.next();
            // Если занятие не распределено
            if (!distributedLessons.contains(lesson)) {
                List<Lesson> chainLessons = slotChainService.findLessonsInSlotChain(lesson, lessons);

                if (!chainLessons.isEmpty()) {
                    System.out.println(chainLessons.toString());
                }

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
                                distributedLessons.add(lesson);
                                // Удаляем ячейку из доступных, если она больше не может использоваться
                                cellIterator.remove();

                                break;
                            }
                        }
                    }

                }
            }
        }
    }*/
    public void distributeLessonsForEducator(Educator educator) {
        // Создаем рабочую копию ячеек
        List<CellForLesson> workingCells = new ArrayList<>(
                CellForLessonFactory.getAllOrderedCells().stream()
                        .filter(cell -> cell.getTimeSlotPair() != TimeSlotPair.FOURTH)
                        .collect(Collectors.toList())
        );

        for (Lesson lesson : lessons) {
            if (distributedLessons.contains(lesson) || !lesson.isEntityUsed(educator)) {
                continue;
            }

            List<Lesson> chainLessons = slotChainService.findLessonsInSlotChain(lesson, lessons);

            if (chainLessons.isEmpty()) {
                distributeSingleLesson(lesson, workingCells);
            } else {
                // Создаем временную копию для попытки распределения цепочки
                List<CellForLesson> tempCells = new ArrayList<>(workingCells);
                boolean distributed = distributeLessonChain(chainLessons, tempCells);

                if (distributed) {
                    // Если цепочку распределили - обновляем основной список
                    workingCells = tempCells;
                } else {
                    // Если не получилось - распределяем по одному
                    for (Lesson singleLesson : chainLessons) {
                        distributeSingleLesson(singleLesson, workingCells);
                    }
                }
            }
        }
    }

    private void distributeSingleLesson(Lesson lesson, List<CellForLesson> listCells) {
        List<CellForLesson> cells = getAvailableCellsForLesson(lesson, listCells);
        for (Iterator<CellForLesson> it = cells.iterator(); it.hasNext(); ) {
            CellForLesson cell = it.next();
            if (isCellFreeForLesson(lesson, cell)) {
                scheduleGrid.addLessonToCell(cell, lesson);
                distributedLessons.add(lesson);
                it.remove();
                break;
            }
        }
    }

    private boolean distributeLessonChain(List<Lesson> chain, List<CellForLesson> listCells) {
        int chainSize = chain.size();
        if (chain.isEmpty() || listCells.isEmpty() || chainSize > listCells.size()) {
            return false;
        }

        List<CellForLesson> cells = getAvailableCellsForLesson(chain.getFirst(), listCells);

        // Группируем ячейки по дням с сохранением порядка
        Map<LocalDate, List<CellForLesson>> cellsByDay = cells.stream()
                .collect(Collectors.groupingBy(
                        CellForLesson::getDate,
                        LinkedHashMap::new,  // Сохраняем порядок дней
                        Collectors.toList()
                ));

        for (List<CellForLesson> dayCells : cellsByDay.values()) {
            if (dayCells.size() < chainSize) {
                continue; // В этом дне недостаточно ячеек
            }

            // Сортируем ячейки дня по временным слотам
            dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

            for (int i = 0; i <= dayCells.size() - chainSize; i++) {
                boolean canAssign = true;
                // Проверяем последовательные временные слоты
                for (int j = 0; j < chainSize - 1; j++) {
                    if (dayCells.get(i + j).getTimeSlotPair().ordinal() + 1 !=
                            dayCells.get(i + j + 1).getTimeSlotPair().ordinal()) {
                        canAssign = false;
                        break;
                    }
                }

                if (canAssign) {
                    // Проверяем доступность всех ячеек
                    for (int j = 0; j < chainSize; j++) {
                        if (!isCellFreeForLesson(chain.get(j), dayCells.get(i + j))) {
                            canAssign = false;
                            break;
                        }
                    }

                    if (canAssign) {
                        // Размещаем занятия
                        for (int j = 0; j < chainSize; j++) {
                            scheduleGrid.addLessonToCell(dayCells.get(i + j), chain.get(j));
                            distributedLessons.add(chain.get(j));
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<CellForLesson> getAvailableCellsForLesson(Lesson lesson, List<CellForLesson> cells) {
        if (lesson == null || cells == null || cells.isEmpty()) {
            return new ArrayList<>();
        }
        Lesson previousLesson = findPreviousLesson(lesson);

        CellForLesson currentCell = scheduleGrid.getCellForLesson(previousLesson);
        if (currentCell == null) {
            return new ArrayList<>(cells); // Если ячейка не найдена, возвращаем все
        }

        return cells.stream()
                .filter(cell -> {
                    // Сравниваем даты
                    int dateComparison = cell.getDate().compareTo(currentCell.getDate());

                    if (dateComparison > 0) {
                        // Если дата ячейки позже текущей - включаем
                        return true;
                    } else if (dateComparison == 0) {
                        // Если та же дата, сравниваем временные слоты
                        return cell.getTimeSlotPair().ordinal() > currentCell.getTimeSlotPair().ordinal();
                    }
                    // Если дата раньше - исключаем
                    return false;
                })
                .collect(Collectors.toList());
    }

    // Проверка возможности размещения цепочки
    private boolean canAssignChain(List<Lesson> chainLessons, List<CellForLesson> cells) {
        for (int i = 0; i < chainLessons.size(); i++) {
            if (!isCellFreeForLesson(chainLessons.get(i), cells.get(i))) {
                return false;
            }
        }
        return true;
    }

    // Проверка доступности ячейки для занятия
    private boolean isCellFreeForLesson(Lesson lesson, CellForLesson cell) {
        return lesson.getAllMaterialEntity().stream()
                .allMatch(entity -> entity.isFree(scheduleGrid, cell));
    }

    /**
     * Проверяет возможность назначения занятия в заданную ячейку для всех сущностей занятия
     */
    private boolean isLessonFreeInCell(CellForLesson cell, Lesson lesson) {
        return lesson.getAllMaterialEntity().stream()
                .allMatch(entity -> entity.isFree(scheduleGrid, cell));
    }

    /**
     * Получает занятие по id slotChain
     *
     * @param slotId
     * @return Lesson
     */
    private Lesson findLessonBySlotId(Integer slotId) {
        return lessons.stream()
                .filter(lesson -> lesson.getCurriculumSlotId().equals(slotId))
                .findFirst()
                .orElseThrow(); // или orElseThrow(), если слот обязан существовать
    }

    /**
     * Находит предыдущее занятие для указанного урока, учитывая общие группы в groupCombinations.
     *
     * @param currentLesson текущее занятие, для которого ищем предыдущее
     * @return предыдущее занятие или null, если не найдено
     */
    private Lesson findPreviousLesson(Lesson currentLesson) {
        if (currentLesson == null || currentLesson.getCurriculumSlot() == null) {
            return null;
        }

        // Получаем данные текущего занятия для сравнения
        Discipline currentDiscipline = currentLesson.getDiscipline();
        Integer currentSlotId = currentLesson.getCurriculumSlot().getId();
        List<GroupCombination> currentCombinations = currentLesson.getGroupCombinations();

        // Проходим по всем занятиям в обратном порядке (для оптимизации)
        for (int i = lessons.indexOf(currentLesson) - 1; i >= 0; i--) {
            Lesson candidate = lessons.get(i);

            // Проверяем условия:
            // 1. Та же дисциплина
            // 2. Меньший curriculumSlot.id
            // 3. Есть общие группы
            if (candidate.getDiscipline().equals(currentDiscipline)
                    && candidate.getCurriculumSlot().getId() < currentSlotId
                    && hasCommonGroups(candidate.getGroupCombinations(), currentCombinations)) {
                return candidate;
            }
        }

        return null; // Не найдено
    }

    /**
     * Проверяет, есть ли общие группы между двумя списками GroupCombination.
     */
    private static boolean hasCommonGroups(List<GroupCombination> comb1, List<GroupCombination> comb2) {
        return comb1.stream()
                .flatMap(c1 -> c1.getGroups().stream())
                .anyMatch(group1 ->
                        comb2.stream()
                                .flatMap(c2 -> c2.getGroups().stream())
                                .anyMatch(group1::equals)
                );
    }
}
