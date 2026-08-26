import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { StudyPeriodDto } from '../../types/api';
import { ResourceService } from '../../services/apiServices';
import { AlertCircle, Calendar } from 'lucide-react';
import { useToast } from '../../context/ToastContext';
import { errorMessage } from '../../services/apiError';

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
  /** Список периодов не загрузился — причина с сервера; `null`, если всё в порядке. */
  loadError: string | null;
  /** Повторить первичную загрузку (кнопка в селекторе периода). */
  retryLoad: () => void;
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
  const toast = useToast();
  const [periods, setPeriods] = useState<StudyPeriodDto[]>([]);
  const [selectedPeriodId, setId] = useState<number | null>(readInitialId);
  const [loading, setLoading] = useState(true);
  // Пустой список периодов и НЕзагруженный список выглядят в селекторе одинаково — «— период —».
  // Разница решающая: в первом случае период надо завести, во втором — повторить запрос.
  const [loadError, setLoadError] = useState<string | null>(null);

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
  const cancelledRef = useRef(false);
  const loadPeriods = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    Promise.all([
      ResourceService.getStudyPeriods(),
      // Активный период — уточнение, а не условие: без него выбор просто падает на первый.
      ResourceService.getActiveStudyPeriod().catch(() => null),
    ])
      .then(([all, active]) => {
        if (cancelledRef.current) return;
        setPeriods(all);
        setId((prev) => {
          const resolved = prev != null && all.some((p) => p.id === prev)
            ? prev
            : (active?.id ?? all[0]?.id ?? null);
          if (resolved != null) localStorage.setItem(STORAGE_KEY, String(resolved));
          return resolved;
        });
      })
      .catch((e) => {
        if (cancelledRef.current) return;
        // Период — вход во все разделы: без него не откроются ни планировщик, ни расписание.
        // Поэтому и тост (человек мог смотреть в раздел), и признак в самом селекторе.
        setLoadError(errorMessage(e, 'Не удалось загрузить учебные периоды.'));
        toast.failure(e, 'Не удалось загрузить учебные периоды.', { label: 'Повторить', run: loadPeriods });
      })
      .finally(() => { if (!cancelledRef.current) setLoading(false); });
  }, [toast]);

  useEffect(() => {
    cancelledRef.current = false;
    loadPeriods();
    return () => { cancelledRef.current = true; };
  }, [loadPeriods]);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;

  return (
    <PeriodContext.Provider
      value={{ periods, selectedPeriodId, selectedPeriod, setSelectedPeriodId, reloadPeriods, loading,
               loadError, retryLoad: loadPeriods }}
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
  const { periods, selectedPeriodId, setSelectedPeriodId, loadError, retryLoad } = usePeriod();

  // Не загрузилось — говорим об этом на месте селектора и даём выход. Пустой выпадающий
  // список здесь читался бы как «периодов не заведено».
  if (loadError) {
    return (
      <button
        type="button"
        onClick={retryLoad}
        title={loadError}
        className={`flex items-center gap-1.5 text-xs font-bold text-red-700 border border-red-200
                    bg-red-50 rounded-lg px-2.5 py-1.5 hover:bg-red-100 transition-colors ${className ?? ''}`}
      >
        <AlertCircle size={14} className="shrink-0" />
        Периоды не загрузились · Повторить
      </button>
    );
  }

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
