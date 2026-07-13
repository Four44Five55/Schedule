package ru.dto;

/**
 * Плотность одной группы в парах 1–3 за учебный период (Query Side, для дашборда).
 *
 * <p>Считается на бэке, потому что для честного расчёта нужны данные, которых у фронта нет:</p>
 * <ul>
 *   <li><b>спрос</b> ({@code demand}) — сколько занятий вообще нужно разместить группе;
 *       знает только набор генерации ({@code GenerationScope.lessons()}), не {@code schedule_view};</li>
 *   <li><b>реальная ёмкость 1–3</b> ({@code capacity13}) — доступные ячейки пар 1–3 с учётом
 *       закрытых бэком дней/пар ({@code ScheduleDaysSlotsConfig}: Вс целиком, Сб без 4-й)
 *       и групповых ограничений (командировки/сессии и т.п.), развёрнутых в ячейки.</li>
 * </ul>
 *
 * <p>«Занято» берётся из {@code schedule_view} — но считается по <b>уникальным размещениям</b>,
 * а не по строкам: с миграции 016 одно занятие даёт строку на КАЖДОГО преподавателя (совместные
 * занятия, напр. английский вдвоём). Подсчёт «в лоб» завышал занятость и уводил {@code free13}
 * в минус при реально свободных парах.</p>
 *
 * <p>{@code remaining}, {@code free13} и {@code mustGoToFourth} — производные, но считаются на
 * бэке, чтобы фронт не повторял капасити-логику.</p>
 *
 * @param groupId        id группы
 * @param groupName      название группы
 * @param demand         всего занятий к размещению для группы (набор генерации периода)
 * @param placed13       размещено в парах 1–3 (уникальных занятий)
 * @param inFourth       размещено в 4-й паре
 * @param remaining      осталось разместить {@code max(0, demand - placed13 - inFourth)}
 * @param capacity13     реально доступные ячейки пар 1–3 за период (закрытые пары и ограничения вычтены)
 * @param free13         свободных пар 1–3 {@code max(0, capacity13 - placed13)}
 * @param mustGoToFourth сколько из оставшихся занятий в пары 1–3 <b>не влезет</b>
 *                       ({@code max(0, remaining - free13)}) — их придётся ставить в 4-ю пару
 */
public record GroupDensityDto(
        Integer groupId,
        String groupName,
        int demand,
        int placed13,
        int inFourth,
        int remaining,
        int capacity13,
        int free13,
        int mustGoToFourth
) {}
