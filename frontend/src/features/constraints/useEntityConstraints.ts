import { useEffect, useState } from 'react';
import { ConstraintDto } from '../../types/api';
import { ConstraintsService } from '../../services/apiServices';

/** Тип сущности, для которой показываем ограничения на сетке расписания. */
export type ConstraintEntityKind = 'group' | 'educator' | 'auditorium';

/**
 * Реестр стратегий загрузки ограничений по типу сущности (GoF Strategy).
 * Новый тип сущности = одна строка здесь, ветки существующих хостов не трогаются
 * (OCP). Ключ — доменный тип сущности сетки, значение — способ достать её
 * ограничения по id. Все три эндпоинта возвращают DTO с общими полями ConstraintDto.
 */
const CONSTRAINT_LOADERS: Record<ConstraintEntityKind, (id: number) => Promise<ConstraintDto[]>> = {
  group: (id) => ConstraintsService.getGroupConstraintsByGroup(id),
  educator: (id) => ConstraintsService.getEducatorConstraintsByEducator(id),
  auditorium: (id) => ConstraintsService.getAuditoriumConstraintsByAuditorium(id),
};

/**
 * Ограничения выбранной сущности расписания (группа/преподаватель/аудитория).
 *
 * Единая ответственность (SRP): загрузка и жизненный цикл ограничений по сущности,
 * вынесенные из компонентов-хостов (раздел «Расписание» и планировщик), чтобы не
 * дублировать один и тот же эффект. Хост отдаёт лишь тип и разрешённый id сущности —
 * о конкретных эндпоинтах и REST не знает (DIP). Пока id не задан, список пуст.
 */
export function useEntityConstraints(
  kind: ConstraintEntityKind,
  entityId?: number,
): { constraints: ConstraintDto[]; loading: boolean } {
  const [constraints, setConstraints] = useState<ConstraintDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!entityId) {
      setConstraints([]);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    CONSTRAINT_LOADERS[kind](entityId)
      .then((data) => { if (!cancelled) setConstraints(data); })
      .catch(() => { if (!cancelled) setConstraints([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [kind, entityId]);

  return { constraints, loading };
}
