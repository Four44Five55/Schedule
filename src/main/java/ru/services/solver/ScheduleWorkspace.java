package ru.services.solver;

import lombok.Getter;
import ru.entity.*;
import ru.services.constraints.AllConstraints;
import ru.services.solver.availability.ResourceAvailabilityManager;
import ru.services.solver.model.SchedulableResource;
import ru.services.solver.model.ScheduleGrid;

import java.time.LocalDate;
import java.util.*;
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
        this.grid = new ScheduleGrid(startDate, endDate);
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
        // 1. Собираем всех "статичных" участников занятия (люди и группы)
        List<SchedulableResource> mainParticipants = getStaticParticipants(lesson);

        // 2. Проверяем их доступность. Это быстрая O(1) проверка.
        for (SchedulableResource participant : mainParticipants) {
            if (!participant.isFree(cell)) {
                return PlacementOption.unavailable(lesson, cell, participant.getName() + " занят");
            }
        }

        // 3. Подбираем аудитории
        List<Auditorium> foundAuditoriums = findAvailableAuditoriumsFor(lesson, cell);
        int requiredAuditoriumCount = lesson.getEducators().size(); // Простое правило: 1 преподаватель = 1 аудитория

        if (foundAuditoriums.size() < requiredAuditoriumCount) {
            return PlacementOption.unavailable(lesson, cell, "Недостаточно свободных аудиторий");
        }

        // 4. Оцениваем "качество" этого размещения
        int score = calculatePlacementScore(mainParticipants, cell);

        return PlacementOption.available(lesson, cell, foundAuditoriums.subList(0, requiredAuditoriumCount), score);
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

    private List<Auditorium> findAvailableAuditoriumsFor(Lesson lesson, CellForLesson cell) {
        // 1. Если есть жесткое требование
        if (lesson.getRequiredAuditorium() != null) {
            SchedulableResource audResource = resourceManager.getAuditoriumResource(lesson.getRequiredAuditorium().getId());
            return audResource.isFree(cell) ? List.of(lesson.getRequiredAuditorium()) : Collections.emptyList();
        }

        // 2. Если есть приоритетное
        if (lesson.getPriorityAuditorium() != null) {
            SchedulableResource audResource = resourceManager.getAuditoriumResource(lesson.getPriorityAuditorium().getId());
            if (audResource.isFree(cell)) {
                // Если приоритетная свободна, возвращаем только ее
                return List.of(lesson.getPriorityAuditorium());
            }
        }

        // 3. Если есть пул
        if (lesson.getAllowedAuditoriumPool() != null) {
            return lesson.getAllowedAuditoriumPool().getAuditoriums().stream()
                    .filter(aud -> resourceManager.getAuditoriumResource(aud.getId()).isFree(cell))
                    .filter(aud -> aud.getCapacity() >= lesson.getStudyStream().calculateTotalSize()) // Условный метод
                    .collect(Collectors.toList());
        }

        // 4. Резервный вариант (нежелателен)
        //TODO Преобразовать логику присвоения аудитории
        Set<Group> groups = lesson.getStudyStream().getGroups();
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("Занятие должно содержать хотя бы одну группу.");
        }
        if (groups.size() == 1) {
            return List.of(groups.stream()
                    .findFirst()
                    .map(Group::getBaseAuditorium)
                    .orElseThrow(() -> new IllegalStateException("Не удалось получить группу из набора.")));
        } else {
            return List.of(groups.stream()
                    .map(Group::getBaseAuditorium)
                    .max(Comparator.comparing(Auditorium::getCapacity))
                    .orElseThrow(() -> new IllegalStateException("Не удалось получить группу из набора.")));
        }
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
