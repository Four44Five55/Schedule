import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, AlertCircle, Home, Building2, Tag, Sparkles } from 'lucide-react';
import { AuditoriumDto, AuditoriumCreateDto, AuditoriumUpdateDto, BuildingDto, FeatureDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { cn } from '../../../utils/cn';

interface AuditoriumFormModalProps {
    auditorium: AuditoriumDto | null;
    onClose: () => void;
    onSaved: (auditorium: AuditoriumDto) => void;
}

interface PurposeOption {
    id: number;
    name: string;
}

export const AuditoriumFormModal: React.FC<AuditoriumFormModalProps> = ({
                                                                            auditorium,
                                                                            onClose,
                                                                            onSaved,
                                                                        }) => {
    const isEditMode = auditorium !== null;

    // Поля формы
    const [name, setName] = useState(auditorium?.name ?? '');
    const [capacity, setCapacity] = useState<number>(auditorium?.capacity ?? 1);
    const [buildingId, setBuildingId] = useState<number | null>(auditorium?.building?.id ?? null);
    const [purposeId, setPurposeId] = useState<number | null>(auditorium?.purpose?.id ?? null);
    const [featureIds, setFeatureIds] = useState<Set<number>>(
        new Set(auditorium?.features?.map((f) => f.id) ?? [])
    );

    // Справочники
    const [buildings, setBuildings] = useState<BuildingDto[]>([]);
    const [purposes, setPurposes] = useState<PurposeOption[]>([]);
    const [features, setFeatures] = useState<FeatureDto[]>([]);
    const [loadingRefs, setLoadingRefs] = useState(true);

    // Состояние отправки
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [errors, setErrors] = useState<{
        name?: string;
        capacity?: string;
        buildingId?: string;
    }>({});

    // Загрузка справочников
    useEffect(() => {
        setLoadingRefs(true);
        Promise.all([
            ResourceService.getBuildings(),
            ResourceService.getAuditoriumPurposes(),
            ResourceService.getFeatures(),
        ])
            .then(([b, p, f]) => {
                setBuildings(b);
                setPurposes(p);
                setFeatures(f);
            })
            .catch((err) => console.error('Ошибка загрузки справочников:', err))
            .finally(() => setLoadingRefs(false));
    }, []);

    const toggleFeature = (id: number) => {
        setFeatureIds((prev) => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const validate = (): boolean => {
        const newErrors: typeof errors = {};
        if (!name.trim()) {
            newErrors.name = 'Название аудитории обязательно';
        } else if (name.length > 255) {
            newErrors.name = 'Название не должно превышать 255 символов';
        }
        if (capacity < 1) {
            newErrors.capacity = 'Вместимость должна быть не меньше 1';
        }
        if (!buildingId) {
            newErrors.buildingId = 'Выберите корпус';
        }
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validate()) return;

        setSaving(true);
        setError(null);

        try {
            const payload: AuditoriumCreateDto | AuditoriumUpdateDto = {
                name: name.trim(),
                capacity,
                buildingId: buildingId!,
                purposeId: purposeId ?? null,
                featureIds: Array.from(featureIds),
            };

            console.log('📤 Отправка аудитории:', JSON.stringify(payload, null, 2));

            let saved: AuditoriumDto;
            if (isEditMode && auditorium) {
                saved = await ResourceService.updateAuditorium(auditorium.id, payload);
            } else {
                saved = await ResourceService.createAuditorium(payload);
            }
            onSaved(saved);
        } catch (err: any) {
            console.error('Ошибка сохранения аудитории:', err);
            if (err.response?.status === 400) {
                const serverError = err.response.data;
                setError(
                    typeof serverError === 'string' ? serverError : serverError.message || 'Ошибка валидации'
                );
            } else if (err.response?.status === 409) {
                setError('Аудитория с таким названием уже существует');
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
                            <div className="p-2 bg-rose-100 rounded-lg">
                                <Home size={20} className="text-rose-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать аудиторию' : 'Новая аудитория'}
                            </h2>
                        </div>
                        <button
                            type="button"
                            onClick={onClose}
                            className="p-1 hover:bg-slate-200 rounded-lg transition-colors"
                            disabled={saving}
                        >
                            <X size={20} className="text-slate-500" />
                        </button>
                    </div>
                </div>

                {/* Форма */}
                <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
                    <div className="p-6 space-y-5 overflow-y-auto flex-1">
                        {error && (
                            <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                                <AlertCircle size={18} className="shrink-0 mt-0.5" />
                                <span>{error}</span>
                            </div>
                        )}

                        {/* Название */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                                Название *
                            </label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => {
                                    setName(e.target.value);
                                    if (errors.name) setErrors((p) => ({ ...p, name: undefined }));
                                }}
                                placeholder="Например: Ауд. 301"
                                className={cn(
                                    'w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none',
                                    errors.name
                                        ? 'border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
                                )}
                                disabled={saving || loadingRefs}
                                autoFocus
                            />
                            {errors.name && <p className="text-xs text-red-600 font-medium">{errors.name}</p>}
                        </div>

                        {/* Вместимость */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                                Вместимость (мест) *
                            </label>
                            <input
                                type="number"
                                value={capacity}
                                onChange={(e) => {
                                    setCapacity(parseInt(e.target.value) || 0);
                                    if (errors.capacity) setErrors((p) => ({ ...p, capacity: undefined }));
                                }}
                                min={1}
                                className={cn(
                                    'w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none',
                                    errors.capacity
                                        ? 'border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
                                )}
                                disabled={saving || loadingRefs}
                            />
                            {errors.capacity && (
                                <p className="text-xs text-red-600 font-medium">{errors.capacity}</p>
                            )}
                        </div>

                        {/* Корпус */}
                        <div className="space-y-1.5">
                            <label className="flex items-center gap-1.5 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Building2 size={12} />
                                Корпус *
                            </label>
                            <select
                                value={buildingId ?? ''}
                                onChange={(e) => {
                                    setBuildingId(e.target.value ? parseInt(e.target.value) : null);
                                    if (errors.buildingId) setErrors((p) => ({ ...p, buildingId: undefined }));
                                }}
                                disabled={saving || loadingRefs}
                                className={cn(
                                    'w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none appearance-none cursor-pointer bg-white',
                                    errors.buildingId
                                        ? 'border-red-300 bg-red-50 focus:border-red-500'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
                                )}
                            >
                                <option value="">— Выберите корпус —</option>
                                {buildings.map((b) => (
                                    <option key={b.id} value={b.id}>
                                        {b.name}{b.location?.name ? ` (${b.location.name})` : ''}
                                    </option>
                                ))}
                            </select>
                            {errors.buildingId && (
                                <p className="text-xs text-red-600 font-medium">{errors.buildingId}</p>
                            )}
                        </div>

                        {/* Назначение */}
                        <div className="space-y-1.5">
                            <label className="flex items-center gap-1.5 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Tag size={12} />
                                Назначение
                            </label>
                            <select
                                value={purposeId ?? ''}
                                onChange={(e) =>
                                    setPurposeId(e.target.value ? parseInt(e.target.value) : null)
                                }
                                disabled={saving || loadingRefs}
                                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 appearance-none cursor-pointer bg-white"
                            >
                                <option value="">— Не указано —</option>
                                {purposes.map((p) => (
                                    <option key={p.id} value={p.id}>
                                        {p.name}
                                    </option>
                                ))}
                            </select>
                            <p className="text-[11px] text-slate-400">
                                Тип аудитории (Лекционная, Компьютерная и т.д.)
                            </p>
                        </div>

                        {/* Особенности */}
                        <div className="space-y-2">
                            <div className="flex items-center gap-2 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Sparkles size={14} />
                                Особенности
                            </div>
                            {features.length > 0 ? (
                                <div className="flex flex-wrap gap-2">
                                    {features.map((f) => (
                                        <button
                                            key={f.id}
                                            type="button"
                                            onClick={() => toggleFeature(f.id)}
                                            disabled={saving}
                                            title={f.name}
                                            className={cn(
                                                'px-3 py-1.5 rounded-lg text-xs font-bold transition-all border',
                                                featureIds.has(f.id)
                                                    ? 'bg-violet-600 text-white border-violet-600'
                                                    : 'bg-white text-slate-600 border-slate-200 hover:border-violet-300 hover:bg-violet-50'
                                            )}
                                        >
                                            {f.name}
                                        </button>
                                    ))}
                                </div>
                            ) : (
                                <p className="text-[11px] text-slate-400 italic">Особенности не настроены</p>
                            )}
                            <p className="text-[11px] text-slate-400">
                                Проектор, интерактивная доска, компьютеры и т.д.
                            </p>
                        </div>
                    </div>

                    {/* Кнопки */}
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
                            disabled={saving || loadingRefs}
                            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {saving ? (
                                <>
                                    <Loader2 size={16} className="animate-spin" /> Сохранение...
                                </>
                            ) : (
                                <>
                                    <Save size={16} /> {isEditMode ? 'Сохранить' : 'Создать'}
                                </>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};
