package ru.services.projection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.ProjectionHealthDto;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;
import ru.repository.read.ScheduleViewRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;

import java.util.List;
import java.util.UUID;

/**
 * Сходятся ли Command Side и Query Side по периоду (read-сервис, SRP — только сверка).
 *
 * <p><b>Зачем.</b> Проекция обновляется асинхронно (@Async, AFTER_COMMIT). Если слушатель упал,
 * приложение перезапустили в неудачный момент или размещение удалили ровно во время проекции —
 * занятие просто не появится в сетке, а единственным следом останется строка в логе. В логе её
 * никто не увидит. Здесь расхождение превращается в число, которое интерфейс показывает рядом с
 * кнопкой «Пересобрать read-модель» ({@code POST /sessions/{id}/reproject}).</p>
 *
 * <p>Проверяется <b>одна</b> сторона расхождения — «размещение есть, строк проекции нет».
 * Обратная («строка есть, размещения нет») невозможна по схеме: FK
 * {@code schedule_view → lesson_placement ON DELETE CASCADE} (миграция 017).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionHealthService {

    private final ScheduleSessionRepository sessionRepository;
    private final LessonPlacementRepository placementRepository;
    private final ScheduleViewRepository viewRepository;

    /**
     * Сверка по живой сессии периода.
     *
     * @param periodId учебный период
     * @return счётчики; при отсутствии живой сессии — нули (сверять нечего)
     */
    @Transactional(readOnly = true)
    public ProjectionHealthDto check(Integer periodId) {
        List<ScheduleSession> active =
                sessionRepository.findActiveSessionsByPeriod(periodId, SessionStatus.ARCHIVED);
        if (active.isEmpty()) {
            return new ProjectionHealthDto(null, 0, 0, 0);
        }

        ScheduleSession session = active.get(0);
        // Только id: сверка не читает содержимое размещений, грузить сущности (их полторы тысячи)
        // ради двух счётчиков незачем — дашборд дёргает этот срез на каждой загрузке.
        List<UUID> placementIds = placementRepository.findIdsBySessionId(session.getId());
        if (placementIds.isEmpty()) {
            return new ProjectionHealthDto(session.getId(), 0, 0, 0);
        }

        int projected = viewRepository.findProjectedPlacementIds(placementIds).size();
        int missing = placementIds.size() - projected;
        if (missing > 0) {
            log.warn("⚠️ Проекция отстала: сессия {} — не спроецировано {} из {} размещений",
                    session.getId(), missing, placementIds.size());
        }
        return new ProjectionHealthDto(session.getId(), placementIds.size(), projected, missing);
    }
}
