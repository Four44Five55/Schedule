import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, AlertCircle, Building2, MapPin } from 'lucide-react';
import { BuildingDto, BuildingCreateDto, BuildingUpdateDto, LocationDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { cn } from '../../../utils/cn';

interface BuildingFormModalProps {
    building: BuildingDto | null;
    onClose: () => void;
    onSaved: (building: BuildingDto) => void;
}

export const BuildingFormModal: React.FC<BuildingFormModalProps> = ({
                                                                        building,
                                                                        onClose,
                                                                        onSaved,
                                                                    }) => {
    const isEditMode = building !== null;

    const [name, setName] = useState(building?.name ?? '');
    const [locationId, setLocationId] = useState<number | null>(building?.location?.id ?? null);

    const [locations, setLocations] = useState<LocationDto[]>([]);
    const [loadingRefs, setLoadingRefs] = useState(true);

    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [errors, setErrors] = useState<{ name?: string; locationId?: string }>({});

    // Корпус обязан принадлежать локации (location_id NOT NULL) — грузим справочник локаций.
    useEffect(() => {
        setLoadingRefs(true);
        ResourceService.getLocations()
            .then(setLocations)
            .catch((err) => console.error('Ошибка загрузки локаций:', err))
            .finally(() => setLoadingRefs(false));
    }, []);

    const validate = (): boolean => {
        const newErrors: typeof errors = {};
        if (!name.trim()) {
            newErrors.name = 'Название корпуса обязательно';
        } else if (name.length > 255) {
            newErrors.name = 'Название не должно превышать 255 символов';
        }
        if (!locationId) {
            newErrors.locationId = 'Выберите локацию';
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
            const payload: BuildingCreateDto | BuildingUpdateDto = {
                name: name.trim(),
                locationId: locationId!,
            };

            let saved: BuildingDto;
            if (isEditMode && building) {
                saved = await ResourceService.updateBuilding(building.id, payload);
            } else {
                saved = await ResourceService.createBuilding(payload);
            }
            onSaved(saved);
        } catch (err: any) {
            console.error('Ошибка сохранения корпуса:', err);
            if (err.response?.status === 400) {
                const serverError = err.response.data;
                setError(
                    typeof serverError === 'string' ? serverError : serverError.message || 'Ошибка валидации'
                );
            } else if (err.response?.status === 409) {
                setError('Корпус с таким названием уже существует в этой локации');
            } else {
                setError('Не удалось сохранить. Попробуйте ещё раз.');
            }
        } finally {
            setSaving(false);
        }
    };

    const noLocations = !loadingRefs && locations.length === 0;

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
                                <Building2 size={20} className="text-indigo-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать корпус' : 'Новый корпус'}
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

                        {noLocations && (
                            <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-700">
                                <AlertCircle size={18} className="shrink-0 mt-0.5" />
                                <span>Сначала создайте хотя бы одну локацию — корпус привязывается к ней.</span>
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
                                placeholder="Например: Корпус №3"
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

                        {/* Локация */}
                        <div className="space-y-1.5">
                            <label className="flex items-center gap-1.5 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <MapPin size={12} />
                                Локация *
                            </label>
                            <select
                                value={locationId ?? ''}
                                onChange={(e) => {
                                    setLocationId(e.target.value ? parseInt(e.target.value) : null);
                                    if (errors.locationId) setErrors((p) => ({ ...p, locationId: undefined }));
                                }}
                                disabled={saving || loadingRefs || noLocations}
                                className={cn(
                                    'w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none appearance-none cursor-pointer bg-white',
                                    errors.locationId
                                        ? 'border-red-300 bg-red-50 focus:border-red-500'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
                                )}
                            >
                                <option value="">— Выберите локацию —</option>
                                {locations.map((l) => (
                                    <option key={l.id} value={l.id}>
                                        {l.name}{l.address ? ` (${l.address})` : ''}
                                    </option>
                                ))}
                            </select>
                            {errors.locationId && (
                                <p className="text-xs text-red-600 font-medium">{errors.locationId}</p>
                            )}
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
                            disabled={saving || loadingRefs || noLocations}
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
