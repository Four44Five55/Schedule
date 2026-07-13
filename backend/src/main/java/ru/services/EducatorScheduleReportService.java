package ru.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.EducatorScheduleQualityDto;
import ru.dto.PeriodScheduleQualityDto;
import ru.entity.Educator;
import ru.entity.StudyPeriod;
import ru.entity.read.ScheduleView;
import ru.entity.write.LessonPlacement;
import ru.repository.read.ScheduleViewRepository;
import ru.repository.write.LessonPlacementRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Аналитика качества расписания преподавателей (Query Side) — считает метрики из готового
 * {@code schedule_view} за период, без обращения к солверу.
 *
 * <p>Единый субстрат «преподаватель → день → множество пар» строится один раз (DRY), из него
 * одним проходом считаются обе грани per-educator качества:</p>
 * <ul>
 *   <li><b>компактность</b> — окна ((maxИдx − minИдx + 1) − пар), одиночные дни, лишние дни, штраф;</li>
 *   <li><b>равномерность</b> — субботние пары и отклонение от среднего по преподавателям.</li>
 * </ul>
 *
 * <p>Метрики когерентны и читают один субстрат, поэтому вынесение в Strategy-контрибьюторы
 * сейчас было бы преждевременным (YAGNI). Точка расширения: если метрик станет заметно больше
 * и они начнут появляться независимо — извлечь интерфейс {@code EducatorMetricContributor}.</p>
 *
 * <p><b>Оговорка по скоупу:</b> {@code schedule_view} не несёт периода, поэтому скоуп берётся
 * по датам периода (как в {@code readiness}). При нескольких активных сессиях с пересекающимися
 * датами метрика их смешает — это вопрос гигиены сессий, решается отдельно.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EducatorScheduleReportService {

    // Веса штрафа компактности (эмпирические, подлежат подстройке). Окно = 1 (база).
    private static final int WEIGHT_SINGLE_DAY = 2; // штраф за день с одной парой
    private static final int WEIGHT_EXCESS_DAY = 1; // штраф за «лишний» день сверх идеала
    private static final double IDEAL_PAIRS_PER_DAY = 3.0;

    private final ScheduleViewRepository viewRepository;
    private final EducatorService educatorService;
    private final StudyPeriodService studyPeriodService;
    private final LessonPlacementRepository placementRepository;

    /** Индекс 4-й пары в {@code TimeSlotPair} (FIRST..FOURTH → 0..3). */
    private static final int FOURTH_SLOT_INDEX = 3;

    /** Сырые счётчики одного преподавателя до нормировки (субботнее отклонение считается по всем). */
    private record Raw(Integer id, String name, boolean compact, int teachingDays, int totalPairs,
                       int singlePairDays, int windowDays, int windowSlots,
                       int fourthPairs, int saturdayPairs) {}

    @Transactional(readOnly = true)
    public PeriodScheduleQualityDto compute(Integer periodId) {
        StudyPeriod period = studyPeriodService.getEntityById(periodId);
        List<ScheduleView> views = viewRepository.findByPeriod(period.getStartDate(), period.getEndDate());

        Map<Integer, Educator> educatorById = educatorService.getAllEntities().stream()
                .collect(Collectors.toMap(Educator::getId, e -> e, (a, b) -> a));

        // schedule_view хранит лишь одного преподавателя на строку, но занятие могут вести
        // несколько. Восстанавливаем полный состав через placement_id, чтобы «задействованными
        // в расписании» считались все преподаватели, включая со-преподавателей.
        Set<UUID> placementIds = views.stream()
                .map(ScheduleView::getPlacementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, List<Integer>> educatorsByPlacement = placementIds.isEmpty() ? Map.of()
                : placementRepository.findByIdInWithEducators(placementIds).stream()
                        .collect(Collectors.toMap(
                                LessonPlacement::getId,
                                p -> p.getAssignment().getEducators().stream()
                                        .map(Educator::getId).collect(Collectors.toList())));

        // Субстрат: educatorId → (дата → множество индексов пар этого дня).
        // Множество слотов дедуплицирует несколько view-строк одного занятия (по группам),
        // поэтому кредитование каждого преподавателя не завышает его нагрузку.
        Map<Integer, Map<LocalDate, Set<Integer>>> byEducatorDay = new HashMap<>();
        for (ScheduleView v : views) {
            if (v.getTimeSlot() == null || v.getScheduledDate() == null) continue;

            List<Integer> educatorIds = educatorsByPlacement.get(v.getPlacementId());
            if (educatorIds == null || educatorIds.isEmpty()) {
                // Фолбэк на денормализованного преподавателя view (легаси-строки без placement).
                if (v.getEducatorId() == null) continue;
                educatorIds = List.of(v.getEducatorId());
            }

            for (Integer educatorId : educatorIds) {
                byEducatorDay
                        .computeIfAbsent(educatorId, k -> new HashMap<>())
                        .computeIfAbsent(v.getScheduledDate(), k -> new HashSet<>())
                        .add(v.getTimeSlot().ordinal()); // FIRST..FOURTH → 0..3
            }
        }

        // Проход 1: сырые метрики по каждому преподавателю (включая субботние пары).
        List<Raw> raws = new ArrayList<>();
        for (Map.Entry<Integer, Map<LocalDate, Set<Integer>>> entry : byEducatorDay.entrySet()) {
            Integer educatorId = entry.getKey();
            Educator educator = educatorById.get(educatorId);
            String name = educator != null ? educator.getName() : ("#" + educatorId);
            boolean compact = educator != null && educator.isCompactSchedule();

            int teachingDays = 0, totalPairs = 0, singlePairDays = 0, windowDays = 0, windowSlots = 0,
                    fourthPairs = 0, saturdayPairs = 0;
            for (Map.Entry<LocalDate, Set<Integer>> day : entry.getValue().entrySet()) {
                Set<Integer> slots = day.getValue();
                int pairs = slots.size();
                int windows = (Collections.max(slots) - Collections.min(slots) + 1) - pairs;
                teachingDays++;
                totalPairs += pairs;
                if (pairs == 1) singlePairDays++;
                if (windows > 0) { windowDays++; windowSlots += windows; }
                // 4-я пара в дне может быть только одна — считаем дни, где она занята.
                if (slots.contains(FOURTH_SLOT_INDEX)) fourthPairs++;
                if (day.getKey().getDayOfWeek() == DayOfWeek.SATURDAY) saturdayPairs += pairs;
            }
            raws.add(new Raw(educatorId, name, compact, teachingDays, totalPairs,
                    singlePairDays, windowDays, windowSlots, fourthPairs, saturdayPairs));
        }

        // Базовое среднее субботних пар — по ВСЕМ ведущим (для отклонений).
        double avgSaturday = raws.isEmpty() ? 0.0
                : round2(raws.stream().mapToInt(Raw::saturdayPairs).average().orElse(0.0));

        // Проход 2: нормировка в DTO (штраф, средняя загрузка дня, субботнее отклонение).
        List<EducatorScheduleQualityDto> items = raws.stream().map(r -> {
            int idealDays = (int) Math.ceil(r.totalPairs() / IDEAL_PAIRS_PER_DAY);
            int excessDays = Math.max(0, r.teachingDays() - idealDays);
            double avgPairsPerDay = r.teachingDays() > 0 ? round2((double) r.totalPairs() / r.teachingDays()) : 0.0;
            int penalty = r.windowSlots() + WEIGHT_SINGLE_DAY * r.singlePairDays() + WEIGHT_EXCESS_DAY * excessDays;
            double saturdayDeviation = round2(r.saturdayPairs() - avgSaturday);
            return new EducatorScheduleQualityDto(
                    r.id(), r.name(), r.compact(), r.teachingDays(), r.totalPairs(), avgPairsPerDay,
                    r.singlePairDays(), r.windowDays(), r.windowSlots(), excessDays, penalty,
                    r.fourthPairs(), r.saturdayPairs(), saturdayDeviation);
        }).collect(Collectors.toCollection(ArrayList::new));

        // Компактные — первыми, внутри — по убыванию штрафа (сначала «худшие»).
        items.sort(Comparator
                .comparing(EducatorScheduleQualityDto::compact).reversed()
                .thenComparing(Comparator.comparingInt(EducatorScheduleQualityDto::penalty).reversed()));

        // Сводка компактности — только по флаговым преподавателям.
        List<EducatorScheduleQualityDto> flagged = items.stream().filter(EducatorScheduleQualityDto::compact).toList();
        int compactEducators = flagged.size();
        int wellPacked = (int) flagged.stream().filter(d -> d.windowSlots() == 0 && d.singlePairDays() == 0).count();
        double avgPenalty = round2(flagged.stream().mapToInt(EducatorScheduleQualityDto::penalty).average().orElse(0.0));
        int totalSinglePairDays = flagged.stream().mapToInt(EducatorScheduleQualityDto::singlePairDays).sum();
        int totalWindowSlots = flagged.stream().mapToInt(EducatorScheduleQualityDto::windowSlots).sum();

        log.info("Educator quality: период id={}, флаговых={}, плотно уложены={}, ср.штраф={}, ср.суббота={}",
                periodId, compactEducators, wellPacked, avgPenalty, avgSaturday);
        return new PeriodScheduleQualityDto(compactEducators, wellPacked, avgPenalty,
                totalSinglePairDays, totalWindowSlots, avgSaturday, items);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
