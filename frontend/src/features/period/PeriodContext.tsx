import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { StudyPeriodDto } from '../../types/api';
import { ResourceService } from '../../services/apiServices';
import { Calendar } from 'lucide-react';

/**
 * Единый источник правды по выбранному учебному периоду — общий для планировщика,
 * расписания, ограничений и дашборда. Раньше каждый раздел держал свой period-state
 * и свой селектор (рассинхрон), теперь период выбирается в ОДНОМ месте (шапка), а
 * разделы читают его из этого контекста.
 */

const STORAGE_KEY = 'unischedule.selectedPeriodId';
// Ключи прежних пер-раздельных выборов — читаем как резерв при первом запуске,
// чтобы у пользователя не «слетел» ранее выбранный период.
const LEGACY_KEYS = ['unischedule.planner.selectedPeriodId', 'unischedule.dashboard.selectedPeriodId'];

interface PeriodContextValue {
  periods: StudyPeriodDto[];
  selectedPeriodId: number | null;
  selectedPeriod: StudyPeriodDto | null;
  setSelectedPeriodId: (id: number | null) => void;
  /** Перечитать список периодов (после создания нового). Возвращает свежий список. */
  reloadPeriods: () => Promise<StudyPeriodDto[]>;
  /** Идёт первичная загрузка списка периодов. */
  loading: boolean;
}

const PeriodContext = createContext<PeriodContextValue | null>(null);

const readInitialId = (): number | null => {
  for (const key of [STORAGE_KEY, ...LEGACY_KEYS]) {
    const v = localStorage.getItem(key);
    if (v) return Number(v);
  }
  return null;
};

export const PeriodProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [periods, setPeriods] = useState<StudyPeriodDto[]>([]);
  const [selectedPeriodId, setId] = useState<number | null>(readInitialId);
  const [loading, setLoading] = useState(true);

  const setSelectedPeriodId = useCallback((id: number | null) => {
    setId(id);
    if (id != null) localStorage.setItem(STORAGE_KEY, String(id));
    else localStorage.removeItem(STORAGE_KEY);
  }, []);

  const reloadPeriods = useCallback(async () => {
    const all = await ResourceService.getStudyPeriods();
    setPeriods(all);
    return all;
  }, []);

  // Первичная загрузка: список периодов + активный. Приоритет выбора: сохранённый
  // (если ещё существует) → активный → первый.
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      ResourceService.getStudyPeriods(),
      ResourceService.getActiveStudyPeriod().catch(() => null),
    ])
      .then(([all, active]) => {
        if (cancelled) return;
        setPeriods(all);
        setId((prev) => {
          const resolved = prev != null && all.some((p) => p.id === prev)
            ? prev
            : (active?.id ?? all[0]?.id ?? null);
          if (resolved != null) localStorage.setItem(STORAGE_KEY, String(resolved));
          return resolved;
        });
      })
      .catch((e) => console.error('Не удалось загрузить учебные периоды:', e))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;

  return (
    <PeriodContext.Provider
      value={{ periods, selectedPeriodId, selectedPeriod, setSelectedPeriodId, reloadPeriods, loading }}
    >
      {children}
    </PeriodContext.Provider>
  );
};

export const usePeriod = (): PeriodContextValue => {
  const ctx = useContext(PeriodContext);
  if (!ctx) throw new Error('usePeriod должен вызываться внутри <PeriodProvider>');
  return ctx;
};

/**
 * Глобальный селектор периода — единственная точка смены периода (шапка приложения).
 */
export const PeriodSelect: React.FC<{ className?: string }> = ({ className }) => {
  const { periods, selectedPeriodId, setSelectedPeriodId } = usePeriod();
  return (
    <div className={`flex items-center gap-1.5 ${className ?? ''}`}>
      <Calendar size={14} className="text-blue-600 shrink-0" />
      <select
        value={selectedPeriodId ?? ''}
        onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
        title="Учебный период (общий для всех разделов)"
        className="max-w-[240px] text-xs font-bold text-slate-800 border border-slate-200 rounded-lg px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 truncate cursor-pointer"
      >
        <option value="">— период —</option>
        {periods.map((p) => (
          <option key={p.id} value={p.id}>{p.name} ({p.studyYear})</option>
        ))}
      </select>
    </div>
  );
};
