package ru.services;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.enums.KindOfStudy;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Сервис, отвечающий за подготовку и сортировку занятий
 */
@Service
@RequiredArgsConstructor
public class LessonSortingService {
    private final CurriculumSlotService curriculumSlotService;
    private final SlotChainService slotChainService;

    /**
     * Адаптированная версия changeOrderLessons.
     * Сортирует занятия: сначала группирует "сцепки", затем пытается перемешать лекции и практики.
     *
     * @param lessons Список занятий для сортировки.
     * @return Новый, отсортированный список занятий.
     */
    public List<Lesson> getSortedLessons(List<Lesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return new ArrayList<>();
        }
        List<Lesson> sortedLessons = lessons.stream().sorted(Comparator.comparing(lesson -> lesson.getCurriculumSlot().getPosition())).collect(Collectors.toList());
        // 1. Сначала группируем "сцепленные" занятия
        List<Lesson> chainSortedLessons = groupChainedLessons(sortedLessons, slotChainService);
        // 2. Затем применяем вашу сложную логику перемешивания лекций и практик

        //return mixLecturesAndPractices(chainSortedLessons, curriculumSlotService);
        return changeOrderLessons(chainSortedLessons);
    }

    /**
     * Группирует занятия, связанные через SlotChain, вместе.
     */
    private List<Lesson> groupChainedLessons(List<Lesson> lessons, SlotChainService slotChainService) {
        List<Lesson> result = new ArrayList<>();
        Set<Lesson> visited = new HashSet<>();

        for (Lesson currentLesson : lessons) {
            if (visited.contains(currentLesson)) {
                continue;
            }

            // 1. Находим все ID слотов в цепочке для текущего занятия
            List<Integer> chainSlotIds = slotChainService.getFullChain(currentLesson.getCurriculumSlot().getId());

            // Если это "одиночное" занятие, просто добавляем его
            if (chainSlotIds.size() <= 1) {
                result.add(currentLesson);
                visited.add(currentLesson);
                continue;
            }


            // 2. Вот он, ключевой фильтр из старой логики!
            List<Lesson> chainForThisGroup = lessons.stream()
                    .filter(otherLesson ->
                            // Условие 1: Принадлежность к тому же потоку/группе.
                            currentLesson.getStudyStream().equals(otherLesson.getStudyStream()) &&
                                    // Условие 2: Принадлежность к той же цепочке слотов.
                                    chainSlotIds.contains(otherLesson.getCurriculumSlot().getId())
                    )
                    // Сортируем занятия внутри блока по их естественному порядку в учебном плане.
                    .sorted(Comparator.comparing(l -> l.getCurriculumSlot().getPosition()))
                    .toList();

            // Добавляем всю найденную и отсортированную цепочку для этой группы
            if (!chainForThisGroup.isEmpty()) {
                result.addAll(chainForThisGroup);
                visited.addAll(chainForThisGroup);
            } else {
                // Этот блок на всякий случай, если логика фильтрации не нашла даже само занятие
                result.add(currentLesson);
                visited.add(currentLesson);
            }
        }
        return result;
    }


    // =======================================================================
    // СТАТИЧЕСКИЕ НЕЗАВИСИМЫЕ МЕТОДЫ (просто скопированы)
    // =======================================================================

    public int getLectureFrequency(List<Lesson> lessons) {
        int lectureCount = getLectureLessons(lessons).size();
        int practiceCount = getAnotherLessons(lessons).size();
        int firstLectures = getAmountFirstLectures(lessons);

        int effectiveLectures = lectureCount - firstLectures;
        if (effectiveLectures <= 0) {
            return practiceCount; // Все практики после первой пачки лекций
        }

        return (int) Math.ceil((double) practiceCount / effectiveLectures);
    }

    public int getAmountFirstLectures(List<Lesson> lessons) {
        return (int) lessons.stream()
                .takeWhile(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
                .count();
    }

    private List<Lesson> getLectureLessons(List<Lesson> lessons) {
        return lessons.stream()
                .filter(lesson -> lesson.getKindOfStudy() == KindOfStudy.LECTURE)
                .collect(Collectors.toList());
    }

    private List<Lesson> getAnotherLessons(List<Lesson> lessons) {
        return lessons.stream()
                .filter(lesson -> lesson.getKindOfStudy() != KindOfStudy.LECTURE)
                .collect(Collectors.toList());
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

    public Map<LocalDate, List<Lesson>> distributeLessons(
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
    private Map<LocalDate, List<Lesson>> mapToDatesUniform(
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
    private Map<Integer, List<Lesson>> distributeToLogicalDays(List<Lesson> lessons) {
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
    public boolean hasSeminarsBetweenFirstLecturesAndNextLecture(List<Lesson> lessons) {
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
    private void distributeRemainingLessons(
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

    private void completeCurrentDay(
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
                if (curriculumSlotService.getPreviousLectureInCourse(practice).get().getPosition()
                        <= lecture.getCurriculumSlot().getPosition()) {
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
            List<Integer> targetSlotIds = slotChainService.getFullChain(lesson.getCurriculumSlot().getId());
            List<Lesson> chainFroLesson = lessons.stream()
                    .filter(lesson1 -> targetSlotIds.contains(lesson1.getCurriculumSlot().getId()))
                    .collect(Collectors.toList());
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
     * Считает количество необходимых дней для распределения списка занятий, по правилу 1 лекция+2 практики, или две практики
     */

    public int calculateDaysForListLessons(List<Lesson> lessons) {
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

    public List<Integer> distributeLessonsEvenly(int totalAvailableDays, int requiredDays) {
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
     * @return int
     */

    public int countUniqueDates(List<CellForLesson> cells) {
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
    public List<Lesson> distributeExam(List<Lesson> lessons) {
        List<Lesson> distribution = lessons.stream()
                .filter(lesson -> lesson.getKindOfStudy().equals(KindOfStudy.EXAM))
                .collect(Collectors.toList());
        lessons.removeAll(distribution);
        return lessons;

    }
}
