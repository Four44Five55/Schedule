package ru.services.solver;

import lombok.Getter;
import ru.entity.*;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintAdmissionRule;
import ru.services.solver.availability.ResourceAvailabilityManager;
import ru.services.solver.model.AuditoriumResource;
import ru.services.solver.model.AcademicCalendar;
import ru.services.solver.model.SchedulableResource;
import ru.services.solver.model.ScheduleGrid;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Главный класс-дирижер, представляющий "рабочее пространство" для решателя.
 *
 * <p>Он инкапсулирует и управляет состоянием всего процесса планирования, включая
 * сетку расписания ({@link ScheduleGrid}) и доступность всех ресурсов (через {@link ResourceAvailabilityManager}).
 * Предоставляет атомарные и высокоуровневые операции для алгоритмов планирования.</p>
 */
public final class ScheduleWorkspace {

    @Getter
    private final ScheduleGrid grid;
    @Getter
    private final ResourceAvailabilityManager resourceManager;

    /** Календарные границы периода планирования (источник дат для солвера). */
    @Getter
    private final LocalDate startDate;
    @Getter
    private final LocalDate endDate;

    /**
     * Какие ячейки существуют в этом периоде. Раньше на этот вопрос отвечала глобальная статика
     * {@code CellForLessonFactory}, которую перезаполнял каждый запрос; теперь — значение,
     * принадлежащее конкретному workspace. Спрашивать календарь надо у того workspace, в чьём
     * периоде идёт работа: другого «текущего периода» в системе больше нет.
     */
    @Getter
    private final AcademicCalendar calendar;

    /**
     * Политика подбора комнаты. Без состояния — workspace держит занятость, селектор решает,
     * какую из свободных взять. Раньше это решение было размазано по четырём {@code if} внутри
     * самого workspace, из-за чего его нельзя было ни протестировать, ни переиспользовать.
     */
    private final AuditoriumSelector auditoriumSelector = new AuditoriumSelector();

    /**
     * Создает новое рабочее пространство для планирования.
     *
     * @param startDate      Дата начала периода планирования.
     * @param endDate        Дата окончания периода планирования.
     * @param allEducators   Список всех преподавателей.
     * @param allGroups      Список всех групп.
     * @param allAuditoriums Список всех аудиторий.
     * @param allConstraints DTO со всеми постоянными ограничениями.
     */
    public ScheduleWorkspace(
            LocalDate startDate,
            LocalDate endDate,
            List<Educator> allEducators,
            List<Group> allGroups,
            List<Auditorium> allAuditoriums,
            AllConstraints allConstraints
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.calendar = AcademicCalendar.of(startDate, endDate);
        this.grid = new ScheduleGrid(this.calendar);
        this.resourceManager = new ResourceAvailabilityManager(allEducators, allGroups, allAuditoriums, allConstraints);
    }

    /**
     * Основной метод для поиска варианта размещения.
     * Проверяет доступность всех ресурсов и подбирает динамические (аудитории).
     *
     * @param lesson Занятие, которое нужно разместить.
     * @param cell   Целевой временной слот.
     * @return {@link PlacementOption}, описывающий возможность и детали размещения.
     */
    public PlacementOption findPlacementOption(Lesson lesson, CellForLesson cell) {
        return findPlacementOption(lesson, cell, ConstraintPolicy.STRICT);
    }

