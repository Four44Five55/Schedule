package ru.dto;

/**
 * Готовность расписания учебного периода — для дашборда.
 *
 * <p>Query Side (schedule_view) знает только про уже размещённые занятия, поэтому «всего
 * к размещению» берётся из того же набора, что идёт в генерацию ({@code GenerationScope.lessons()}),
 * но БЕЗ запуска распределения. Так дашборд получает актуальные total/placed/unplaced.</p>
 *
 * @param total    всего занятий к размещению (набор генерации периода)
 * @param placed   размещено (уникальных placement в schedule_view за период)
 * @param unplaced не размещено ({@code total - placed}, не меньше 0)
 */
public record PeriodReadinessDto(int total, int placed, int unplaced) {}
