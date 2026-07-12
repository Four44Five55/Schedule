package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.GroupDensityDto;
import ru.entity.CellForLesson;
import ru.entity.Group;
import ru.entity.Lesson;
import ru.entity.constraints.ConstraintData;
import ru.entity.read.ScheduleView;
import ru.enums.TimeSlotPair;
import ru.repository.read.ScheduleViewRepository;
import ru.services.constraints.AllConstraints;
import ru.services.constraints.ConstraintService;
import ru.services.generation.GenerationScope;
import ru.services.generation.GenerationScopeResolver;

import java.time.LocalDate;
import java.util.*;

/**
 * Плотность групп в парах 1–3 за период (Query Side) — честный расчёт «сколько ещё влезет».
 *
 * <p>Ответственность (SRP): по периоду собрать per-group {@link GroupDensityDto} с реальной
 * ёмкостью пар 1–3. В отличие от прежнего фронтового расчёта (Пн–Сб × 3), ёмкость учитывает:</p>
 * <ul>
 *   <li>закрытые бэком ячейки — {@link ScheduleDaysSlotsConfig#isSlotAvailable} (Вс целиком,
 *       Сб без 4-й; 4-я в ёмкость 1–3 и так не входит);</li>
 *   <li>групповые ограничения — {@link ConstraintService#loadAllConstraints()} развёрнуты в
 *       ячейки, вычитаются из ёмкости группы.</li>
 * </ul>
 *
 * <p>Спрос ({@code demand}) берётся из того же набора, что идёт в генерацию
 * ({@link GenerationScope#lessons()}), но БЕЗ запуска распределения — {@code schedule_view}
 * знает только про уже размещённые занятия. «Занято» — из {@code schedule_view} (вариант 3:
 * одна строка на группу потока, group_id хранится в колонке {@code study_stream_id}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDensityReportService {

    /** Пары дневной сетки 1–3 (без 4-й — она отдельным столбцом «В 4-й»). */
    private static final List<TimeSlotPair> SLOTS_13 =
            List.of(TimeSlotPair.FIRST, TimeSlotPair.SECOND, TimeSlotPair.THIRD);

    private final GenerationScopeResolver scopeResolver;
    private final ScheduleViewRepository viewRepository;
    private final ConstraintService constraintService;

    /** Сырьё по одной группе до сборки DTO. */
    private static final class Acc {
        final String name;
        int demand;
        int placed13;
        int inFourth;
        Acc(String name) { this.name = name; }
    }

    /**
     * @param periodId учебный период
     * @return плотность по группам (самые «забитые» — с наименьшим {@code free13} — первыми);
     *         при периоде без курсов — пустой список
     */
    @Transactional(readOnly = true)
    public List<GroupDensityDto> compute(Integer periodId) {
        GenerationScope scope;
        try {
            scope = scopeResolver.resolve(periodId, null);
        } catch (IllegalStateException e) {
            // Период без курсов — распределять нечего.
            log.info("Group density: период id={} без курсов, пустой отчёт", periodId);
            return List.of();
        }

        // Спрос по группам (из набора генерации): каждое занятие даёт +1 каждой группе своего потока.
        // Порядок LinkedHashMap задаёт стабильную «естественную» вторичную сортировку при равном free13.
        Map<Integer, Acc> byGroup = new LinkedHashMap<>();
        for (Lesson lesson : scope.lessons()) {
            if (lesson.getStudyStream() == null || lesson.getStudyStream().getGroups() == null) continue;
            for (Group group : lesson.getStudyStream().getGroups()) {
                byGroup.computeIfAbsent(group.getId(), k -> new Acc(group.getName())).demand++;
            }
        }
        if (byGroup.isEmpty()) return List.of();

        // Занято по группам из schedule_view (group_id хранится в колонке study_stream_id).
        LocalDate start = scope.period().getStartDate();
        LocalDate end = scope.period().getEndDate();
        for (ScheduleView v : viewRepository.findByPeriod(start, end)) {
            Acc acc = byGroup.get(v.getStudyStreamId());
            if (acc == null || v.getTimeSlot() == null) continue;
            if (v.getTimeSlot() == TimeSlotPair.FOURTH) acc.inFourth++;
            else acc.placed13++; // FIRST/SECOND/THIRD
        }

        // Доступные ячейки 1–3 за период (одинаково для всех групп; ограничения вычитаем персонально).
        List<CellForLesson> availableCells13 = availableCells13(start, end);
        int baseCapacity = availableCells13.size();
        Set<CellForLesson> availableSet = new HashSet<>(availableCells13);

        Map<Integer, List<ConstraintData>> groupConstraints =
                constraintService.loadAllConstraints().groupConstraints();

        List<GroupDensityDto> result = new ArrayList<>(byGroup.size());
        for (Map.Entry<Integer, Acc> e : byGroup.entrySet()) {
            Integer groupId = e.getKey();
            Acc acc = e.getValue();

            // Ёмкость группы = доступные 1–3 минус её ограничения, попадающие в эти ячейки.
            int blocked = 0;
            List<ConstraintData> constraints = groupConstraints.get(groupId);
            if (constraints != null) {
                Set<CellForLesson> blockedCells = new HashSet<>();
                for (ConstraintData c : constraints) {
                    if (availableSet.contains(c.cell())) blockedCells.add(c.cell());
                }
                blocked = blockedCells.size();
            }
            int capacity13 = baseCapacity - blocked;
            int free13 = capacity13 - acc.placed13;
            int remaining = Math.max(0, acc.demand - acc.placed13 - acc.inFourth);

            result.add(new GroupDensityDto(
                    groupId, acc.name, acc.demand, acc.placed13, acc.inFourth,
                    remaining, capacity13, free13));
        }

        // Самые «забитые» сверху (наименьший запас), при равенстве — по имени группы.
        result.sort(Comparator
                .comparingInt(GroupDensityDto::free13)
                .thenComparing(GroupDensityDto::groupName, Comparator.nullsLast(String::compareTo)));

        log.info("Group density: период id={}, групп={}, ёмкость 1–3 (база)={}",
                periodId, result.size(), baseCapacity);
        return result;
    }

    /**
     * Разворачивает период в список доступных ячеек пар 1–3 (Вс исключено целиком,
     * Сб 1–3 доступны). 4-я пара сюда не входит по определению столбца.
     */
    private List<CellForLesson> availableCells13(LocalDate start, LocalDate end) {
        List<CellForLesson> cells = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            for (TimeSlotPair slot : SLOTS_13) {
                if (ScheduleDaysSlotsConfig.isSlotAvailable(date, slot)) {
                    cells.add(new CellForLesson(date, slot));
                }
            }
        }
        return cells;
    }
}
