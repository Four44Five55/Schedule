import React, { useState } from 'react';
import { X, Save, Loader2, User, AlertCircle, Calendar, Clock, Zap, Network } from 'lucide-react';
import { EducatorDto, EducatorCreateDto, EducatorUpdateDto, DayOfWeek, TimeSlotPair } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { useOrgUnits } from '../../orgUnit/hooks/useOrgUnits';
import { cn } from '../../../utils/cn';

interface EducatorFormModalProps {
    educator: EducatorDto | null;
    onClose: () => void;
    onSaved: (educator: EducatorDto) => void;
}

export const EducatorFormModal: React.FC<EducatorFormModalProps> = ({
                                                                        educator,
                                                                        onClose,
                                                                        onSaved
                                                                    }) => {
    const isEditMode = educator !== null;
    const { daysOfWeek, timeSlots } = useEnums();
    const { flat: orgUnits, loading: orgUnitsLoading } = useOrgUnits();

    const [name, setName] = useState(educator?.name ?? '');
    const [preferredDays, setPreferredDays] = useState<Set<string>>(
        new Set(educator?.preferredDays ?? [])
    );
    const [preferredTimeSlots, setPreferredTimeSlots] = useState<Set<string>>(
        new Set(educator?.preferredTimeSlots ?? [])
    );
    const [compactSchedule, setCompactSchedule] = useState<boolean>(
        educator?.compactSchedule === true
    );
    // Подразделение: одно на преподавателя (совместительство не моделируем).
    const [orgUnitId, setOrgUnitId] = useState<number | null>(educator?.orgUnitId ?? null);

    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [nameError, setNameError] = useState<string | null>(null);

    const toggleDay = (day: string) => {
        setPreferredDays(prev => {
            const next = new Set(prev);
            next.has(day) ? next.delete(day) : next.add(day);
            return next;
        });
    };

    const toggleSlot = (slot: string) => {
        setPreferredTimeSlots(prev => {
            const next = new Set(prev);
            next.has(slot) ? next.delete(slot) : next.add(slot);
            return next;
        });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // Валидация
        if (!name.trim()) {
            setNameError('ФИО преподавателя обязательно');
            return;
        }
        if (name.length > 255) {
            setNameError('ФИО не должно превышать 255 символов');
            return;
        }
        setNameError(null);

        setSaving(true);
        setError(null);

        try {
            const payload: EducatorCreateDto | EducatorUpdateDto = {
                name: name.trim(),
                preferredDays: Array.from(preferredDays) as DayOfWeek[],
                preferredTimeSlots: Array.from(preferredTimeSlots) as TimeSlotPair[],
                compactSchedule: compactSchedule,
                orgUnitId: orgUnitId
            };

            console.log('📤 Отправка:', JSON.stringify(payload, null, 2));

            let saved: EducatorDto;
            if (isEditMode && educator) {
                saved = await ResourceService.updateEducator(educator.id, payload);
            } else {
                saved = await ResourceService.createEducator(payload);
            }
            onSaved(saved);
        } catch (err: any) {
            console.error('Ошибка сохранения преподавателя:', err);
            if (err.response?.status === 400) {
                const serverError = err.response.data;
                setError(typeof serverError === 'string' ? serverError : serverError.message || 'Ошибка валидации');
            } else if (err.response?.status === 409) {
                setError('Преподаватель с таким именем уже существует');
            } else {
                setError('Не удалось сохранить. Попробуйте ещё раз.');
            }
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden max-h-[90vh] flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Заголовок */}
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-indigo-100 rounded-lg">
                                <User size={20} className="text-indigo-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать преподавателя' : 'Новый преподаватель'}
                            </h2>
                        </div>
                        <button type="button" onClick={onClose} className="p-1 hover:bg-slate-200 rounded-lg transition-colors" disabled={saving}>
                            <X size={20} className="text-slate-500" />
                        </button>
                    </div>
                </div>

                {/* Единая форма с кнопками */}
                <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
                    <div className="p-6 space-y-5 overflow-y-auto flex-1">
                        {/* Ошибка */}
                        {error && (
                            <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                                <AlertCircle size={18} className="shrink-0 mt-0.5" />
                                <span>{error}</span>
                            </div>
                        )}

                        {/* ФИО */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">ФИО преподавателя *</label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => { setName(e.target.value); setNameError(null); }}
                                placeholder="Иванов Иван Иванович"
                                className={cn(
                                    "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                    nameError
                                        ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                        : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                )}
                                disabled={saving}
                                autoFocus
                            />
                            {nameError && <p className="text-xs text-red-600 font-medium">{nameError}</p>}
                        </div>

                        {/* Подразделение (кафедра или отдел) */}
                        <div className="space-y-1.5">
                            <label className="flex items-center gap-1.5 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Network size={12} />
                                Подразделение
                            </label>
                            <select
                                value={orgUnitId ?? ''}
                                onChange={(e) => setOrgUnitId(e.target.value ? parseInt(e.target.value) : null)}
                                disabled={saving || orgUnitsLoading}
                                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium outline-none appearance-none cursor-pointer bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                            >
                                <option value="">— Не распределён —</option>
                                {orgUnits
                                    // Расформированные не предлагаем, но уже выбранное показываем,
                                    // иначе правка карточки молча стёрла бы привязку.
                                    .filter((u) => u.active || u.id === orgUnitId)
                                    .map((u) => (
                                        <option key={u.id} value={u.id}>
                                            {' '.repeat(u.depth * 4)}
                                            {u.name}
                                        </option>
                                    ))}
                            </select>
                        </div>

                        {/* Предпочитаемые дни */}
                        <div className="space-y-2">
                            <div className="flex items-center gap-2 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Calendar size={14} />
                                Предпочитаемые дни
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {daysOfWeek.map((day) => (
                                    <button
                                        key={day.value}
                                        type="button"
                                        onClick={() => toggleDay(day.value)}
                                        disabled={saving}
                                        title={day.label}
                                        className={cn(
                                            "px-3 py-1.5 rounded-lg text-xs font-bold transition-all border",
                                            preferredDays.has(day.value)
                                                ? "bg-blue-600 text-white border-blue-600"
                                                : "bg-white text-slate-600 border-slate-200 hover:border-blue-300 hover:bg-blue-50"
                                        )}
                                    >
                                        {day.abbreviation}
                                    </button>
                                ))}
                            </div>
                            <p className="text-[11px] text-slate-400">Дни, в которые преподаватель предпочитает вести занятия</p>
                        </div>

                        {/* Предпочитаемые пары */}
                        <div className="space-y-2">
                            <div className="flex items-center gap-2 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Clock size={14} />
                                Предпочитаемые пары
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {timeSlots.map((slot) => (
                                    <button
                                        key={slot.value}
                                        type="button"
                                        onClick={() => toggleSlot(slot.value)}
                                        disabled={saving}
                                        title={`${slot.label}${slot.extra ? ` (${slot.extra})` : ''}`}
                                        className={cn(
                                            "px-3 py-1.5 rounded-lg text-xs font-bold transition-all border",
                                            preferredTimeSlots.has(slot.value)
                                                ? "bg-emerald-600 text-white border-emerald-600"
                                                : "bg-white text-slate-600 border-slate-200 hover:border-emerald-300 hover:bg-emerald-50"
                                        )}
                                    >
                                        {slot.abbreviation} пара
                                    </button>
                                ))}
                            </div>
                            <p className="text-[11px] text-slate-400">Временные слоты, предпочитаемые преподавателем</p>
                        </div>

                        {/* Компактное расписание — через checkbox */}
                        <div className="space-y-2">
                            <label className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl border border-slate-200 cursor-pointer hover:bg-slate-100 transition-colors select-none">
                                <input
                                    type="checkbox"
                                    checked={compactSchedule}
                                    onChange={(e) => setCompactSchedule(e.target.checked)}
                                    disabled={saving}
                                    className="sr-only"
                                />
                                <div className={cn(
                                    "w-10 h-6 rounded-full transition-colors relative shrink-0",
                                    compactSchedule ? "bg-emerald-500" : "bg-slate-300"
                                )}>
                                    <div className={cn(
                                        "absolute top-1 w-4 h-4 bg-white rounded-full shadow-sm transition-transform",
                                        compactSchedule ? "translate-x-5" : "translate-x-1"
                                    )} />
                                </div>
                                <div className="flex-1">
                                    <div className="flex items-center gap-2">
                                        <Zap size={14} className={compactSchedule ? "text-emerald-600" : "text-slate-400"} />
                                        <span className="text-sm font-bold text-slate-800">Компактное расписание</span>
                                    </div>
                                    <p className="text-[11px] text-slate-500 mt-0.5">Минимизировать окна между занятиями</p>
                                </div>
                            </label>
                        </div>
                    </div>

                    {/* Кнопки ВНУТРИ формы */}
                    <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3 shrink-0">
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={saving}
                            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
                        <button
                            type="submit"
                            disabled={saving}
                            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {saving ? (
                                <><Loader2 size={16} className="animate-spin" /> Сохранение...</>
                            ) : (
                                <><Save size={16} /> {isEditMode ? 'Сохранить' : 'Создать'}</>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};
