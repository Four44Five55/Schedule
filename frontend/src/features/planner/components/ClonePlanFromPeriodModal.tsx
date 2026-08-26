import React, { useState, useEffect } from 'react';
import { StudyPeriodDto, DisciplineCourseDto } from '../../../types/api';
import { CurriculumService } from '../../../services/apiServices';
import { X, Loader2, Copy } from 'lucide-react';
import { cn } from '../../../utils/cn';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

// Наполнение текущего периода копией курсов из другого периода. Глубокую копию
// (слоты + сцепки + ремап) делает бэкенд; здесь только выбор источника и курсов.

export const ClonePlanFromPeriodModal: React.FC<{
  periods: StudyPeriodDto[];
  targetPeriod: StudyPeriodDto;
  onClose: () => void;
  onCloned: () => void;
}> = ({ periods, targetPeriod, onClose, onCloned }) => {
  const sourcePeriods = periods.filter(p => p.id !== targetPeriod.id);

  const [sourcePeriodId, setSourcePeriodId] = useState<number | null>(null);
  const [sourceCourses, setSourceCourses] = useState<DisciplineCourseDto[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (sourcePeriodId == null) {
      setSourceCourses([]);
      setSelectedIds(new Set());
      return;
    }
    setLoading(true);
    setSelectedIds(new Set());
    CurriculumService.getCourses(sourcePeriodId)
      .then(setSourceCourses)
      // Пустой список источника означает «в том периоде копировать нечего» — при отказе
      // запроса это неправда, и человек уходит искать другой период.
      .catch((e) => setError(errorMessage(e, 'Не удалось загрузить курсы выбранного периода.')))
      .finally(() => setLoading(false));
  }, [sourcePeriodId]);

  const toggle = (id: number) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const allSelected = sourceCourses.length > 0 && selectedIds.size === sourceCourses.length;
  const toggleAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(sourceCourses.map(c => c.id)));
  };

  const handleClone = async () => {
    if (selectedIds.size === 0) return;
    setSaving(true);
    setError(null);
    try {
      await CurriculumService.cloneCourses({
        sourceCourseIds: Array.from(selectedIds),
        targetPeriodId: targetPeriod.id,
      });
      onCloned();
    } catch (err: any) {
      // 409 — курс уже есть в целевом периоде (операция атомарна, ничего не скопировано).
      setError(errorMessage(err, 'Не удалось скопировать курсы.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden max-h-[90vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg"><Copy size={20} className="text-blue-600" /></div>
            <div>
              <h2 className="text-lg font-black text-slate-900">Скопировать из периода</h2>
              <p className="text-xs text-slate-500 font-medium mt-0.5">
                В период «{targetPeriod.name}»
              </p>
            </div>
          </div>
          <button onClick={onClose} disabled={saving} className="p-1 hover:bg-slate-200 rounded-lg transition-colors">
            <X size={20} className="text-slate-500" />
          </button>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          {error && (
            <ErrorBanner message={error} />
          )}

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Период-источник *</label>
            <select
              value={sourcePeriodId ?? ''}
              onChange={e => setSourcePeriodId(e.target.value ? Number(e.target.value) : null)}
              className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              <option value="">— выберите период —</option>
              {sourcePeriods.map(p => (
                <option key={p.id} value={p.id}>{p.name} ({p.studyYear})</option>
              ))}
            </select>
          </div>

          {sourcePeriodId != null && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-bold text-slate-600 uppercase tracking-wider">Курсы для копирования</label>
                {sourceCourses.length > 0 && (
                  <button onClick={toggleAll} className="text-xs text-blue-600 hover:text-blue-800 font-semibold">
                    {allSelected ? 'Снять все' : 'Выбрать все'}
                  </button>
                )}
              </div>

              {loading ? (
                <div className="flex items-center justify-center py-8 gap-2 text-slate-400 text-sm">
                  <Loader2 size={16} className="animate-spin" /> Загрузка...
                </div>
              ) : sourceCourses.length === 0 ? (
                <p className="text-sm text-slate-400 italic py-4 text-center">В этом периоде нет курсов</p>
              ) : (
                <div className="border border-slate-200 rounded-xl divide-y divide-slate-100 max-h-64 overflow-y-auto">
                  {sourceCourses.map(course => {
                    const isSelected = selectedIds.has(course.id);
                    return (
                      <label
                        key={course.id}
                        className={cn(
                          'flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors',
                          isSelected ? 'bg-blue-50' : 'hover:bg-slate-50'
                        )}
                      >
                        <input type="checkbox" checked={isSelected} onChange={() => toggle(course.id)} className="w-4 h-4" />
                        <span className="text-sm font-medium text-slate-800">{course.discipline.name}</span>
                        <span className="text-xs text-slate-400">Семестр {course.semester}</span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3 shrink-0">
          <button
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
          >
            Отмена
          </button>
          <button
            onClick={handleClone}
            disabled={selectedIds.size === 0 || saving}
            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {saving ? <><Loader2 size={16} className="animate-spin" /> Копирование...</> : <><Copy size={16} /> Скопировать ({selectedIds.size})</>}
          </button>
        </div>
      </div>
    </div>
  );
};
