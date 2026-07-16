package ru.services.auditorium;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.auditorium.AuditoriumHealthDto;
import ru.dto.auditorium.RoomHealthDto;
import ru.entity.Auditorium;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;
import ru.enums.TimeSlotPair;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Всё ли в порядке с аудиториями в расписании периода (read-сервис, SRP — только сверка).
 *
 * <p>Сборочный слой над чистым правилом {@link AuditoriumUsageRule}: достаёт размещения живой
 * сессии, разворачивает их в пары «занятие × комната» и прогоняет правило. Сам вердикт живёт в
 * правиле и тестируется юнитами — здесь только выборка и агрегация. Тот же приём, что в
 * {@link ru.services.LessonOrderService} над {@code LessonOrderRule}.</p>
 *
 * <p><b>Скоуп — живая сессия периода</b>, как в {@link ru.services.projection.ProjectionHealthService}.
 * Это важно именно здесь: разные сессии (архив, соседний период) делят одни и те же комнаты
 * совершенно законно, и сверять их между собой значило бы выдумать сотни конфликтов на ровном
 * месте. Правило про это не знает и знать не должно — скоуп задаётся тут.</p>
 *
 * <p><b>Ничего не чинит.</b> Датчик: превращает невидимое в число. Починка подбора аудиторий —
 * следующие шаги, и этот же срез будет их проверять.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriumHealthService {

    private final ScheduleSessionRepository sessionRepository;
    private final LessonPlacementRepository placementRepo;

    private final AuditoriumUsageRule rule = new AuditoriumUsageRule();

    /**
     * Сверка по живой сессии периода.
     *
     * @param periodId учебный период
     * @return счётчики и разбивка по комнатам; при отсутствии живой сессии — нули
     */
    @Transactional(readOnly = true)
    public AuditoriumHealthDto check(Integer periodId) {
        List<ScheduleSession> active =
                sessionRepository.findActiveSessionsByPeriod(periodId, SessionStatus.ARCHIVED);
        if (active.isEmpty()) {
            return new AuditoriumHealthDto(null, 0, 0, 0, 0, List.of());
        }

        ScheduleSession session = active.get(0);
        List<LessonPlacement> placements = placementRepo.findBySessionId(session.getId());
        if (placements.isEmpty()) {
            return new AuditoriumHealthDto(session.getId(), 0, 0, 0, 0, List.of());
        }

        Map<Integer, Auditorium> rooms = new HashMap<>();
        List<RoomedLesson> roomed = new ArrayList<>();
        int checked = 0;

        for (LessonPlacement placement : placements) {
            Set<Auditorium> assigned = placement.getAssignedAuditoriums();
            if (assigned == null || assigned.isEmpty()) {
                // Занятие без комнаты — реальное состояние (аудиторию удалили, связь ушла
                // каскадом), но это отдельный симптом с отдельной причиной. Правилу его не
                // предъявить: его вход — пары «занятие × комната». Сюда добавим, когда займёмся
                // именно им.
                continue;
            }
            checked++;
            int headcount = placement.getAssignment().getStudyStream().calculateTotalSize();
            for (Auditorium room : assigned) {
                rooms.putIfAbsent(room.getId(), room);
                roomed.add(new RoomedLesson(
                        placement.getId(),
                        placement.getScheduledDate(),
                        placement.getScheduledSlot(),
                        room.getId(),
                        room.getCapacity(),
                        headcount));
            }
        }

        List<AuditoriumFinding> findings = rule.check(roomed);
        AuditoriumHealthDto health = aggregate(session.getId(), checked, findings, rooms);

        if (health.conflictingCells() > 0) {
            log.warn("⚠️ Аудитории: сессия {} — двойных бронирований {} в {} ячейках, не помещается {} занятий",
                    session.getId(), health.doubleBooked(), health.conflictingCells(), health.overCapacity());
        }
        return health;
    }

    /** Находки → счётчики. Считаем занятия и ячейки, а не находки: у занятия их может быть две. */
    private static AuditoriumHealthDto aggregate(UUID sessionId, int checked,
                                                 List<AuditoriumFinding> findings,
                                                 Map<Integer, Auditorium> rooms) {
        Set<UUID> doubleBooked = new LinkedHashSet<>();
        Set<UUID> overCapacity = new LinkedHashSet<>();
        Set<RoomCell> conflictingCells = new LinkedHashSet<>();
        Map<Integer, RoomTally> byRoom = new HashMap<>();

        for (AuditoriumFinding finding : findings) {
            RoomTally tally = byRoom.computeIfAbsent(finding.auditoriumId(), k -> new RoomTally());
            switch (finding.kind()) {
                case DOUBLE_BOOKED -> {
                    doubleBooked.add(finding.placementId());
                    conflictingCells.add(new RoomCell(finding.auditoriumId(), finding.date(), finding.slot()));
                    tally.doubleBooked.add(finding.placementId());
                    tally.cells.add(new RoomCell(finding.auditoriumId(), finding.date(), finding.slot()));
                }
                case OVER_CAPACITY -> {
                    overCapacity.add(finding.placementId());
                    tally.overCapacity.add(finding.placementId());
                    tally.maxExcess = Math.max(tally.maxExcess, finding.excess());
                }
            }
        }

        List<RoomHealthDto> breakdown = new ArrayList<>();
        for (Map.Entry<Integer, RoomTally> entry : byRoom.entrySet()) {
            Auditorium room = rooms.get(entry.getKey());
            RoomTally tally = entry.getValue();
            breakdown.add(new RoomHealthDto(
                    entry.getKey(),
                    room != null ? room.getName() : null,
                    room != null ? room.getCapacity() : 0,
                    tally.cells.size(),
                    tally.doubleBooked.size(),
                    tally.overCapacity.size(),
                    tally.maxExcess));
        }

        // Худшие первыми, и «худшее» — это конфликты: они про физику, их допустимое значение
        // ровно ноль. Переполнение — суждение, поэтому оно второй ключ, а не первый.
        breakdown.sort(Comparator
                .comparingInt(RoomHealthDto::conflictingCells).reversed()
                .thenComparing(Comparator.comparingInt(RoomHealthDto::overCapacity).reversed())
                .thenComparing(Comparator.comparingInt(RoomHealthDto::maxExcess).reversed())
                .thenComparing(r -> r.name() != null ? r.name() : ""));

        return new AuditoriumHealthDto(sessionId, checked, conflictingCells.size(),
                doubleBooked.size(), overCapacity.size(), List.copyOf(breakdown));
    }

    /** Ячейка конкретной комнаты — единица счёта конфликтов. */
    private record RoomCell(Integer auditoriumId, LocalDate date, TimeSlotPair slot) {
    }

    /** Накопитель по комнате. Множества, а не счётчики: одно занятие не должно считаться дважды. */
    private static final class RoomTally {
        private final Set<UUID> doubleBooked = new LinkedHashSet<>();
        private final Set<UUID> overCapacity = new LinkedHashSet<>();
        private final Set<RoomCell> cells = new LinkedHashSet<>();
        private int maxExcess;
    }
}
