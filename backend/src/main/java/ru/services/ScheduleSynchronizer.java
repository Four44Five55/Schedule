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
        int createdCount = 0;
        int updatedCount = 0;
        int viewsPerPlacement = 0;

        for (LessonPlacement placement : event.getPlacements()) {
            try {
                // ✅ ВАРИАНТ 3: Создаём НЕСКОЛЬКО ScheduleView для ОДНОГО placement (по одной на группу)
                Assignment assignment = placement.getAssignment();

                // Если у assignment нет study_stream или групп - создаем один view
                if (assignment.getStudyStream() == null || assignment.getStudyStream().getGroups() == null || assignment.getStudyStream().getGroups().isEmpty()) {
                    // Создаём один view (как раньше)
                    var existingView = viewRepository.findByPlacementId(placement.getId());

                    if (existingView.isEmpty()) {
                        ScheduleView view = createViewFromPlacement(placement);
                        viewRepository.save(view);
                        createdCount++;
                        log.debug("✅ Создана ScheduleView для placementId={} (без групп)", placement.getId());
                    } else {
                        ScheduleView view = existingView.get();
                        updateViewFromPlacement(view, placement);
                        viewRepository.save(view);
                        updatedCount++;
                        log.debug("🔄 Обновлена ScheduleView для placementId={}", placement.getId());
                    }
                    syncedCount++;
                    viewsPerPlacement++;
                } else {
                    // ✅ Создаём несколько view (по одной на группу)
                    for (ru.entity.Group group : assignment.getStudyStream().getGroups()) {
                        // ✅ Генерируем уникальный ключ: (placement_id, group_id)
                        String uniqueKey = placement.getId().toString() + "-" + group.getId();

                        // Проверяем, существует ли view по уникальному ключу
                        var existingView = viewRepository.findByPlacementIdAndGroupId(
                            placement.getId(),
                            group.getId()
                        );

                        if (existingView.isEmpty()) {
                            // ✅ Создаём новую view для группы (id генерируется автоматически!)
                            ScheduleView view = createViewFromPlacementForGroup(placement, group);
                            viewRepository.save(view);
                            createdCount++;
                            log.debug("✅ Создана ScheduleView для placementId={}, groupId={}", placement.getId(), group.getId());
                        } else {
                            // Обновляем существующую view
                            ScheduleView view = existingView.get();
                            updateViewFromPlacementForGroup(view, placement, group);
                            viewRepository.save(view);
                            updatedCount++;
                            log.debug("🔄 Обновлена ScheduleView для placementId={}, groupId={}", placement.getId(), group.getId());
                        }
                        syncedCount++;
                        viewsPerPlacement++;
                    }
                }
            } catch (Exception e) {
                log.error("❌ Ошибка синхронизации placementId={}: {}", placement.getId(), e.getMessage(), e);
                // Продолжаем синхронизацию остальных placement
            }
        }

        log.info("✅ Синхронизация завершена: {} view records (создано: {}, обновлено: {}, среднее view на placement: {})",
                syncedCount, createdCount, updatedCount, event.getPlacementsCount() > 0 ? syncedCount / event.getPlacementsCount() : 0);
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
     * Создаёт или обновляет все {@link ScheduleView} для одного размещения.
     *
     * <p>Учитывает «вариант 3»: на один {@link LessonPlacement} приходится по одной
     * view на каждую группу потока. Симметрично логике в {@link #onScheduleGenerated},
     * благодаря чему перенос обновляет ровно те же строки, что создала генерация.</p>
     *
     * @param placement изменённое размещение
     */
    private void syncPlacementViews(LessonPlacement placement) {
        Assignment assignment = placement.getAssignment();

        boolean hasGroups = assignment != null
                && assignment.getStudyStream() != null
                && assignment.getStudyStream().getGroups() != null
                && !assignment.getStudyStream().getGroups().isEmpty();

        if (!hasGroups) {
            // Один view (без групп)
            var existingView = viewRepository.findByPlacementId(placement.getId());
            if (existingView.isEmpty()) {
                viewRepository.save(createViewFromPlacement(placement));
            } else {
                ScheduleView view = existingView.get();
                updateViewFromPlacement(view, placement);
                viewRepository.save(view);
            }
            return;
        }

        // По одной view на каждую группу потока
        for (ru.entity.Group group : assignment.getStudyStream().getGroups()) {
            var existingView = viewRepository.findByPlacementIdAndGroupId(placement.getId(), group.getId());
            if (existingView.isEmpty()) {
                viewRepository.save(createViewFromPlacementForGroup(placement, group));
            } else {
                ScheduleView view = existingView.get();
                updateViewFromPlacementForGroup(view, placement, group);
                viewRepository.save(view);
            }
        }
    }

    /**
     * Создаёт ScheduleView из LessonPlacement.
     *
     * @param placement Размещение
     * @return ScheduleView
     */
    private ScheduleView createViewFromPlacement(LessonPlacement placement) {
        Assignment assignment = placement.getAssignment();

        // ✅ Создаём view с автоматической генерацией id (JPA сгенерирует UUID)
        ScheduleView view = new ScheduleView();

        // Заполняем данные размещения
        view.setScheduledDate(placement.getScheduledDate());
        view.setTimeSlot(placement.getScheduledSlot());
        view.setPlacementId(placement.getId());

        // Заполняем денормализованные данные из Assignment
        if (assignment != null) {
            view.setDiscipline(
                extractDisciplineName(assignment),
                extractDisciplineAbbr(assignment)
            );
            view.setKindOfStudy(assignment.getCurriculumSlot().getKindOfStudy().name());
            view.setCurriculumSlotId(assignment.getCurriculumSlot().getId());

            if (assignment.getCurriculumSlot().getThemeLesson() != null) {
                view.setTheme(
                    assignment.getCurriculumSlot().getThemeLesson().getThemeNumber(),
                    assignment.getCurriculumSlot().getThemeLesson().getTitle()
                );
            }
        }

        // Заполняем данные из Placement (аудитории)
        if (placement.getAssignedAuditoriums() != null && !placement.getAssignedAuditoriums().isEmpty()) {
            view.setAuditorium(
                placement.getAssignedAuditoriums().iterator().next().getId(),
                placement.getAssignedAuditoriums().iterator().next().getName()
            );
        }

        // Заполняем данные из Assignment (участники)
        if (assignment != null) {
            // Преподаватели
            if (assignment.getEducators() != null && !assignment.getEducators().isEmpty()) {
                view.setEducator(
                    assignment.getEducators().iterator().next().getId(),
                    assignment.getEducators().iterator().next().getName()
                );
            }

            // Группа
            if (assignment.getStudyStream() != null && assignment.getStudyStream().getGroups() != null) {
                view.setGroup(
                    assignment.getStudyStream().getId(),
                    assignment.getStudyStream().getName()
                );
            }
        }

        applyPinMetadata(view, placement);
        view.setLastUpdated(LocalDateTime.now());

        log.debug("Создана ScheduleView: date={}, slot={}, discipline={}",
                view.getScheduledDate(), view.getTimeSlot(), view.getDisciplineAbbr());

        return view;
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Создаёт ScheduleView для конкретной группы из LessonPlacement.
     *
     * <p>Используется в варианте 3: один placement → несколько ScheduleView (по одной на группу).</p>
     *
     * @param placement Размещение
     * @param group Группа
     * @return ScheduleView для конкретной группы
     */
    private ScheduleView createViewFromPlacementForGroup(LessonPlacement placement, ru.entity.Group group) {
        Assignment assignment = placement.getAssignment();

        // ✅ Создаём view с автоматической генерацией id (JPA сгенерирует UUID)
        ScheduleView view = new ScheduleView();

        // Заполняем данные размещения
        view.setScheduledDate(placement.getScheduledDate());
        view.setTimeSlot(placement.getScheduledSlot());
        view.setPlacementId(placement.getId());

        // Заполняем денормализованные данные из Assignment
        if (assignment != null) {
            view.setDiscipline(
                extractDisciplineName(assignment),
                extractDisciplineAbbr(assignment)
            );
            view.setKindOfStudy(assignment.getCurriculumSlot().getKindOfStudy().name());
            view.setCurriculumSlotId(assignment.getCurriculumSlot().getId());

            if (assignment.getCurriculumSlot().getThemeLesson() != null) {
                view.setTheme(
                    assignment.getCurriculumSlot().getThemeLesson().getThemeNumber(),
                    assignment.getCurriculumSlot().getThemeLesson().getTitle()
                );
            }
        }

        // Заполняем данные из Placement (аудитории)
        if (placement.getAssignedAuditoriums() != null && !placement.getAssignedAuditoriums().isEmpty()) {
            view.setAuditorium(
                placement.getAssignedAuditoriums().iterator().next().getId(),
                placement.getAssignedAuditoriums().iterator().next().getName()
            );
        }

        // ✅ Заполняем данные для КОНКРЕТНОЙ ГРУППЫ
        if (assignment != null) {
            // Преподаватели
            if (assignment.getEducators() != null && !assignment.getEducators().isEmpty()) {
                view.setEducator(
                    assignment.getEducators().iterator().next().getId(),
                    assignment.getEducators().iterator().next().getName()
                );
            }

            // ✅ Конкретная группа (не stream!)
            view.setGroup(group.getId(), group.getName());
        }

        applyPinMetadata(view, placement);
        view.setLastUpdated(LocalDateTime.now());

        log.debug("Создана ScheduleView для группы: date={}, slot={}, group={}, discipline={}",
                view.getScheduledDate(), view.getTimeSlot(), group.getName(), view.getDisciplineAbbr());

        return view;
    }

    /**
     * Обновляет ScheduleView из LessonPlacement.
     *
     * @param view Существующая view
     * @param placement Размещение
     */
    private void updateViewFromPlacement(ScheduleView view, LessonPlacement placement) {
        view.setScheduledDate(placement.getScheduledDate());
        view.setTimeSlot(placement.getScheduledSlot());
        view.setPlacementId(placement.getId());

        // Обновляем аудитории (после переноса они могли измениться)
        if (placement.getAssignedAuditoriums() != null && !placement.getAssignedAuditoriums().isEmpty()) {
            view.setAuditorium(
                placement.getAssignedAuditoriums().iterator().next().getId(),
                placement.getAssignedAuditoriums().iterator().next().getName()
            );
        }

        applyPinMetadata(view, placement);
        view.setLastUpdated(LocalDateTime.now());

        log.debug("Обновлена ScheduleView: date={}, slot={}",
                view.getScheduledDate(), view.getTimeSlot());
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Обновляет ScheduleView для конкретной группы из LessonPlacement.
     *
     * @param view Существующая view
     * @param placement Размещение
     * @param group Группа
     */
    private void updateViewFromPlacementForGroup(ScheduleView view, LessonPlacement placement, ru.entity.Group group) {
        view.setScheduledDate(placement.getScheduledDate());
        view.setTimeSlot(placement.getScheduledSlot());
        view.setPlacementId(placement.getId());

        // ✅ Обновляем группу
        view.setGroup(group.getId(), group.getName());

        // Обновляем аудитории
        if (placement.getAssignedAuditoriums() != null && !placement.getAssignedAuditoriums().isEmpty()) {
            view.setAuditorium(
                placement.getAssignedAuditoriums().iterator().next().getId(),
                placement.getAssignedAuditoriums().iterator().next().getName()
            );
        }

        applyPinMetadata(view, placement);
        view.setLastUpdated(LocalDateTime.now());

        log.debug("Обновлена ScheduleView для группы: date={}, slot={}, group={}",
                view.getScheduledDate(), view.getTimeSlot(), group.getName());
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