    /**
     * То же, но с явно названной политикой отношения к постоянным ограничениям.
     *
     * <p>Перегрузка, а не параметр у единственного метода: строгая проверка должна оставаться
     * дефолтом. Генерация зовёт версию из двух аргументов и о послаблениях не знает — иначе окно
     * со свободными днями группы открыло бы автоматическую раскладку зачётов в сессию. Послабление
     * получает только тот, кто назвал {@link ConstraintPolicy#HONOR_WINDOWS}: ручная установка,
     * перенос и подсказка «куда можно» — то есть пути, где решение принимает человек.</p>
     *
     * @param policy {@link ConstraintPolicy#STRICT} — любое ограничение запрещает;
     *               {@link ConstraintPolicy#HONOR_WINDOWS} — окна аттестации пускают то, что им
     *               положено (см. {@code ConstraintAdmissionRule})
     */
    public PlacementOption findPlacementOption(Lesson lesson, CellForLesson cell, ConstraintPolicy policy) {
        // 1. Собираем всех "статичных" участников занятия (люди и группы)
        List<SchedulableResource> mainParticipants = getStaticParticipants(lesson);

        // Занятие глазами правила допуска: норма плана (где сдаётся) + категория вида. При строгой
        // политике не вычисляем вовсе — null означает «спрашиваю вообще», и любое ограничение
        // запрещает, как было до появления окон.
        ConstraintAdmissionRule.Admission admission =
                policy == ConstraintPolicy.HONOR_WINDOWS ? ConstraintAdmissionRule.Admission.of(lesson) : null;

        // 2. Проверяем их доступность. Это быстрая O(1) проверка.
        for (SchedulableResource participant : mainParticipants) {
            if (!participant.isFree(cell, admission)) {
                return PlacementOption.unavailable(lesson, cell, participant.getName() + " занят");
            }
        }

        // 3. Подбираем аудитории
        List<Auditorium> foundAuditoriums = findAvailableAuditoriumsFor(lesson, cell);
        // Одно занятие (один Assignment) = одна аудитория. Параллельные подгруппы — это
        // отдельные Assignment → отдельные Lesson, каждый со своей комнатой. Число
        // преподавателей на занятии (совместный экзамен/зачёт двумя преподавателями
        // в одной аудитории) на число комнат не влияет.
        int requiredAuditoriumCount = 1;

        if (foundAuditoriums.size() < requiredAuditoriumCount) {
            return PlacementOption.unavailable(lesson, cell, "Недостаточно свободных аудиторий");
        }

        // 4. Оцениваем "качество" этого размещения
        int score = calculatePlacementScore(mainParticipants, cell);

        return PlacementOption.available(lesson, cell, foundAuditoriums.subList(0, requiredAuditoriumCount), score);
    }

    /**
     * Как проверка относится к постоянным ограничениям.
     *
     * <p>Не «строгий/мягкий режим вообще»: занятость ресурса другим занятием — физика, она
     * непреодолима при любой политике. Различие касается только окон промежуточной аттестации.</p>
     */
    public enum ConstraintPolicy {
        /** Любое ограничение запрещает. Так работает генерация — и так было всегда. */
        STRICT,
        /** Окна аттестации пускают то, что им положено. Ручные пути: установка, перенос, подсказка. */
        HONOR_WINDOWS
    }

    /**
     * Атомарно выполняет размещение занятия в соответствии с переданной опцией.
     * Обновляет и основную сетку, и кэши занятости ресурсов.
     *
     * @param option Опция размещения, полученная от {@link #findPlacementOption}.
     */
    public void executePlacement(PlacementOption option) {
        if (!option.isPossible()) {
            throw new IllegalArgumentException("Попытка выполнить невозможное размещение.");
        }
        Lesson lesson = option.lessonToPlace();
        CellForLesson cell = option.targetCell();

        // --- НАЧАЛО АТОМАРНОЙ ОПЕРАЦИИ ---

        // 1. Назначаем подобранные аудитории на "виртуальные" подзанятия
        // Мы можем добавить логику, чтобы связать каждого преподавателя с конкретной аудиторией
        lesson.setAssignedAuditoriums(option.assignedAuditoriums());

        // 2. Добавляем "толстый" Lesson в основную сетку
        grid.add(cell, lesson);

        // 3. Обновляем кэши занятости для всех участников
        List<SchedulableResource> allParticipants = new ArrayList<>(getStaticParticipants(lesson));
        for (Auditorium aud : option.assignedAuditoriums()) {
            allParticipants.add(resourceManager.getAuditoriumResource(aud.getId()));
        }

        allParticipants.forEach(p -> p.occupy(cell, lesson));

        // --- КОНЕЦ АТОМАРНОЙ ОПЕРАЦИИ ---
    }

