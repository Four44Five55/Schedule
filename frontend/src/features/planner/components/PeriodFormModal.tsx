import React, { useState } from 'react';
import { StudyPeriodDto, StudyPeriodCreateDto, PeriodType } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { CalendarPlus, X, Loader2, Check } from 'lucide-react';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

const PERIOD_TYPE_OPTIONS: { value: PeriodType; label: string }[] = [
  { value: 'FALL_SEMESTER', label: 'Осенний семестр' },
  { value: 'SPRING_SEMESTER', label: 'Весенний семестр' },
  { value: 'FALL_EXAM_SESSION', label: 'Осенняя сессия' },
  { value: 'SPRING_EXAM_SESSION', label: 'Весенняя сессия' },
];

export const PeriodFormModal: React.FC<{
  onClose: () => void;
  onCreated: (period: StudyPeriodDto) => void;
}> = ({ onClose, onCreated }) => {
  const [name, setName] = useState('');
  const [studyYear, setStudyYear] = useState(new Date().getFullYear());
  const [periodType, setPeriodType] = useState<PeriodType>('FALL_SEMESTER');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSave = name.trim() && startDate && endDate && !saving;

  const handleSave = async () => {
    if (!canSave) return;
    if (startDate > endDate) {
      setError('Дата начала не может быть позже даты окончания.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload: StudyPeriodCreateDto = {
        name: name.trim(), studyYear, periodType, startDate, endDate,
      };
      const created = await ResourceService.createStudyPeriod(payload);
      onCreated(created);
    } catch (err: any) {
      setError(errorMessage(err, 'Не удалось создать период.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden" onClick={e => e.stopPropagation()}>
        <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg"><CalendarPlus size={20} className="text-blue-600" /></div>
            <h2 className="text-lg font-black text-slate-900">Новый учебный период</h2>
          </div>
          <button onClick={onClose} disabled={saving} className="p-1 hover:bg-slate-200 rounded-lg transition-colors">
            <X size={20} className="text-slate-500" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {error && (
            <ErrorBanner message={error} />
          )}

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Название *</label>
            <input
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Осенний семестр 2026/2027"
              className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Учебный год *</label>
              <input
                type="number"
                min={2020}
                value={studyYear}
                onChange={e => setStudyYear(Number(e.target.value))}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Тип периода *</label>
              <select
                value={periodType}
                onChange={e => setPeriodType(e.target.value as PeriodType)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              >
                {PERIOD_TYPE_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Дата начала *</label>
              <input
                type="date"
                value={startDate}
                onChange={e => setStartDate(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Дата окончания *</label>
              <input
                type="date"
                value={endDate}
                onChange={e => setEndDate(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
          </div>
        </div>

        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3">
          <button
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
          >
            Отмена
          </button>
          <button
            onClick={handleSave}
            disabled={!canSave}
            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {saving ? <><Loader2 size={16} className="animate-spin" /> Сохранение...</> : <><Check size={16} /> Создать</>}
          </button>
        </div>
      </div>
    </div>
  );
};
