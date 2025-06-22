package ru.services;

import ru.entity.*;
import ru.entity.factories.CellForLessonFactory;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
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

    /**
     * Метод сортирует список занятий в соответствии с логикой и неразрывно проходящих занятий для комбинации групп
     */
    //TODO помещять ли этот метод в фабрику занятий? LessonFactory
    private List<Lesson> sortedLessonAboutChain(List<Lesson> lessons) {
        List<Lesson> sortedLessons = new ArrayList<>();
        List<Lesson> visitedLessons = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (visitedLessons.contains(lesson)) {
                continue;
            }
            List<Lesson> chainFroLesson = slotChainService.findLessonsInSlotChain(lesson, lessons);
            int i = 0;
            if (!chainFroLesson.isEmpty()) {
                sortedLessons.addAll(chainFroLesson);
                visitedLessons.addAll(chainFroLesson);
            } else {
                sortedLessons.add(lesson);
                visitedLessons.add(lesson);
            }

        }


        return sortedLessons;
    }






    public void distributeLessonsForEducator(Educator educator) {
        // Создаем рабочую копию ячеек
        List<CellForLesson> workingCells = new ArrayList<>(
                CellForLessonFactory.getAllOrderedCells().stream()
                        .filter(cell -> cell.getTimeSlotPair() != TimeSlotPair.FOURTH)
                        .collect(Collectors.toList())
        );

        // Группируем и сортируем ячейки по дате (не по номеру недели)
        Map<YearWeek, List<CellForLesson>> cellsByWeek = workingCells.stream()
                .collect(Collectors.groupingBy(
                        cell -> new YearWeek(cell.getDate()),
                        TreeMap::new, // автоматически сортирует по ключу
                        Collectors.toList()));

        List<Lesson> distributeLessons = changeOrderLessons(lessons);

        // Параметры распределения
        int practicalLessonsCount = 0;
        int practicalLessonsPerWeek = getLectureFrequency(lessons);
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
                if (isFirstWeekLecture && countingFirstWeekLecture < getAmountFirstLectures(lessons)) {
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


    /**
     * Метод распределения одиночного занятия
     *
     * @param lesson    распределяемое занятие
     * @param listCells список доступных ячеек для распределения
     */
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

    /**
     * Метод распределения связной группы занятий
     *
     * @param chain     список связных занятий
     * @param listCells список доступных ячеек для распределения
     */
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
                /*// Проверяем последовательные временные слоты
                for (int j = 0; j < chainSize - 1; j++) {
                    if (dayCells.get(i + j).getTimeSlotPair().ordinal() + 1 !=
                            dayCells.get(i + j + 1).getTimeSlotPair().ordinal()) {
                        canAssign = false;
                        break;
                    }
                }*/

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
    /**
     * Метод определяющий частотность распределения лекций между практическими занятиями, учитывающий исключаемые первые
     * лекции
     */
    //TODO доработать правило определения лекций для пропуска, и вывести расчет дополнительных лекций не попадающих под
    //TODO правило распределения этого метода
    private static int getLectureFrequency(List<Lesson> lessons) {
        //количество лекций не задействованных для совместного распределения с практическими занятиями
        int correctLecturesSize = getAmountFirstLectures(lessons);

        List<Lesson> lectureLessons = getLectureLessons(lessons);
        List<Lesson> anotherLessons = getAnotherLessons(lessons);

        //частота лекций между практическими занятиями
        float floatLectureFrequency = (float) anotherLessons.size() / (lectureLessons.size() - correctLecturesSize);
        int lectureFrequency = (int) floatLectureFrequency;
        // т.к. частота распределения лекций чаще всего является не целым числом, определеяем частоту распределения
        //дополнительной лекции (условно в какую неделю выставляется доп.лекция)
        int result = 0;
        if (floatLectureFrequency % 1 != 0) {
            result = (int) Math.floor(1 / (floatLectureFrequency - lectureFrequency));
            if (result == 1) {
                result += 1;
            }
        }
        return lectureFrequency;
    }
    /**
     * Метод сортирует список занятий для исключения следующих друг за другом лекций, при этом распределяя практические
     * занятия между лекциями так, чтобы в неделе была одна лекция и равномерное количество практик
     *
     * @param lessons список лекций
     * @return отсортированный список
     */
    //TODO изменить метод для возврата отсортированного списка
    public List<Lesson> changeOrderLessons(List<Lesson> lessons) {

        //if (lessons == null) return;

        List<Lesson> sortedLessons = sortedLessonAboutChain(lessons);
        lessons.clear();
        lessons.addAll(sortedLessons);

        List<Lesson> lectureLessons = getLectureLessons(lessons);
        List<Lesson> anotherLessons = getAnotherLessons(lessons);

        int lectureFrequency = getLectureFrequency(lessons);

        List<Lesson> resultLessons = new ArrayList<>();
        Iterator<Lesson> lectureIter = lectureLessons.iterator();
        Iterator<Lesson> practiceIter = anotherLessons.iterator();

        int practicesBetweenLectures = 0;
        int cyclesCompleted = 0;
        int lectureOutsideCycle = 2;
        for (int i = 0; i < lectureOutsideCycle; i++) {
            resultLessons.add(lectureIter.next());
        }

        while (lectureIter.hasNext() && practiceIter.hasNext()) {
            Lesson lecture = lectureIter.next();
            Lesson practice = new Lesson();
            boolean successAddPractice = true;
            // Добавляем лекцию
            resultLessons.add(lecture);

            // Добавляем практики с заданной частотой
            practicesBetweenLectures = 0;
            while (practiceIter.hasNext() && practicesBetweenLectures < lectureFrequency) {
                if (successAddPractice) {
                    practice = practiceIter.next();
                }


                if (curriculumSlotService.getPreviousLecture(practice.getCurriculumSlotId(), practice.getDiscipline().getId())
                        .get().getId() <= lecture.getCurriculumSlotId()) {
                    resultLessons.add(practice);
                    successAddPractice = true;
                    practicesBetweenLectures++;
                    cyclesCompleted++;
                } else {
                    lecture = lectureIter.next();
                    resultLessons.add(lecture);
                    successAddPractice = false;
                    if (resultLessons.size() >= 2 &&
                            resultLessons.get(resultLessons.size() - 1).getKindOfStudy() == KindOfStudy.LECTURE &&
                            resultLessons.get(resultLessons.size() - 2).getKindOfStudy() == KindOfStudy.LECTURE) {
                        Lesson movableLesson = resultLessons.remove(resultLessons.size() - 2);
                        resultLessons.add(resultLessons.size() - 3, movableLesson);
                    }

                }


                // Проверяем условие для дополнительной лекции
/*                if (result > 0 && cyclesCompleted >= result) {
                    if (lectureIter.hasNext()) {
                        resultLessons.add(lectureIter.next());
                    }
                    cyclesCompleted = 0;
                }*/
            }
        }

        // Добавляем оставшиеся элементы
        lectureIter.forEachRemaining(resultLessons::add);
        practiceIter.forEachRemaining(resultLessons::add);

        System.out.println();
        return resultLessons;
    }
    /**
     * Возвращает список доступных ячеек для распределения занятия. Т.е. все ячейки после предыдущего занятия
     *
     * @param lesson занятие
     * @param cells  список всех ячеек
     * @return List
     */
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

    /**
     * Метод возвращает список занятий состоящий только из лекций
     *
     * @param lessons список всех занятий
     */
    private static List<Lesson> getLectureLessons(List<Lesson> lessons) {
        return lessons.stream()
                .filter(lesson -> KindOfStudy.LECTURE.equals(lesson.getKindOfStudy()))
                .toList();
    }

    /**
     * Метод возвращает список занятий без лекций
     *
     * @param lessons список всех занятий
     */
    private static List<Lesson> getAnotherLessons(List<Lesson> lessons) {
        return lessons.stream()
                .filter(lesson -> !KindOfStudy.LECTURE.equals(lesson.getKindOfStudy()))
                .toList();
    }

    /**
     * Возвращает количество лекций до начала первого не лекционного занятия
     * Если список пуст, содержит только нелекционные занятия или первое же занятие не является лекцией,
     * возвращает 0.
     *
     * @param lessons список занятий
     * @return количество лекций в начале списка до первого нелекционного занятия (0, если таких лекций нет)
     */
    private static int getAmountFirstLectures(List<Lesson> lessons) {
        return (int) lessons.stream()
                .takeWhile(l -> l.getKindOfStudy().equals(KindOfStudy.LECTURE))
                .count();
    }

    /**
     * Метод распределения экзамена
     */
    //TODO реализовать распределение экзамена в сессию, на данный момент метод удаляет экзамены из списка занятий
    private void distributeExam() {
        lessons.removeIf(lesson -> lesson.getKindOfStudy().equals(KindOfStudy.EXAM));
    }

    // Вспомогательный класс для группировки по году и неделе
    private static class YearWeek implements Comparable<YearWeek> {
        private final int year;
        private final int week;
        private final LocalDate firstDay;

        public YearWeek(LocalDate date) {
            this.year = date.get(WeekFields.ISO.weekBasedYear());
            this.week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            this.firstDay = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        @Override
        public int compareTo(YearWeek other) {
            return this.firstDay.compareTo(other.firstDay);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            YearWeek yearWeek = (YearWeek) o;
            return year == yearWeek.year && week == yearWeek.week;
        }

        @Override
        public int hashCode() {
            return Objects.hash(year, week);
        }
    }
}
