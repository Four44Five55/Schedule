package ru.utils;

import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.services.CurriculumSlotService;
import ru.services.SlotChainService;
import ru.entity.Educator;
import ru.entity.ScheduleGrid;
import ru.enums.TimeSlotPair;
import ru.abstracts.AbstractLesson;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListLessonsHelper {

    private final CurriculumSlotService curriculumSlotService;
    private final SlotChainService slotChainService;

    public ListLessonsHelper(CurriculumSlotService slotService, SlotChainService chainService) {
        this.curriculumSlotService = slotService;
        this.slotChainService = chainService;
    }

    /**
     * Полное распределение занятий с учетом:
     * 1. Начальных лекций (N-1 дней для первых лекций)
     * 2. Стандартных правил (1 лекция + 2 практики)
     * 3. Привязки к конкретным датам
     *
     * @param lessons        Список занятий для распределения
     * @param availableDates Доступные даты для распределения
     * @return Map с распределением по датам
     * @throws IllegalArgumentException если дат недостаточно
     */
    public static Map<LocalDate, List<Lesson>> distributeLessons(
            List<Lesson> lessons,
            List<LocalDate> availableDates
    ) {
        // 1. Распределяем занятия по логическим дням
        Map<Integer, List<Lesson>> dayMap = distributeToLogicalDays(lessons);

        // 2. Проверяем достаточность дат
        if (availableDates.size() < dayMap.size()) {
            throw new IllegalArgumentException(String.format(
                    "Недостаточно дат. Требуется: %d, доступно: %d",
                    dayMap.size(), availableDates.size()
            ));
        }

        // 3. Привязываем к реальным датам
        return mapToDatesUniform(dayMap, availableDates);
    }

    /**
     * Распределяет занятия по дням с учетом:
     * 1. Первые (N-1) лекций распределяются по одной в день
     * 2. Последняя начальная лекция (N-я) распределяется вместе с практическими занятиями
     * 3. Остальные занятия по стандартным правилам
     */
    private static Map<Integer, List<Lesson>> distributeToLogicalDays(List<Lesson> lessons) {
        Map<Integer, List<Lesson>> schedule = new LinkedHashMap<>();
        if (lessons == null || lessons.isEmpty()) {
            return schedule;
        }

        int currentDay = 1;
        int initialLectures = getAmountFirstLectures(lessons);
        //если есть семинары после первых лекций, инкерментируем счетскик, тем самым увеличиваем кол-во дней на подгото
        //вку к семинару
        if (hasSeminarsBetweenFirstLecturesAndNextLecture(lessons)) initialLectures++;

        // 1. Распределяем первые (N-1) лекций по одной в день
        for (int i = 0; i < Math.max(0, initialLectures - 1); i++) {
            List<Lesson> dailyLessons = new ArrayList<>();
            dailyLessons.add(lessons.get(i));
            if (i == 0) {
                schedule.put(currentDay, dailyLessons);
                continue;
            }
            if (i % 2 != 0) {
                dailyLessons.clear();
                dailyLessons.add(lessons.get(i - 1));
                dailyLessons.add(lessons.get(i));
                schedule.put(currentDay, dailyLessons);
                currentDay++;
                //в случае когда количество лекций для распределения без практик не четное
                // добавляется одиночная лекция в день
            } else if ((initialLectures - 1) % 2 != 0) {
                schedule.put(currentDay++, Collections.singletonList(lessons.get(i)));
            }

        }

        // 2. Оставшиеся занятия (включая последнюю начальную лекцию)
        int remainingStartIndex = Math.max(0, initialLectures - 1);
        if (remainingStartIndex < lessons.size()) {
            distributeRemainingLessons(
                    lessons.subList(remainingStartIndex, lessons.size()),
                    schedule,
                    currentDay
            );
        }

        return schedule;
    }

    /**
     * Распределяет оставшиеся занятия (включая последнюю начальную лекцию)
     * по правилам: 1 лекция + до 2 практик в день
     */
    private static void distributeRemainingLessons(
            List<Lesson> remainingLessons,
            Map<Integer, List<Lesson>> schedule,
            int startDay
    ) {
        int currentDay = startDay;
        List<Lesson> currentDayLessons = new ArrayList<>();
        int practicesToday = 0;

        for (Lesson lesson : remainingLessons) {
            if (lesson.getKindOfStudy() == KindOfStudy.LECTURE) {
                // Если уже есть лекция в текущем дне - завершаем день
                if (currentDayLessons.stream().anyMatch(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
                ) {
                    schedule.put(currentDay++, currentDayLessons);
                    currentDayLessons = new ArrayList<>();
                    practicesToday = 0;
                }
                currentDayLessons.add(lesson);
            } else {
                currentDayLessons.add(lesson);
                practicesToday++;
                // Если уже 2 практики в текущем дне - завершаем день
                if (practicesToday >= 2) {
                    schedule.put(currentDay++, currentDayLessons);
                    currentDayLessons = new ArrayList<>();
                    practicesToday = 0;
                }

            }
        }

        // Добавляем последний день, если в нем есть занятия
        if (!currentDayLessons.isEmpty()) {
            schedule.put(currentDay, currentDayLessons);
        }
    }

    /**
     * Завершает заполнение текущего дня (если есть занятия)
     */
    private static void completeCurrentDay(
            Map<Integer, List<Lesson>> schedule,
            int dayNumber,
            List<Lesson> dayLessons
    ) {
        if (!dayLessons.isEmpty()) {
            schedule.put(dayNumber, new ArrayList<>(dayLessons));
        }
    }

    /**
     * Более простая версия равномерного распределения
     */
    private static Map<LocalDate, List<Lesson>> mapToDatesUniform(
            Map<Integer, List<Lesson>> dayMap,
            List<LocalDate> availableDates
    ) {
        Map<LocalDate, List<Lesson>> result = new LinkedHashMap<>();
        int requiredDays = dayMap.size();
        int totalDates = availableDates.size();

        if (requiredDays == 0 || totalDates == 0) {
            return result;
        }

        // Рассчитываем шаг распределения
        float step = (float) totalDates / requiredDays;

        for (int dayNum = 1; dayNum <= requiredDays; dayNum++) {
            int dateIndex = Math.round((dayNum - 1) * step);
            if (dateIndex >= totalDates) {
                dateIndex = totalDates - 1;
            }
            result.put(availableDates.get(dateIndex), dayMap.get(dayNum));
        }

        return result;
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
        int lectureOutsideCycle = getAmountFirstLectures(lessons);

        for (int i = 0; i < lectureOutsideCycle; i++) {
            resultLessons.add(lectureIter.next());
        }

        while (lectureIter.hasNext() && practiceIter.hasNext()) {
            Lesson lecture = lectureIter.next();
            Lesson practice = new Lesson();
            boolean successAddPractice = true;
            boolean addFirstPractice = false;
            // Добавляем лекцию
            resultLessons.add(lecture);

            // Добавляем практики с заданной частотой
            practicesBetweenLectures = 0;
            while (practiceIter.hasNext() && practicesBetweenLectures < lectureFrequency) {
                if (successAddPractice) {
                    practice = practiceIter.next();
                }

                //Проверка на последовательность выставления практического занятия.
                //Если практика
                if (curriculumSlotService.getPreviousLecture(practice.getCurriculumSlotId(), practice.getDiscipline().getId())
                        .get().getId() <= lecture.getCurriculumSlotId()) {
                    resultLessons.add(practice);
                    if (!addFirstPractice) {
                        addFirstPractice = true;
                    }
                    successAddPractice = true;
                    practicesBetweenLectures++;
                    cyclesCompleted++;
                    //Если выбрана практика, которая проводится позднее не распределенной лекции, то выставляется лекция
                } else {
                    lecture = lectureIter.next();
                    resultLessons.add(lecture);
                    successAddPractice = false;
                    //если распределены уже более двух занятий
                    // подряд уже стоят две лекции
                    //и флаг на добавление первой практики true, переносим вторую лекцию на две практики назад
                    if (resultLessons.size() >= 2 &&
                            resultLessons.get(resultLessons.size() - 1).getKindOfStudy() == KindOfStudy.LECTURE &&
                            resultLessons.get(resultLessons.size() - 2).getKindOfStudy() == KindOfStudy.LECTURE && addFirstPractice) {
                        Lesson movableLesson = resultLessons.remove(resultLessons.size() - 2);
                        resultLessons.add(resultLessons.size() - 3, movableLesson);
                        //или если количество практик между лекциями не четное, переносим лекцию на 1 практику назад,
                        // чтобы лекция в дальнейшем была на первой паре
                    } else if (practicesBetweenLectures % 2 != 0) {
                        Lesson movableLesson = resultLessons.removeLast();
                        resultLessons.add(resultLessons.size() - 1, movableLesson);
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
     * Метод сортирует список занятий в соответствии с логикой и неразрывно проходящих занятий для комбинации групп
     */
    //TODO помещять ли этот метод в фабрику занятий? LessonFactory
    public List<Lesson> sortedLessonAboutChain(List<Lesson> lessons) {
        List<Lesson> sortedLessons = new ArrayList<>();
        List<Lesson> visitedLessons = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (visitedLessons.contains(lesson)) {
                continue;
            }
            List<Lesson> chainFroLesson = slotChainService.findLessonsInSlotChain(lesson, lessons);

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

    /**
     * Метод определяющий частотность распределения лекций между практическими занятиями, учитывающий исключаемые первые
     * лекции
     */
    //TODO доработать правило определения лекций для пропуска, и вывести расчет дополнительных лекций не попадающих под
    //TODO правило распределения этого метода
    public static int getLectureFrequency(List<Lesson> lessons) {
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
     * Возвращает количество лекций до начала первого не лекционного занятия
     * Если список пуст, содержит только нелекционные занятия или первое же занятие не является лекцией,
     * возвращает 0.
     *
     * @param lessons список занятий
     * @return количество лекций в начале списка до первого нелекционного занятия (0, если таких лекций нет)
     */
    public static int getAmountFirstLectures(List<Lesson> lessons) {
        return (int) lessons.stream()
                .takeWhile(l -> l.getKindOfStudy().equals(KindOfStudy.LECTURE))
                .count();
    }

    /**
     * Проверяет, есть ли семинары между первой группой лекций и следующей лекцией после неё.
     *
     * @param lessons список занятий
     * @return true, если между первой группой лекций и следующей лекцией есть хотя бы один семинар,
     * false в противном случае (включая случаи, когда нет первой группы лекций или нет лекций после неё)
     */
    public static boolean hasSeminarsBetweenFirstLecturesAndNextLecture(List<Lesson> lessons) {
        int firstLecturesCount = getAmountFirstLectures(lessons);

        // Если нет первой группы лекций или это все занятия
        if (firstLecturesCount == 0 || firstLecturesCount >= lessons.size()) {
            return false;
        }

        // Получаем подсписок после первой группы лекций
        List<Lesson> remainingLessons = lessons.subList(firstLecturesCount, lessons.size());

        // Находим индекс следующей лекции
        OptionalInt nextLectureIndex = IntStream.range(0, remainingLessons.size())
                .filter(i -> remainingLessons.get(i).getKindOfStudy().equals(KindOfStudy.LECTURE))
                .findFirst();

        // Если нет следующей лекции, возвращаем false
        if (!nextLectureIndex.isPresent()) {
            return false;
        }

        // Проверяем, есть ли семинары между первой группой лекций и следующей лекцией
        return remainingLessons.subList(0, nextLectureIndex.getAsInt()).stream()
                .anyMatch(l -> l.getKindOfStudy().equals(KindOfStudy.SEMINAR));
    }

    /**
     * Считает количество необходимых дней для распределения списка занятий, по правилу 1 лекция+2 практики, или две практики
     */
    public static int calculateDaysForListLessons(List<Lesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return 0;
        }

        int days = 0;
        int lecturesToday = 0;
        int practicesToday = 0;

        for (Lesson lesson : lessons) {
            if (lesson.getKindOfStudy() == KindOfStudy.LECTURE) {
                // Лекция требует нового дня
                days++;
                lecturesToday = 1;
                practicesToday = 0; // Сбрасываем счетчик практик
            } else if (lesson.getKindOfStudy() != KindOfStudy.LECTURE) {
                if (practicesToday < 2) {
                    // Если можно добавить практику в текущий день
                    practicesToday++;
                    if (lecturesToday == 0 && practicesToday == 1) {
                        // Если это первая практика в новом дне
                        days++;
                    }
                } else {
                    // Если лимит практик исчерпан, начинаем новый день
                    days++;
                    practicesToday = 1;
                    lecturesToday = 0;
                }
            }
        }

        return days;


    }

    /**
     * Создание списка с индексами местоположения дней с занятиями
     *
     * @param totalAvailableDays Общее количество доступных дней
     * @param requiredDays       Количество необходимых дней
     * @return list
     */
    public static List<Integer> distributeLessonsEvenly(int totalAvailableDays, int requiredDays) {
        List<Integer> distribution = new ArrayList<>();

        if (requiredDays <= 0 || totalAvailableDays <= 0) {
            return distribution;
        }

        // Если дней достаточно, просто равномерно распределяем
        if (requiredDays <= totalAvailableDays) {
            float step = (float) totalAvailableDays / requiredDays;
            for (int i = 0; i < requiredDays; i++) {
                int day = Math.round(i * step);
                distribution.add(day);
            }
        }
        // Если дней не хватает, "уплотняем" (некоторые дни будут перегружены)
        else {
            /*float step = (float) totalAvailableDays / requiredDays;
            for (int i = 0; i < requiredDays; i++) {
                int day = (int) (i * step);
                distribution.add(day);
            }*/
            System.out.println("Количество требуемых дней превышает количество доступных дней");
        }

        return distribution;
    }

    /**
     * Возвращает количество дней в списке с ячейками
     *
     * @param cells список ячеек
     * @return int
     */
    public static int countUniqueDates(List<CellForLesson> cells) {
        if (cells == null) {
            return 0;
        }

        return (int) cells.stream()
                .filter(Objects::nonNull)
                .map(CellForLesson::getDate)
                .filter(Objects::nonNull)
                .distinct()
                .count();
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
     * Метод распределения экзамена
     */
    //TODO реализовать распределение экзамена в сессию, на данный момент метод удаляет экзамены из списка занятий
    public static List<Lesson> distributeExam(List<Lesson> lessons) {
        List<Lesson> distribution = lessons.stream()
                .filter(lesson -> lesson.getKindOfStudy().equals(KindOfStudy.EXAM))
                .collect(Collectors.toList());
        lessons.removeAll(distribution);
        return lessons;

    }

    /**
     * Получает все занятия преподавателя на указанную дату
     *
     * @param educator преподаватель
     * @param date дата
     * @param scheduleGrid сетка расписания
     * @return список занятий преподавателя на указанную дату, отсортированный по времени пары
     */
    public static List<Lesson> getEducatorLessonsForDate(Educator educator, LocalDate date, ScheduleGrid scheduleGrid) {
        if (educator == null || date == null || scheduleGrid == null) {
            throw new IllegalArgumentException("Educator, date and scheduleGrid cannot be null");
        }

        return scheduleGrid.getScheduleGridMap().entrySet().stream()
                .filter(entry -> {
                    CellForLesson cell = entry.getKey();
                    return cell.getDate().equals(date);
                })
                .flatMap(entry -> entry.getValue().stream())
                .filter(lesson -> lesson.getEducators().contains(educator))
                .sorted(Comparator.comparing(lesson -> {
                    CellForLesson cell = scheduleGrid.getCellForLesson((AbstractLesson) lesson);
                    return cell != null ? cell.getTimeSlotPair() : TimeSlotPair.FIRST;
                }))
                .map(lesson -> (Lesson) lesson)
                .collect(Collectors.toList());
    }
}
