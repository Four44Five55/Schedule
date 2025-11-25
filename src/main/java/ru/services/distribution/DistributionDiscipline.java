package ru.services.distribution;

public class DistributionDiscipline {
/*

    ScheduleGrid scheduleGrid;
    List<Lesson> lessons;
    List<Educator> educators;
    List<GroupCombination> groupCombinations;
    SlotChainService slotChainService;
    CurriculumSlotService curriculumSlotService;
    List<Lesson> distributedLessons = new ArrayList<>();
    ListLessonsHelper listLessonsHelper;


    public DistributionDiscipline(ScheduleGrid scheduleGrid, List<Lesson> lessons, List<Educator> educators, List<GroupCombination> groupCombinationList, SlotChainService slotChainService, CurriculumSlotService curriculumSlotService) {
        this.scheduleGrid = scheduleGrid;
        this.lessons = lessons;
        this.educators = educators;
        this.groupCombinations = groupCombinationList;
        this.slotChainService = slotChainService;
        this.curriculumSlotService = curriculumSlotService;
        this.listLessonsHelper = new ListLessonsHelper(curriculumSlotService, slotChainService);
    }

    public void distributeLessons() {
        for (Educator educator : educators) {
            distributeLessonsForEducator(educator);
        }
    }
*/

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


/*
    public void distributeLessonsForEducator(Educator educator) {
        // Создаем рабочую копию ячеек
        List<CellForLesson> workingCells = new ArrayList<>(
                CellForLessonFactory.getAllOrderedCells().stream()
                        .filter(cell -> cell.getTimeSlotPair() != TimeSlotPair.FOURTH)
                        .collect(Collectors.toList())
        );


        // Группируем и сортируем ячейки по дате
        Map<YearWeek, List<CellForLesson>> cellsByWeek = YearWeek.getWeekMap(workingCells);

        Map<YearWeek, List<CellForLesson>> newCellsByWeek = YearWeek.getWeekMap(scheduleGrid.getAvailableCells(groupCombinations, List.of(educator)));


        List<Lesson> distributeLessons = listLessonsHelper.changeOrderLessons(lessons);

        // Параметры распределения
        int practicalLessonsCount = 0;
        int practicalLessonsPerWeek = listLessonsHelper.getLectureFrequency(lessons);
        int countingFirstWeekLecture = 0;
        boolean isFirstWeekLecture = true;

        // Итерация по неделям в хронологическом порядке
        for (Map.Entry<YearWeek, List<CellForLesson>> entry : cellsByWeek.entrySet()) {
            List<CellForLesson> weekCells = new ArrayList<>(entry.getValue());

            // Группируем ячейки по дням с сортировкой по времени
            Map<LocalDate, List<CellForLesson>> dayCellsMap = weekCells.stream()
                    .collect(Collectors.groupingBy(
                            CellForLesson::getDate,
                            TreeMap::new,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> list.stream()
                                            .sorted(Comparator.comparing(CellForLesson::getTimeSlotPair))
                                            .collect(Collectors.toList())
                            )
                    ));

            for (Lesson lesson : distributeLessons) {
                if (distributedLessons.contains(lesson) || !lesson.isEntityUsed(educator)) {
                    continue;
                }
                //распределение лекций на первую учебную неделю без практик
                if (isFirstWeekLecture && countingFirstWeekLecture < listLessonsHelper.getAmountFirstLectures(lessons)) {
                    distributeSingleLesson(lesson, dayCellsMap.values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList()));
                    countingFirstWeekLecture++;
                    continue;
                } else if (isFirstWeekLecture) {

                    isFirstWeekLecture = false;
                    break; // Переходим к следующей неделе
                }

                // Проверка лимита практических занятий
                if (lesson.getKindOfStudy() != KindOfStudy.LECTURE &&
                        practicalLessonsCount >= practicalLessonsPerWeek) {
                    break; // Переходим к следующей неделе
                }

                // Распределение занятий с учетом требований к позициям
                List<Lesson> chainLessons = slotChainService.findLessonsInSlotChain(lesson, lessons);

                if (chainLessons.isEmpty()) {
                    distributeSingleLessonWithPosition(lesson, dayCellsMap);
                } else {
                    distributeChainWithPosition(chainLessons, dayCellsMap);
                }

                // Обновляем счетчик
                if (lesson.getKindOfStudy() != KindOfStudy.LECTURE) {
                    practicalLessonsCount++;
                }
            }

            // Сбрасываем счетчик для новой недели
            practicalLessonsCount = 0;
        }
    }

    // Распределения с учетом позиции
    private void distributeSingleLessonWithPosition(Lesson lesson, Map<LocalDate, List<CellForLesson>> dayCellsMap) {
        // Жесткое правило: практики только на 2+ позиции
        for (List<CellForLesson> dayCells : dayCellsMap.values()) {
            if (dayCells.size() < 2) continue; // Пропускаем дни с <2 ячейками

            int position = lesson.getKindOfStudy() == KindOfStudy.LECTURE ? 0 : 1;

            // Для практик проверяем только ячейки с позиции 1 и далее
            if (lesson.getKindOfStudy() != KindOfStudy.LECTURE) {
                for (int i = 1; i < dayCells.size(); i++) {
                    if (isCellFreeForLesson(lesson, dayCells.get(i))) {
                        scheduleGrid.addLessonToCell(dayCells.get(i), lesson);
                        distributedLessons.add(lesson);
                        return;
                    }
                }
                continue; // Не нашли место для практики в этом дне
            }

            // Только для лекций - проверяем позицию 0
            if (position < dayCells.size() && isCellFreeForLesson(lesson, dayCells.get(position))) {
                scheduleGrid.addLessonToCell(dayCells.get(position), lesson);
                distributedLessons.add(lesson);
                return;
            }
        }

        // Резервный вариант (сохраняет оригинальную логику)
        distributeSingleLesson(lesson, dayCellsMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList()));
    }

    // Распределения цепочки с учетом позиций
    private void distributeChainWithPosition(List<Lesson> chain, Map<LocalDate, List<CellForLesson>> dayCellsMap) {
        // Сначала пробуем разместить цепочку по правилам
        for (List<CellForLesson> dayCells : dayCellsMap.values()) {
            if (dayCells.size() < chain.size()) continue;

            boolean allMatch = true;
            for (int i = 0; i < chain.size(); i++) {
                Lesson lesson = chain.get(i);
                int expectedPosition = lesson.getKindOfStudy() == KindOfStudy.LECTURE ? 0 :
                        (i == 0 ? 1 : 2);

                if (expectedPosition >= dayCells.size() ||
                        !isCellFreeForLesson(lesson, dayCells.get(expectedPosition))) {
                    allMatch = false;
                    break;
                }
            }

            if (allMatch) {
                for (int i = 0; i < chain.size(); i++) {
                    Lesson lesson = chain.get(i);
                    int expectedPosition = lesson.getKindOfStudy() == KindOfStudy.LECTURE ? 0 :
                            (i == 0 ? 1 : 2);
                    scheduleGrid.addLessonToCell(dayCells.get(expectedPosition), lesson);
                    distributedLessons.add(lesson);
                }
                return;
            }
        }

        // Если не получилось по правилам, пробуем обычным способом
        List<CellForLesson> allCells = dayCellsMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<CellForLesson> tempCells = new ArrayList<>(allCells);
        if (!distributeLessonChain(chain, tempCells)) {
            for (Lesson lesson : chain) {
                distributeSingleLesson(lesson, allCells);
            }
        }
    }


    */
/**
     * Метод распределения одиночного занятия
     *
     * @param lesson    распределяемое занятие
     * @param listCells список доступных ячеек для распределения
     *//*

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

    */
/**
     * Метод распределения связной группы занятий
     *
     * @param chain     список связных занятий
     * @param listCells список доступных ячеек для распределения
     *//*

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
                */
/*//*
/ Проверяем последовательные временные слоты
                for (int j = 0; j < chainSize - 1; j++) {
                    if (dayCells.get(i + j).getTimeSlotPair().ordinal() + 1 !=
                            dayCells.get(i + j + 1).getTimeSlotPair().ordinal()) {
                        canAssign = false;
                        break;
                    }
                }*//*


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


    */
/**
     * Возвращает список доступных ячеек для распределения занятия. Т.е. все ячейки после предыдущего занятия
     *
     * @param lesson занятие
     * @param cells  список всех ячеек
     * @return List
     *//*

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


    // Проверка доступности ячейки для занятия
    private boolean isCellFreeForLesson(Lesson lesson, CellForLesson cell) {
        return lesson.getAllMaterialEntity().stream()
                .allMatch(entity -> entity.isFree(scheduleGrid, cell));
    }

    */
/**
     * Проверяет возможность назначения занятия в заданную ячейку для всех сущностей занятия
     *//*

    private boolean isLessonFreeInCell(CellForLesson cell, Lesson lesson) {
        return lesson.getAllMaterialEntity().stream()
                .allMatch(entity -> entity.isFree(scheduleGrid, cell));
    }

    */
/**
     * Получает занятие по id slotChain
     *
     * @param slotId
     * @return Lesson
     *//*

    private Lesson findLessonBySlotId(Integer slotId) {
        return lessons.stream()
                .filter(lesson -> lesson.getCurriculumSlotId().equals(slotId))
                .findFirst()
                .orElseThrow(); // или orElseThrow(), если слот обязан существовать
    }

    */
/**
     * Находит предыдущее занятие для указанного урока, учитывая общие группы в groupCombinations.
     *
     * @param currentLesson текущее занятие, для которого ищем предыдущее
     * @return предыдущее занятие или null, если не найдено
     *//*

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

    */
/**
     * Проверяет, есть ли общие группы между двумя списками GroupCombination.
     *//*

    private static boolean hasCommonGroups(List<GroupCombination> comb1, List<GroupCombination> comb2) {
        return comb1.stream()
                .flatMap(c1 -> c1.getGroups().stream())
                .anyMatch(group1 ->
                        comb2.stream()
                                .flatMap(c2 -> c2.getGroups().stream())
                                .anyMatch(group1::equals)
                );
    }

*/




}
