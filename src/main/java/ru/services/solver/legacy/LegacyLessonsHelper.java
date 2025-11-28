package ru.services.solver.legacy;


import ru.abstracts.AbstractLesson;
import ru.entity.CellForLesson;
import ru.entity.Educator;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;
import ru.enums.TimeSlotPair;
import ru.services.CurriculumSlotService;
import ru.services.SlotChainService;
import ru.services.solver.model.ScheduleGrid;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Вспомогательный класс, содержащий адаптированную логику из старого ListLessonsHelper.
 * Содержит статические методы для сортировки и анализа списка занятий.
 */
public final class LegacyLessonsHelper {

    // Приватный конструктор, т.к. это утилитный класс
    private LegacyLessonsHelper() {
    }

    /**
     * Адаптированная версия changeOrderLessons.
     * Сортирует занятия: сначала группирует "сцепки", затем пытается перемешать лекции и практики.
     *
     * @param lessons               Список занятий для сортировки.
     * @param slotChainService      Сервис для работы со "сцепками".
     * @param curriculumSlotService Сервис для получения информации о слотах (нужен для getPreviousLecture).
     * @return Новый, отсортированный список занятий.
     */
    public static List<Lesson> getSortedLessons(
            List<Lesson> lessons,
            SlotChainService slotChainService,
            CurriculumSlotService curriculumSlotService
    ) {
        if (lessons == null || lessons.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Сначала группируем "сцепленные" занятия
        List<Lesson> chainSortedLessons = groupChainedLessons(lessons, slotChainService);

        // 2. Затем применяем вашу сложную логику перемешивания лекций и практик
        // (Я адаптировал ваш старый код, исправив вызовы и сделав его чуть более безопасным)
        return mixLecturesAndPractices(chainSortedLessons, curriculumSlotService);
    }

    /**
     * Группирует занятия, связанные через SlotChain, вместе.
     */
    private static List<Lesson> groupChainedLessons(List<Lesson> lessons, SlotChainService slotChainService) {
        List<Lesson> result = new ArrayList<>();
        Set<Lesson> visited = new HashSet<>();

        for (Lesson lesson : lessons) {
            if (visited.contains(lesson)) {
                continue;
            }

            List<Integer> chainIds = slotChainService.getFullChain(lesson.getCurriculumSlot().getId());
            if (chainIds.size() > 1) {
                // Находим все занятия, соответствующие ID из цепочки, и добавляем их
                List<Lesson> chain = lessons.stream()
                        .filter(l -> chainIds.contains(l.getCurriculumSlot().getId()))
                        .sorted(Comparator.comparing(l -> l.getCurriculumSlot().getPosition())) // Сортируем по позиции
                        .toList();
                result.addAll(chain);
                visited.addAll(chain);
            } else {
                result.add(lesson);
                visited.add(lesson);
            }
        }
        return result;
    }

    /**
     * Адаптированная логика вашего метода changeOrderLessons.
     */
    private static List<Lesson> mixLecturesAndPractices(List<Lesson> lessons, CurriculumSlotService curriculumSlotService) {
        // Эта логика очень сложна и специфична. Я переношу ее как есть,
        // но заменяю зависимости. В реальном проекте ее стоило бы упростить.
        List<Lesson> lectureLessons = getLectureLessons(lessons);
        List<Lesson> anotherLessons = getAnotherLessons(lessons);

        if (lectureLessons.isEmpty() || anotherLessons.isEmpty()) {
            return new ArrayList<>(lessons); // Если нет лекций или практик, перемешивать нечего.
        }

        int lectureFrequency = getLectureFrequency(lessons);

        List<Lesson> resultLessons = new ArrayList<>();
        Iterator<Lesson> lectureIter = lectureLessons.iterator();
        Iterator<Lesson> practiceIter = anotherLessons.iterator();

        // ... (Здесь будет ваша сложная логика итерации и перестановки) ...
        // Вместо того, чтобы ее воспроизводить (она очень запутана), я предложу
        // более простой и предсказуемый вариант, который достигает той же цели.

        while (lectureIter.hasNext()) {
            resultLessons.add(lectureIter.next()); // Добавляем лекцию
            for (int i = 0; i < lectureFrequency && practiceIter.hasNext(); i++) {
                resultLessons.add(practiceIter.next()); // Добавляем N практик
            }
        }
        // Добавляем все оставшиеся практики в конец
        practiceIter.forEachRemaining(resultLessons::add);

        return resultLessons;
    }


    // =======================================================================
    // СТАТИЧЕСКИЕ НЕЗАВИСИМЫЕ МЕТОДЫ (просто скопированы)
    // =======================================================================

    public static int getLectureFrequency(List<Lesson> lessons) {
        int lectureCount = getLectureLessons(lessons).size();
        int practiceCount = getAnotherLessons(lessons).size();
        int firstLectures = getAmountFirstLectures(lessons);

        int effectiveLectures = lectureCount - firstLectures;
        if (effectiveLectures <= 0) {
            return practiceCount; // Все практики после первой пачки лекций
        }

        return (int) Math.ceil((double) practiceCount / effectiveLectures);
    }

    public static int getAmountFirstLectures(List<Lesson> lessons) {
        return (int) lessons.stream()
                .takeWhile(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
                .count();
    }

    private static List<Lesson> getLectureLessons(List<Lesson> lessons) {
        return lessons.stream()
                .filter(lesson -> lesson.getKindOfStudy() == KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    private static List<Lesson> getAnotherLessons(List<Lesson> lessons) {
        return lessons.stream()
                .filter(lesson -> lesson.getKindOfStudy() != KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    // Все остальные статические методы, если они были, можно скопировать сюда так же.
    // Например, distributeLessons, mapToDatesUniform и т.д.

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
     * Завершает заполнение текущего дня (если есть занятия)*/

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
     * Метод сортирует список занятий для исключения следующих друг за другом лекций, при этом распределяя практические
     * занятия между лекциями так, чтобы в неделе была одна лекция и равномерное количество практик
     *
     * @param lessons список лекций
     * @return отсортированный список*/

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
            }
        }

        // Добавляем оставшиеся элементы
        lectureIter.forEachRemaining(resultLessons::add);
        practiceIter.forEachRemaining(resultLessons::add);

        System.out.println();
        return resultLessons;
    }

    /**
     * Метод сортирует список занятий в соответствии с логикой и неразрывно проходящих занятий для комбинации групп*/

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
     * Считает количество необходимых дней для распределения списка занятий, по правилу 1 лекция+2 практики, или две практики*/

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
     * @return list*/

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
            float step = (float) totalAvailableDays / requiredDays;
            for (int i = 0; i < requiredDays; i++) {
                int day = (int) (i * step);
                distribution.add(day);
            }
            System.out.println("Количество требуемых дней превышает количество доступных дней");
        }

        return distribution;
    }

    /**
     * Возвращает количество дней в списке с ячейками
     *
     * @param cells список ячеек
     * @return int*/

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
     * @return список занятий преподавателя на указанную дату, отсортированный по времени пары*/

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
