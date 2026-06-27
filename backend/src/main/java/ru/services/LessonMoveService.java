package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.TimeSlotPair;
import ru.events.PlacementChangedEvent;
import ru.exceptions.LessonMoveConflictException;
import ru.repository.write.LessonPlacementRepository;
import ru.services.factories.CellForLessonFactory;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

/**
 * Команда переноса занятия в «живом» расписании (CQRS Command Side).
 *
 * <p>Зеркальна {@link MoveLessonSuggestionService} (Query Side): тот ищет допустимые
 * слоты, этот выполняет перенос в один из них. Перед записью перенос повторно
 * валидируется на актуальном состоянии расписания через {@link ScheduleWorkspace} —
 * тот же доменный примитив, что использует генератор. Благодаря этому подсказка и
 * фактический перенос не могут разойтись в проверках.</p>
 *
 * <p><b>Почему аудиторию подбирает бэк, а не берёт с фронта:</b> «зелёная» ячейка
 * гарантирует лишь, что в слоте свободна <i>какая-то</i> подходящая аудитория. Если
 * сохранить текущую аудиторию занятия вслепую, она может оказаться занята другим
 * занятием в новом слоте — двойное бронирование. Поэтому конкретную свободную аудиторию
 * выбирает {@link ScheduleWorkspace#findPlacementOption}, рассматривая её как
 * полноправного участника занятия наравне с преподавателями и группами.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonMoveService {

    private final WorkspaceRecreationService workspaceRecreationService;
    private final LessonPlacementRepository placementRepo;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Перенести занятие в новый слот с повторной валидацией всех участников.
     *
     * @param placementId     переносимое размещение; одновременно — надёжный якорь сессии
     * @param newDate         новая дата
     * @param newSlot         новый временной слот (имя константы {@link TimeSlotPair})
     * @param expectedVersion ожидаемая версия сессии-владельца (optimistic lock)
     * @param user            автор изменения (для аудита)
     * @return сессия-владелец размещения — с актуальными данными для ответа
     * @throws ObjectOptimisticLockingFailureException если версия сессии устарела
     * @throws LessonMoveConflictException             если слот вне периода либо
     *                                                 преподаватель/группа/аудитория заняты
     */
    @Transactional
    public ScheduleSession moveLesson(
            UUID placementId,
            LocalDate newDate,
            String newSlot,
            Long expectedVersion,
            String user
    ) {
        // 1. Размещение — единственный надёжный якорь: сессию берём из него же, а не из
        //    sessionId с фронта (schedule_view грузится без привязки к сессии, фронтовый
        //    sessionId может указывать на другую).
        LessonPlacement placement = placementRepo.findById(placementId)
                .orElseThrow(() -> new IllegalArgumentException("Размещение не найдено: " + placementId));
        ScheduleSession session = placement.getSession();

        // 2. Optimistic lock по версии сессии-владельца.
        if (!session.getVersion().equals(expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(ScheduleSession.class, session.getId());
        }

        // 3. Пересоздаём workspace из сессии. Побочный эффект — инициализация кэша ячеек на
        //    период сессии, поэтому целевую ячейку резолвим строго ПОСЛЕ этого шага.
        var recreated = workspaceRecreationService.recreateWorkspaceFromSession(session.getId());
        ScheduleWorkspace workspace = recreated.workspace();

        Lesson targetLesson = recreated.lessonByPlacementId().get(placementId);
        if (targetLesson == null) {
            throw new IllegalStateException("Не удалось восстановить занятие для размещения " + placementId);
        }

        // 4. Целевая ячейка должна принадлежать планируемому периоду.
        TimeSlotPair slot = TimeSlotPair.valueOf(newSlot);
        CellForLesson targetCell = CellForLessonFactory.getCell(newDate, slot);
        if (targetCell == null) {
            throw new LessonMoveConflictException("выбранный слот вне планируемого периода");
        }

        // 5. Виртуально изымаем занятие — освобождаем его ресурсы (включая текущую
        //    аудиторию), чтобы повторная проверка видела целевой слот без него самого.
        workspace.removePlacement(targetLesson);

        // 6. Повторная валидация: все участники свободны И есть конкретная свободная
        //    аудитория. findPlacementOption — тот же примитив, что и при генерации.
        PlacementOption option = workspace.findPlacementOption(targetLesson, targetCell);
        if (!option.isPossible()) {
            throw new LessonMoveConflictException(option.failureReason());
        }

        // 7. Фиксируем перенос: дата/слот + КОНКРЕТНЫЕ подобранные аудитории (не «текущие»).
        placement.updatePlacement(newDate, slot, new HashSet<>(option.assignedAuditoriums()), user);
        placementRepo.save(placement);

        // 8. Публикуем событие — Query Side (schedule_view) обновится асинхронно.
        eventPublisher.publishEvent(new PlacementChangedEvent(session.getId(), placementId, placement));

        log.info("✅ Занятие перенесено: placementId={}, newDate={}, newSlot={}, аудиторий={}",
                placementId, newDate, newSlot, option.assignedAuditoriums().size());

        return session;
    }
}
