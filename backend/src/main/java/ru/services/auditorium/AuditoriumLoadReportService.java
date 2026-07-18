package ru.services.auditorium;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.auditorium.AuditoriumLoadDto;
import ru.dto.auditorium.PeriodAuditoriumLoadDto;
import ru.entity.Auditorium;
import ru.entity.StudyPeriod;
import ru.entity.write.LessonPlacement;
import ru.entity.write.ScheduleSession;
import ru.enums.SessionStatus;
import ru.enums.TimeSlotPair;
import ru.repository.write.LessonPlacementRepository;
import ru.repository.write.ScheduleSessionRepository;
import ru.services.AuditoriumService;
import ru.services.ScheduleDaysSlotsConfig;
import ru.services.StudyPeriodService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Загрузка аудиторий за период (Query Side) — зеркало {@link ru.services.EducatorScheduleReportService},
 * но метрики про <b>утилизацию</b>: сколько комната занята от доступного, а не компактность (у комнаты
 * нет «окон/штрафа»).
 *
 * <p><b>Источник — write-сторона (живая сессия периода), как в {@link AuditoriumHealthService}.</b>
 * В отличие от отчёта преподавателей, здесь нельзя читать {@code schedule_view}: он несёт лишь одну
 * комнату из N ({@code auditorium_id} — одна колонка), а у занятия комнат может быть несколько.
 * По размещениям видно все.</p>
 *
 * <p><b>Знаменатель загрузки</b> — открытые ячейки периода ({@link ScheduleDaysSlotsConfig#isSlotAvailable}):
 * дни × доступные пары (Вс и Сб-4 закрыты; Пт-4 — по текущему конфигу). Ограничения самих комнат
 * (ремонт) из знаменателя пока НЕ вычитаются — загрузка считается «от физической сетки».</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriumLoadReportService {

    /** Индекс 4-й пары в {@code TimeSlotPair} (FIRST..FOURTH → 0..3). */
    private static final int FOURTH_SLOT_INDEX = 3;

    private final ScheduleSessionRepository sessionRepository;
    private final LessonPlacementRepository placementRepository;
    private final StudyPeriodService studyPeriodService;
    private final AuditoriumService auditoriumService;

    @Transactional(readOnly = true)
    public PeriodAuditoriumLoadDto compute(Integer periodId) {
        StudyPeriod period = studyPeriodService.getEntityById(periodId);
        int availablePairs = countAvailableCells(period.getStartDate(), period.getEndDate());

        // Занятость по живой сессии периода: комната → (дата → множество индексов пар).
        // Множество дедуплицирует ячейку, если её делят несколько занятий (двойное бронирование
        // считается за одну занятую ячейку — это про время; конфликты — отдельный датчик health).
        Map<Integer, Map<LocalDate, Set<Integer>>> byRoomDay = new HashMap<>();
        List<ScheduleSession> active = sessionRepository.findActiveSessionsByPeriod(periodId, SessionStatus.ARCHIVED);
        if (!active.isEmpty()) {
            for (LessonPlacement p : placementRepository.findBySessionId(active.get(0).getId())) {
                if (p.getScheduledDate() == null || p.getScheduledSlot() == null) continue;
                Set<Auditorium> rooms = p.getAssignedAuditoriums();
                if (rooms == null) continue;
                for (Auditorium room : rooms) {
                    byRoomDay
                            .computeIfAbsent(room.getId(), k -> new HashMap<>())
                            .computeIfAbsent(p.getScheduledDate(), k -> new HashSet<>())
                            .add(p.getScheduledSlot().ordinal());
                }
            }
        }

        // Строим строки по ВСЕМ комнатам (простаивающие тоже видны — загрузка 0 %).
        List<AuditoriumLoadDto> items = new ArrayList<>();
        for (Auditorium room : auditoriumService.getAllEntities()) {
            Map<LocalDate, Set<Integer>> days = byRoomDay.getOrDefault(room.getId(), Map.of());
            int occupied = 0, fourthPairs = 0, saturdayPairs = 0;
            for (Map.Entry<LocalDate, Set<Integer>> day : days.entrySet()) {
                Set<Integer> slots = day.getValue();
                occupied += slots.size();
                if (slots.contains(FOURTH_SLOT_INDEX)) fourthPairs++; // 4-я пара в дне одна
                if (day.getKey().getDayOfWeek() == DayOfWeek.SATURDAY) saturdayPairs += slots.size();
            }
            int daysUsed = days.size();
            int free = Math.max(0, availablePairs - occupied);
            double load = availablePairs > 0 ? round2(occupied * 100.0 / availablePairs) : 0.0;
            double avgPerDay = daysUsed > 0 ? round2((double) occupied / daysUsed) : 0.0;
            items.add(new AuditoriumLoadDto(room.getId(), room.getName(), room.getCapacity(),
                    occupied, free, load, daysUsed, avgPerDay, fourthPairs, saturdayPairs));
        }

        // Самые загруженные первыми, при равенстве — по имени.
        items.sort(Comparator.comparingDouble(AuditoriumLoadDto::loadPercent).reversed()
                .thenComparing(d -> d.name() != null ? d.name() : ""));

        int roomsUsed = (int) items.stream().filter(d -> d.occupiedPairs() > 0).count();
        double avgLoad = round2(items.stream().filter(d -> d.occupiedPairs() > 0)
                .mapToDouble(AuditoriumLoadDto::loadPercent).average().orElse(0.0));

        log.info("Auditorium load: период id={}, комнат={}, задействовано={}, ср.загрузка={}%, доступно ячеек={}",
                periodId, items.size(), roomsUsed, avgLoad, availablePairs);
        return new PeriodAuditoriumLoadDto(items.size(), roomsUsed, items.size() - roomsUsed,
                availablePairs, avgLoad, items);
    }

    /** Открытых ячеек «дата×пара» за период — знаменатель загрузки (Вс/Сб-4 закрыты). */
    private static int countAvailableCells(LocalDate start, LocalDate end) {
        int n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            for (TimeSlotPair slot : TimeSlotPair.values()) {
                if (ScheduleDaysSlotsConfig.isSlotAvailable(d, slot)) n++;
            }
        }
        return n;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