    /**
     * Атомарно удаляет размещенное занятие из расписания.
     */
    public void removePlacement(Lesson lesson) {

        CellForLesson cell = grid.getCellForLesson(lesson);

        if (cell == null) return;
        // 1. Удаляем из основной сетки
        grid.remove(cell, lesson);

        // 2. Освобождаем все ресурсы
        List<SchedulableResource> allParticipants = new ArrayList<>(getStaticParticipants(lesson));
        if (lesson.getAssignedAuditoriums() != null) {
            for (Auditorium aud : lesson.getAssignedAuditoriums()) {
                allParticipants.add(resourceManager.getAuditoriumResource(aud.getId()));
            }
        }

        allParticipants.forEach(p -> p.free(cell));
    }

    /**
     * Принудительно размещает занятие в указанный слот и аудитории.
     * Обновляет и общую сетку, и ресурсы.
     * Используется для фиксации результатов алгоритма.
     */
    public void forcePlacement(Lesson lesson, CellForLesson cell, List<Auditorium> auditoriums) {
        // 1. Обновляем само занятие
        if (auditoriums != null) {
            // Фильтруем null (если аудитория не была найдена)
            List<Auditorium> validAuditoriums = new ArrayList<>();
            for (Auditorium aud : auditoriums) {
                if (aud != null) validAuditoriums.add(aud);
            }
            lesson.setAssignedAuditoriums(validAuditoriums);
        } else {
            lesson.setAssignedAuditoriums(new ArrayList<>());
        }

        // 2. Добавляем в общую сетку
        grid.add(cell, lesson);

        // 3. Обновляем ресурсы (тут используется уже существующая приватная логика или getStaticParticipants)
        List<SchedulableResource> participants = getStaticParticipants(lesson);

        // Добавляем динамические ресурсы (аудитории)
        for (Auditorium aud : lesson.getAssignedAuditoriums()) {
            participants.add(resourceManager.getAuditoriumResource(aud.getId()));
        }

        // Проставляем занятость
        participants.forEach(p -> p.occupy(cell, lesson));
    }

    /**
     * Выполняет чтение так, будто указанных занятий в расписании нет, и <b>возвращает их на место</b>.
     *
     * <p>Подбор «куда можно перенести» обязан временно изъять само занятие: иначе его собственные
     * ресурсы (преподаватель, группа, комната) выглядят занятыми, и правильный ответ выродится в
     * «переносить некуда». Изъятие — операция над состоянием, а метод по смыслу запрос: единственный
     * способ не нарушить CQS наружу — сделать «как было» <b>гарантией</b>, а не соглашением.</p>
     *
     * <p><b>Почему {@code finally}, а не обработчик исключений.</b> Advice отвечает, что показать
     * человеку; вернуть изменённый объект в памяти он не может. Пока workspace жил один запрос и
     * выбрасывался, разницы не было. С кэшем workspace переживает запрос, и исключение посреди
     * подбора (например {@code findAvailableAuditoriumsFor} на потоке без групп) оставило бы
     * занятие изъятым <b>в кэше</b> — расписание молча теряет занятие, и ни одной ошибки в логе.</p>
     *
     * <p>Порядок возврата обратный порядку изъятия, а снимок — {@code (ячейка, аудитории)} на момент
     * изъятия: {@link #removePlacement} освобождает ресурсы по <b>текущим</b> аудиториям занятия, и
     * без копии списка восстановление зависело бы от того, не переписал ли их кто-то в теле.</p>
     *
     * @param lessons занятия, которые нужно временно изъять; не размещённые пропускаются
     * @param body    само чтение (фильтры, подбор вариантов)
     * @return то, что вернуло чтение
     */
    public <T> T withoutPlacements(Collection<Lesson> lessons, Supplier<T> body) {
        List<RemovedPlacement> removed = new ArrayList<>();
        try {
            for (Lesson lesson : lessons) {
                CellForLesson cell = grid.getCellForLesson(lesson);
                if (cell == null) {
                    continue; // занятия нет в сетке — изымать нечего, и возвращать потом тоже
                }
                List<Auditorium> rooms = lesson.getAssignedAuditoriums() == null
                        ? List.of() : List.copyOf(lesson.getAssignedAuditoriums());
                removePlacement(lesson);
                removed.add(new RemovedPlacement(lesson, cell, rooms));
            }
            return body.get();
        } finally {
            for (int i = removed.size() - 1; i >= 0; i--) {
                RemovedPlacement placement = removed.get(i);
                forcePlacement(placement.lesson(), placement.cell(), placement.auditoriums());
            }
        }
    }

