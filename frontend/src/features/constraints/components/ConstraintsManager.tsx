import React, { useState, useEffect, useMemo } from 'react';
import { parseISO } from 'date-fns';
import { ResourceService } from '../../../services/apiServices';
import { StudyPeriodDto } from '../../../types/api';
import { ShieldAlert } from 'lucide-react';
import { ConstraintsWorkspace } from './ConstraintsWorkspace';

/**
 * Раздел «Ограничения» — тонкий хост над переиспользуемым {@link ConstraintsWorkspace}:
 * добавляет лишь выбор учебного периода, над которым редактируются ограничения. Та же
 * рабочая область используется во вкладке планировщика (там период приходит из контекста
 * планировщика) — единый редактор, разные хосты (DRY).
 */
export const ConstraintsManager: React.FC = () => {
  const [studyPeriods, setStudyPeriods] = useState<StudyPeriodDto[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<StudyPeriodDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      ResourceService.getStudyPeriods(),
      ResourceService.getActiveStudyPeriod(),
    ]).then(([periods, active]) => {
      setStudyPeriods(periods);
      setSelectedPeriod(active);
    }).finally(() => setLoading(false));
  }, []);

  const periodDates = useMemo(() => {
    if (!selectedPeriod) return null;
    return { start: parseISO(selectedPeriod.startDate), end: parseISO(selectedPeriod.endDate) };
  }, [selectedPeriod]);

  if (loading) {
    return <div className="p-8 text-center animate-pulse text-slate-400">Загрузка периодов...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="bg-white border border-slate-100 rounded-xl p-4 shadow-sm">
        <label className="block text-[11px] font-black uppercase tracking-wider text-slate-500 mb-1.5">Учебный период</label>
        <select
          value={selectedPeriod?.id || ''}
          onChange={(e) => setSelectedPeriod(studyPeriods.find((p) => p.id === Number(e.target.value)) || null)}
          className="w-full px-3 py-1.5 bg-blue-50 border border-blue-100 rounded-lg text-xs font-bold text-blue-900 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
        >
          <option value="">Выберите период...</option>
          {studyPeriods.map((p) => (
            <option key={p.id} value={p.id}>{p.name} ({p.startDate} — {p.endDate})</option>
          ))}
        </select>
      </div>

      {periodDates ? (
        <ConstraintsWorkspace startDate={periodDates.start} endDate={periodDates.end} />
      ) : (
        <div className="bg-white border border-slate-100 rounded-xl p-8 shadow-sm text-center">
          <div className="flex flex-col items-center gap-3">
            <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
              <ShieldAlert className="text-slate-400" size={24} />
            </div>
            <p className="text-sm font-bold text-slate-900">Выберите учебный период</p>
          </div>
        </div>
      )}
    </div>
  );
};
