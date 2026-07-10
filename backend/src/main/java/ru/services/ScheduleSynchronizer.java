package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.entity.Assignment;
import ru.entity.read.ScheduleView;
import ru.entity.write.LessonPlacement;
import ru.enums.TimeSlotPair;
import ru.events.PlacementChangedEvent;
import ru.events.ScheduleGeneratedEvent;
import ru.repository.read.ScheduleViewRepository;
import ru.repository.write.LessonPlacementRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для синхронизации Query Side с Command Side.
 *
 * <p>Работает асинхронно, чтобы не блокировать запись.</p>
 * <p>Получает события из Command Side и обновляет {@link ScheduleView}.</p>
 *
 * <p><b>ВАРИАНТ 3 (Вариант с несколькими группами):</b></p>
 * <pre>
 * Command Side                    Event Bus                    Query Side
 * ┌──────────────┐              ┌─────┐              ┌─────────────┐
 * │  LessonPlacement│  Publish   │     │  Subscribe   │ ScheduleView │
 * │  (1 для всех │────────────▶│Event│────────────▶│  (по одной   │
 * │   групп)     │              │     │              │   на группу) │
 * └──────────────┘              └─────┘              └─────────────┘
 *                                      Async
 *                               (не блокирует запись)
 * </pre>
 *
 * <p><b>Логика:</b></p>
 * <ul>
 *   <li>Один {@link LessonPlacement} может обслуживать несколько групп</li>
 *   <li>Для каждой группы создается отдельный {@link ScheduleView}</li>
 *   <li>Например: Лекция для ПИ-401, ПИ-402, ПИ-403 → 1 placement, 3 ScheduleView</li>
 * </ul>
 *
 * <p><b>Преимущества:</b></p>
 * <ul>
 *   <li>✅ Экономия памяти в Command Side (один placement)</li>
 *   <li>✅ Быстрый поиск по группе в Query Side (индекс на group_id)</li>
 *   <li>✅ Логически правильно (одна лекция для нескольких групп)</li>
 *   <li>✅ Никаких constraint errors</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/eaaDev/EventSourcing.html">Event Sourcing</a>
 * @see ru.entity.read.ScheduleView
 * @see ru.entity.write.LessonPlacement
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSynchronizer {

    private final ScheduleViewRepository viewRepository;
    private final LessonPlacementRepository placementRepository;

    /**
     * Синхронизация Query Side после генерации расписания.
     *
     * <p><b>ВАЖНО:</b> Этот метод выполняется АСИНХРОННО в фоне (@Async).</p>
     * <p>Фронтенд уже получил данные из Command Side, этот метод
     * заполняет Query Side для будущих запросов.</p>
     *
     * <p><b>ВАРИАНТ 3 (Мульти-групповая логика):</b></p>
     * <pre>
     * Placement 1 (Assignment "Лекция БД") содержит 3 группы
     *   ↓
     * ScheduleView 1 → ПИ-401 (group_id=10)
     * ScheduleView 2 → ПИ-402 (group_id=20)
     * ScheduleView 3 → ПИ-403 (group_id=30)
     * </pre>
     *
     * <p><b>Когда вызывается:</b></p>
     * <ol>
     *   <li>После COMMIT транзакции генерации</li>
     *   <li>ПОСЛЕ возврата ответа фронтенду</li>
     *   <li>В фоновом потоке (не блокирует)</li>
     * </ol>
     *
     * <p><b>Исполнение:</b></p>
     * <ul>
     *   <li>Для каждого placement создаётся НЕСКОЛЬКО ScheduleView (по одной на группу)</li>
     *   <li>Данные извлекаются из placement + assignment + group</li>
     *   <li>Записываются в {@link ScheduleViewRepository}</li>
     * </ul>
     *
     * @param event Событие генерации расписания
     * @see ScheduleGeneratedEvent
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScheduleGenerated(ScheduleGeneratedEvent event) {
        log.info("🔄 Синхронизация Query Side для session: {} ({} placements)",
                event.getSessionId(), event.getPlacementsCount());

        // Перед перепроекцией чистим прежние строки, чтобы исключить наложение
        // архивированных сессий. Парная логика на Command-стороне:
        // ScheduleGenerationService.archivePreviousSessions.
        // Путь 2: чистим ТОЛЬКО строки периода (по диапазону дат) — расписания других
        // семестров не затрагиваются. Без рамок (легаси) — весь view, как раньше.
        if (event.getPeriodStart() != null && event.getPeriodEnd() != null) {
            viewRepository.deleteByPeriod(event.getPeriodStart(), event.getPeriodEnd());
        } else {
            viewRepository.deleteAllInBatch();
        }

        int syncedCount = 0;

        // Период уже очищен выше — пишем строки заново. На один placement приходится строка
        // на КАЖДУЮ пару (группа × преподаватель): так read-модель несёт всех преподавателей
        // совместного занятия (напр. английский вдвоём), а mergeViewsToDto собирает их обратно
        // в одно занятие (distinct по преподавателям/группам). Прежде бралась лишь первая пара
        // getEducators().iterator().next() → второй преподаватель терялся и в сетке, и в тултипе.
        for (LessonPlacement placement : event.getPlacements()) {
            try {
                List<ScheduleView> views = buildViewsForPlacement(placement);
                viewRepository.saveAll(views);
                syncedCount += views.size();
            } catch (Exception e) {
                log.error("❌ Ошибка синхронизации placementId={}: {}", placement.getId(), e.getMessage(), e);
                // Продолжаем синхронизацию остальных placement
            }
        }

        log.info("✅ Синхронизация завершена: {} строк read-модели для {} placements",
                syncedCount, event.getPlacementsCount());
    }

    /**
     * Синхронизация после изменения размещения.
     *
     * <p>Обновляет {@link ScheduleView} при изменении {@link LessonPlacement}.</p>
     *
     * @param event Событие изменения размещения
     * @see PlacementChangedEvent
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPlacementChanged(PlacementChangedEvent event) {
        log.info("🔄 Синхронизация Query Side для placementId={} (тип: {})",
                event.getPlacementId(), event.getChangeType());

        try {
            if (event.isDeleted()) {
                // Удаляем view
                viewRepository.deleteByPlacementId(event.getPlacementId());
                log.info("🗑️  Удалена ScheduleView для placementId={}", event.getPlacementId());
                return;
            }

            // Перенос/изменение: перечитываем размещение по id в СОБСТВЕННОЙ
            // транзакции (REQUIRES_NEW). Полагаться на entity из события нельзя —
            // слушатель @Async выполняется в другом потоке после COMMIT, и навигация
            // по ленивым ассоциациям детачнутого placement в чужой/закрытой сессии
            // падает с "Illegal pop() ... JdbcValuesSourceProcessingState".
            LessonPlacement placement = placementRepository.findById(event.getPlacementId()).orElse(null);
            if (placement == null) {
                log.warn("⚠️  Размещение placementId={} не найдено — view не обновлена", event.getPlacementId());
                return;
            }

            // Один placement может иметь НЕСКОЛЬКО view — по одной на группу
            // (вариант 3). Обновляем/создаём каждую, как при генерации.
            syncPlacementViews(placement);
            log.info("🔄 Синхронизирована ScheduleView для placementId={}", event.getPlacementId());
        } catch (Exception e) {
            log.error("❌ Ошибка синхронизации placementId={}: {}", event.getPlacementId(), e.getMessage(), e);
        }
    }

    /**
     * Пересоздаёт все {@link ScheduleView} одного размещения: удаляет прежние строки и
     * пишет заново по паре (группа × преподаватель). Delete+recreate (а не update-in-place)
     * корректно отражает и смену состава — например добавление/снятие второго преподавателя,
     * — и держит логику симметричной {@link #onScheduleGenerated}.
     *
     * @param placement изменённое размещение
     */
    private void syncPlacementViews(LessonPlacement placement) {
        // ВАЖНО: bulk-delete (@Modifying) выполняется НЕМЕДЛЕННО, а производный
        // deleteByPlacementId (select+remove) откладывается до flush; Hibernate во flush
        // выполняет INSERT'ы ПЕРЕД DELETE'ами, из-за чего при переносе новая строка
        // (placement, group, educator) сталкивалась бы со старой (ещё не удалённой) →
        // нарушение UNIQUE. Поэтому старые строки сносим сразу, затем вставляем новые.
        viewRepository.deleteByPlacementIdIn(java.util.List.of(placement.getId()));
        viewRepository.saveAll(buildViewsForPlacement(placement));
    }

    /**
     * Строит все строки read-модели для одного размещения — по паре
     * (группа × преподаватель). Нет групп → одна «строка потока» (прежнее поведение);
     * нет преподавателей → строка без преподавателя. Благодаря паре по преподавателю
     * совместное занятие (напр. английский вдвоём) попадает в расписание каждого из них.
     */
    private List<ScheduleView> buildViewsForPlacement(LessonPlacement placement) {
        Assignment assignment = placement.getAssignment();

        boolean hasGroups = assignment != null
                && assignment.getStudyStream() != null
                && assignment.getStudyStream().getGroups() != null
                && !assignment.getStudyStream().getGroups().isEmpty();
        // null-группа = «строка на весь поток» (сохраняет прежнюю ветку без групп).
        List<ru.entity.Group> groups = hasGroups
                ? new ArrayList<>(assignment.getStudyStream().getGroups())
                : Collections.singletonList((ru.entity.Group) null);

        boolean hasEducators = assignment != null
                && assignment.getEducators() != null
                && !assignment.getEducators().isEmpty();
        // null-преподаватель = строка без преподавателя (как раньше при пустом составе).
        List<ru.entity.Educator> educators = hasEducators
                ? new ArrayList<>(assignment.getEducators())
                : Collections.singletonList((ru.entity.Educator) null);

        List<ScheduleView> views = new ArrayList<>(groups.size() * educators.size());
        for (ru.entity.Group group : groups) {
            for (ru.entity.Educator educator : educators) {
                views.add(buildOneView(placement, assignment, group, educator));
            }
        }
        return views;
    }

    /**
     * Одна строка read-модели для конкретной пары (группа, преподаватель).
     * {@code group}/{@code educator} могут быть null (нет групп / нет преподавателей).
     */
    private ScheduleView buildOneView(LessonPlacement placement, Assignment assignment,
                                      ru.entity.Group group, ru.entity.Educator educator) {
        ScheduleView view = new ScheduleView(); // id генерируется автоматически (UUID)

        view.setScheduledDate(placement.getScheduledDate());
        view.setTimeSlot(placement.getScheduledSlot());
        view.setPlacementId(placement.getId());

        if (assignment != null) {
            view.setDiscipline(extractDisciplineName(assignment), extractDisciplineAbbr(assignment));
            view.setKindOfStudy(assignment.getCurriculumSlot().getKindOfStudy().name());
            view.setCurriculumSlotId(assignment.getCurriculumSlot().getId());
            if (assignment.getCurriculumSlot().getThemeLesson() != null) {
                view.setTheme(
                    assignment.getCurriculumSlot().getThemeLesson().getThemeNumber(),
                    assignment.getCurriculumSlot().getThemeLesson().getTitle()
                );
            }
        }

        // Аудитория — денормализуем первую назначенную (как и прежде).
        if (placement.getAssignedAuditoriums() != null && !placement.getAssignedAuditoriums().isEmpty()) {
            var aud = placement.getAssignedAuditoriums().iterator().next();
            view.setAuditorium(aud.getId(), aud.getName());
        }

        if (educator != null) {
            view.setEducator(educator.getId(), educator.getName());
        }

        // Конкретная группа, если есть; иначе — сам поток (ветка «без групп»).
        if (group != null) {
            view.setGroup(group.getId(), group.getName());
        } else if (assignment != null && assignment.getStudyStream() != null) {
            view.setGroup(assignment.getStudyStream().getId(), assignment.getStudyStream().getName());
        }

        applyPinMetadata(view, placement);
        view.setLastUpdated(LocalDateTime.now());
        return view;
    }

    // ========== Helper Methods ==========

    /**
     * Денормализует признак закрепления и происхождение из {@link LessonPlacement}
     * в read-модель (Фича 2: индикатор замка на фронте). Единая точка, чтобы
     * create/update × group/no-group не расходились.
     */
    private void applyPinMetadata(ScheduleView view, LessonPlacement placement) {
        view.setLocked(placement.isLocked());
        view.setSource(placement.getSource() != null ? placement.getSource().name() : "GENERATED");
    }

    /**
     * Извлечь название дисциплины из Assignment.
     */
    private String extractDisciplineName(Assignment assignment) {
        if (assignment == null || assignment.getCurriculumSlot() == null) return null;
        if (assignment.getCurriculumSlot().getDisciplineCourse() == null) return null;
        if (assignment.getCurriculumSlot().getDisciplineCourse().getDiscipline() == null) return null;
        return assignment.getCurriculumSlot().getDisciplineCourse().getDiscipline().getName();
    }

    /**
     * Извлечь аббревиатуру дисциплины из Assignment.
     */
    private String extractDisciplineAbbr(Assignment assignment) {
        if (assignment == null || assignment.getCurriculumSlot() == null) return null;
        if (assignment.getCurriculumSlot().getDisciplineCourse() == null) return null;
        if (assignment.getCurriculumSlot().getDisciplineCourse().getDiscipline() == null) return null;
        return assignment.getCurriculumSlot().getDisciplineCourse().getDiscipline().getAbbreviation();
    }

    /**
     * Создаёт ScheduleView для тестирования.
     */
    public ScheduleView createTestView(UUID placementId, LocalDate date, ru.enums.TimeSlotPair slot) {
        ScheduleView view = new ScheduleView(placementId, date, slot);
        view.setEducator(1, "Test Educator");
        view.setGroup(1, "Test Group");
        view.setDiscipline("Test Discipline", "TEST");
        view.setAuditorium(1, "Test Auditorium");
        view.setLastUpdated(LocalDateTime.now());
        return view;
    }
}