    /** Снимок изъятого размещения: чем именно его возвращать на место. */
    private record RemovedPlacement(Lesson lesson, CellForLesson cell, List<Auditorium> auditoriums) {}

    /**
     * Находит ячейку, в которой размещено занятие.
     * Делегирует поиск сетке расписания.
     *
     * @param lesson искомое занятие.
     * @return Ячейка или null.
     */
    public CellForLesson getCellForLesson(Lesson lesson) {
        return grid.getCellForLesson(lesson);
    }
    // =======================================================================
    // Приватные вспомогательные методы
    // =======================================================================

    private List<SchedulableResource> getStaticParticipants(Lesson lesson) {
        List<SchedulableResource> participants = new ArrayList<>();
        // Добавляем преподавателей
        for (Educator educator : lesson.getEducators()) {
            participants.add(resourceManager.getEducatorResource(educator.getId()));
        }
        // Добавляем группы
        if (lesson.getStudyStream() != null) {
            for (Group group : lesson.getStudyStream().getGroups()) {
                participants.add(resourceManager.getGroupResource(group.getId()));
            }
        }
        return participants;
    }

    /**
     * Свободные комнаты, подходящие занятию в этой ячейке, — лучшие первыми.
     *
     * <p>Само решение («какая комната лучше») живёт в {@link AuditoriumSelector}: workspace — это
     * состояние, а не политика. Здесь остаётся только подставить свои комнаты как ресурсы.</p>
     *
     * <p><b>Что было.</b> Цепочка из четырёх {@code if} (жёсткое → приоритет → пул → базовая
     * аудитория группы), где каждая ветка проверяла что хотела: {@code isFree} спрашивали три из
     * четырёх, вместимость — одна из четырёх, а резервная не спрашивала ничего и возвращала
     * базовую аудиторию группы вслепую. Замер живой базы (2026-07-17): <b>179</b> ячеек с двойным
     * бронированием и <b>366</b> занятий, не помещающихся в комнату, — все из резервной ветки.
     * И ветка эта не экзотическая, а основная: у всех лекций {@code required/priority/pool} пусты.</p>
     *
     * @param lesson занятие (несёт требования плана и состав потока)
     * @param cell   целевая ячейка
     * @return свободные комнаты, лучшая первой; пусто — ставить некуда
     */
    public List<Auditorium> findAvailableAuditoriumsFor(Lesson lesson, CellForLesson cell) {
        return auditoriumSelector.select(lesson, cell, resourceManager.allAuditoriumResources()).stream()
                .map(AuditoriumResource::auditorium)
                .collect(Collectors.toList());
    }

    private int calculatePlacementScore(List<SchedulableResource> participants, CellForLesson cell) {
        // Суммируем "штрафы" от каждого участника
        return participants.stream()
                .mapToInt(p -> p.getPreferenceScore(cell))
                .sum();
    }

    /**
     * Полностью очищает текущее расписание и освобождает все ресурсы.
     * Используется при генерации популяции, чтобы использовать один Workspace многократно.
     */
    public void clear() {
// 1. Очищаем сетку через её публичный метод
        grid.clear();

        // 2. Очищаем ресурсы
        resourceManager.clearAllResources();
    }
}
