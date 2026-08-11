import React, { useState } from 'react';
import { X, Save, Loader2, ShieldAlert, AlertCircle } from 'lucide-react';
import { ConstraintDto, KindOfConstraints, TimeSlotPair } from '../../../types/api';
import { ConstraintsService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { SLOTS } from '../../../components/grid/AcademicGridShell';
import { cn } from '../../../utils/cn';

type EntityType = 'group' | 'educator' | 'auditorium';

interface ConstraintFormModalProps {
  entityType: EntityType;
  entityId: number;
  /** Подпись сущности для заголовка (напр. «Иванов И.И.»). */
  entityLabel: string;
  /** Предзаполнение периода (yyyy-MM-dd). */
  defaultStartDate?: string;
  defaultEndDate?: string;
  /** Предвыбранная пара (например, при клике по ячейке сетки); undefined = весь день. */
  defaultTimeSlot?: TimeSlotPair;
  onClose: () => void;
  onSaved: (created: ConstraintDto) => void;
}

/**
 * Модалка создания ограничения для одной сущности.
 * Переиспользуется и в разделе «Ограничения», и в планировщике (перед генерацией).
 * Вид ограничения берётся из EnumContext (единый источник правды).
 */
export const ConstraintFormModal: React.FC<ConstraintFormModalProps> = ({
  entityType,
  entityId,
  entityLabel,
  defaultStartDate,
  defaultEndDate,
  defaultTimeSlot,
  onClose,
  onSaved,
}) => {
  const { constraintKinds } = useEnums();
  // Погашенные виды не предлагаем: их оставили ради уже проставленных ограничений.
  const selectableKinds = constraintKinds.filter((k) => k.active);

  const [kind, setKind] = useState<KindOfConstraints | ''>('');
  const [startDate, setStartDate] = useState(defaultStartDate ?? '');
  const [endDate, setEndDate] = useState(defaultEndDate ?? '');
  // '' = ограничение на весь день (все пары); иначе — только выбранная пара.
  const [timeSlot, setTimeSlot] = useState<TimeSlotPair | ''>(defaultTimeSlot ?? '');
  const [description, setDescription] = useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!kind) { setError('Выберите вид ограничения'); return; }
    if (!startDate || !endDate) { setError('Укажите даты начала и окончания'); return; }
    if (endDate < startDate) { setError('Дата окончания раньше даты начала'); return; }

    setSaving(true);
    setError(null);
    try {
      const base = { kindOfConstraint: kind, startDate, endDate, description: description.trim() || undefined, timeSlot: timeSlot || undefined };
      let saved: ConstraintDto;
      if (entityType === 'educator') {
        saved = await ConstraintsService.createEducatorConstraint({ educatorId: entityId, ...base });
      } else if (entityType === 'group') {
        saved = await ConstraintsService.createGroupConstraint({ groupId: entityId, ...base });
      } else {
        saved = await ConstraintsService.createAuditoriumConstraint({ auditoriumId: entityId, ...base });
      }
      onSaved(saved);
    } catch (err: any) {
      console.error('Ошибка сохранения ограничения:', err);
      const serverError = err?.response?.data;
      setError(
        typeof serverError === 'string' ? serverError
          : serverError?.message || 'Не удалось сохранить. Попробуйте ещё раз.'
      );
    } finally {
      setSaving(false);
    }
  };

  const inputCls = "w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20";

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-[120] p-4" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-rose-100 rounded-lg">
                <ShieldAlert size={20} className="text-rose-600" />
              </div>
              <div>
                <h2 className="text-lg font-black text-slate-900">Новое ограничение</h2>
                <p className="text-xs text-slate-500 font-medium">{entityLabel}</p>
              </div>
            </div>
            <button type="button" onClick={onClose} className="p-1 hover:bg-slate-200 rounded-lg transition-colors" disabled={saving}>
              <X size={20} className="text-slate-500" />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
          <div className="p-6 space-y-5 overflow-y-auto flex-1">
            {error && (
              <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                <AlertCircle size={18} className="shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Вид ограничения *</label>
              <select
                value={kind}
                onChange={(e) => { setKind(e.target.value as KindOfConstraints | ''); setError(null); }}
                className={cn(inputCls, "cursor-pointer")}
                disabled={saving}
                autoFocus
              >
                <option value="">Выберите вид...</option>
                {selectableKinds.map((k) => (
                  <option key={k.code} value={k.code}>{k.name} ({k.shortName})</option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Начало *</label>
                <input type="date" value={startDate} onChange={(e) => { setStartDate(e.target.value); setError(null); }} className={inputCls} disabled={saving} />
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Окончание *</label>
                <input type="date" value={endDate} onChange={(e) => { setEndDate(e.target.value); setError(null); }} className={inputCls} disabled={saving} />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Пара</label>
              <select
                value={timeSlot}
                onChange={(e) => setTimeSlot(e.target.value as TimeSlotPair | '')}
                className={cn(inputCls, "cursor-pointer")}
                disabled={saving}
              >
                <option value="">Весь день (все пары)</option>
                {SLOTS.map((s) => (
                  <option key={s.id} value={s.id}>{s.label} пара ({s.time})</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Описание</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Необязательно"
                rows={2}
                className={cn(inputCls, "resize-none")}
                disabled={saving}
              />
            </div>
          </div>

          <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3 shrink-0">
            <button type="button" onClick={onClose} disabled={saving} className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50">
              Отмена
            </button>
            <button type="submit" disabled={saving} className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2">
              {saving ? (<><Loader2 size={16} className="animate-spin" /> Сохранение...</>) : (<><Save size={16} /> Создать</>)}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
