import { useMemo } from 'react';
import { eachDayOfInterval, format, parseISO } from 'date-fns';
import { ConstraintDto } from '../../../types/api';

/**
 * Карта «дата (yyyy-MM-dd) → ограничения этого дня».
 *
 * Ограничение на бэке хранится диапазоном дат [startDate, endDate]; после Feature 1 у него
 * есть опциональный {@code timeSlot}: пусто = весь день, иначе — конкретная пара. Этот хук
 * разворачивает ТОЛЬКО диапазон дат (визуальный маппинг на рисуемые недели, SRP) и НЕ
 * фильтрует по паре — за пару отвечает потребитель: ячейке (день, пара) ограничение
 * применимо, если {@code !c.timeSlot || c.timeSlot === пара}. Не забыть этот фильтр —
 * иначе пер-парное ограничение покрасит весь день (был такой баг в сетке расписания).
 *
 * В один день может попасть несколько ограничений (наложение) — храним список,
 * чтобы потребитель сам решил, что показать в аббревиатуре и что в тултипе.
 */
/**
 * Чистая версия маппинга (без React) — разворачивает диапазоны в карту «дата → ограничения».
 * Вынесена, чтобы переиспользовать и в хуке (одна сущность), и в Ганте (по каждой
 * сущности отдельно), без дублирования логики разворота диапазона (DRY).
 */
export function buildConstraintDateLookup(constraints: ConstraintDto[]): Map<string, ConstraintDto[]> {
  const map = new Map<string, ConstraintDto[]>();
  for (const c of constraints) {
    const start = parseISO(c.startDate);
    const end = parseISO(c.endDate);
    // Пропускаем битые/перевёрнутые диапазоны, чтобы eachDayOfInterval не упал.
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end < start) continue;
    for (const day of eachDayOfInterval({ start, end })) {
      const key = format(day, 'yyyy-MM-dd');
      const existing = map.get(key);
      if (existing) existing.push(c);
      else map.set(key, [c]);
    }
  }
  return map;
}

export function useConstraintLookup(constraints: ConstraintDto[]): Map<string, ConstraintDto[]> {
  return useMemo(() => buildConstraintDateLookup(constraints), [constraints]);
}
