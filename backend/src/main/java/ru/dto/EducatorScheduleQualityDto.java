package ru.dto;

/**
 * Качество расписания одного преподавателя за период (Query Side, из schedule_view).
 *
 * <p>Объединяет две грани per-educator качества:</p>
 * <ul>
 *   <li><b>компактность</b> — окна внутри дня ({@code windowSlots}), одиночные дни
 *       ({@code singlePairDays}), лишние дни ({@code excessDays}); свёрнуто в {@code penalty};</li>
 *   <li><b>равномерность нагрузки</b> — субботние пары ({@code saturdayPairs}) и отклонение
 *       от среднего по преподавателям ({@code saturdayDeviation}).</li>
 * </ul>
 *
 * <p>{@code penalty} — только про компактность (суббота в него не входит, чтобы метрика
 * оставалась прозрачной). Считается только по учебным дням преподавателя.</p>
 *
 * @param educatorId       id преподавателя
 * @param educatorName     ФИО
 * @param compact          стоит ли флаг {@code compact_schedule} (приоритет компактности)
 * @param teachingDays     число дней, в которые преподаватель ведёт занятия
 * @param totalPairs       всего пар за период (уникальных (день, пара))
 * @param avgPairsPerDay   средняя загрузка учебного дня (цель 2–3)
 * @param singlePairDays   дней ровно с одной парой (ось междневная)
 * @param windowDays       дней, где есть хотя бы одно окно
 * @param windowSlots      суммарно окон-слотов (ось внутридневная)
 * @param excessDays       «лишние» дни сверх идеала {@code teachingDays - ceil(totalPairs/3)} (≥0)
 * @param penalty          сводный штраф компактности (меньше = лучше)
 * @param saturdayPairs    пар в субботы за период
 * @param saturdayDeviation отклонение субботних пар от среднего по преподавателям (+ выше)
 */
public record EducatorScheduleQualityDto(
        Integer educatorId,
        String educatorName,
        boolean compact,
        int teachingDays,
        int totalPairs,
        double avgPairsPerDay,
        int singlePairDays,
        int windowDays,
        int windowSlots,
        int excessDays,
        int penalty,
        int saturdayPairs,
        double saturdayDeviation
) {}
