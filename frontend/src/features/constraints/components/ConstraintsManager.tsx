import React, { useMemo } from 'react';
import { parseISO } from 'date-fns';
import { ShieldAlert } from 'lucide-react';
import { usePeriod } from '../../period/PeriodContext';
import { ConstraintsWorkspace } from './ConstraintsWorkspace';

/**
 * Раздел «Ограничения» — тонкий хост над переиспользуемым {@link ConstraintsWorkspace}.
 * Учебный период берётся из общего контекста (единый выбор в шапке приложения) — своего
 * селектора у раздела больше нет. Та же рабочая область используется во вкладке
 * планировщика (единый редактор, разные хосты, DRY).
 */
export const ConstraintsManager: React.FC = () => {
  const { selectedPeriod, loading } = usePeriod();

  const periodDates = useMemo(() => {
    if (!selectedPeriod) return null;
    return { start: parseISO(selectedPeriod.startDate), end: parseISO(selectedPeriod.endDate) };
  }, [selectedPeriod]);

  if (loading) {
    return <div className="p-8 text-center animate-pulse text-slate-400">Загрузка периодов...</div>;
  }

  return (
    <div className="space-y-4">
      {periodDates ? (
        <ConstraintsWorkspace startDate={periodDates.start} endDate={periodDates.end} />
      ) : (
        <div className="bg-white border border-slate-100 rounded-xl p-8 shadow-sm text-center">
          <div className="flex flex-col items-center gap-3">
            <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
              <ShieldAlert className="text-slate-400" size={24} />
            </div>
            <p className="text-sm font-bold text-slate-900">Выберите учебный период в шапке</p>
          </div>
        </div>
      )}
    </div>
  );
};
