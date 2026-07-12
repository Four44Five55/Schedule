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
 * <p>«Занято» берётся из {@code schedule_view} по строкам этой группы (вариант 3: одна строка
 * на группу потока). {@code remaining} и {@code free13} — производные, но считаются на бэке,
 * чтобы фронт не повторял капасити-логику.</p>
 *
 * @param groupId     id группы
 * @param groupName   название группы
 * @param demand      всего занятий к размещению для группы (набор генерации периода)
 * @param placed13    размещено в парах 1–3
 * @param inFourth    размещено в 4-й паре
 * @param remaining   осталось разместить {@code max(0, demand - placed13 - inFourth)}
 * @param capacity13  реально доступные ячейки пар 1–3 за период (с учётом закрытых пар и ограничений)
 * @param free13      свободная ёмкость 1–3 {@code capacity13 - placed13} (может быть &lt; 0 при перегрузе)
 */
public record GroupDensityDto(
        Integer groupId,
        String groupName,
        int demand,
        int placed13,
        int inFourth,
        int remaining,
        int capacity13,
        int free13
) {}
