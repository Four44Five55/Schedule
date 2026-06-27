import { useMemo } from 'react';
import { eachDayOfInterval, format, parseISO } from 'date-fns';
import { ConstraintDto } from '../../../types/api';

/**
 * Карта «дата (yyyy-MM-dd) → ограничения этого дня».
 *
 * Ограничение на бэке хранится диапазоном дат [startDate, endDate] и покрывает
 * день целиком (все пары). Для отрисовки в сетке день×пара диапазон нужно
 * «развернуть» по дням — это чисто визуальный маппинг (зависит от того, какие
 * недели рисуем), поэтому он на фронте и изолирован в одном чистом хуке (SRP).
 *
 * В один день может попасть несколько ограничений (наложение) — храним список,
 * чтобы потребитель сам решил, что показать в аббревиатуре и что в тултипе.
 */
export function useConstraintLookup(constraints: ConstraintDto[]): Map<string, ConstraintDto[]> {
  return useMemo(() => {
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
  }, [constraints]);
}
