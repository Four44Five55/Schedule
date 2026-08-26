import { useEffect, useState } from 'react';
import { DayOfWeek, TimeSlotPair } from '../../types/api';
import { ResourceService } from '../../services/apiServices';

/** Приоритеты преподавателя: предпочитаемые дни недели и пары (источник истины — бэк, EducatorDto). */
export interface EducatorPriority {
  days: DayOfWeek[];
  slots: TimeSlotPair[];
}

/**
 * Приоритеты выбранного преподавателя по id — для подсветки «замороженных» колонок сетки.
 *
 * SRP: загрузка одного {@code EducatorDto} и извлечение его {@code preferredDays}/{@code preferredTimeSlots}
 * (классификация «приоритетно/нет» — тривиальное вхождение в множество, делается в компоненте).
 * {@code active=false} или пустой id → {@code null} (для видов «группа»/«аудитория» приоритета нет).
 * Ветвление по типу сущности остаётся у хоста — хук знает только про преподавателя (DIP).
 */
export function useEducatorPriority(educatorId?: number, active: boolean = true): EducatorPriority | null {
  const [priority, setPriority] = useState<EducatorPriority | null>(null);

  useEffect(() => {
    if (!active || !educatorId) {
      setPriority(null);
      return;
    }
    let cancelled = false;
    ResourceService.getEducator(educatorId)
      .then((e) => { if (!cancelled) setPriority({ days: e.preferredDays, slots: e.preferredTimeSlots }); })
      // Единственный отказ, о котором намеренно молчим: предпочтения — оформление ячеек
      // (рамка «любимый день»), их отсутствие ничего не утверждает о расписании и ни к какому
      // неверному действию не ведёт. Те же данные видны в карточке преподавателя.
      .catch(() => { if (!cancelled) setPriority(null); });
    return () => { cancelled = true; };
  }, [educatorId, active]);

  return priority;
}
