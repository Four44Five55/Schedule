import React, { useCallback, useEffect, useState } from 'react';
import { LocationDto, LocationDeletionImpactDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { LocationFormModal } from './LocationFormModal';
import { MapPin, Building2, Plus, Pencil, Trash2, Loader2 } from 'lucide-react';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { useToast } from '../../../context/ToastContext';

interface LocationListProps {
    /** Вызывается после изменения локаций — чтобы хост при желании освежил зависимое (корпуса). */
    onChanged?: () => void;
}

export const LocationList: React.FC<LocationListProps> = ({ onChanged }) => {
    const toast = useToast();
    const [locations, setLocations] = useState<LocationDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingLocation, setEditingLocation] = useState<LocationDto | null>(null);

    const [deletingLocation, setDeletingLocation] = useState<LocationDto | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);
    // Последствия удаления (с бэка): привязанные корпуса блокируют удаление (FK RESTRICT).
    const [impact, setImpact] = useState<LocationDeletionImpactDto | null>(null);
    const [impactFailed, setImpactFailed] = useState(false);

    const reload = useCallback(() => {
        setLoading(true);
        setLoadError(null);
        ResourceService.getLocations()
            .then(setLocations)
            // Пустой список и незагруженный выглядят одинаково, а делать надо разное:
            // завести первую строку либо повторить запрос.
            .catch((err) => setLoadError(errorMessage(err, 'Не удалось загрузить список локаций.')))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        reload();
    }, [reload]);

    const handleCreate = () => {
        setEditingLocation(null);
        setIsModalOpen(true);
    };

    const handleEdit = (l: LocationDto) => {
        setEditingLocation(l);
        setIsModalOpen(true);
    };

    const handleSaved = () => {
        setIsModalOpen(false);
        setEditingLocation(null);
        reload();
        onChanged?.();
    };

    const handleCloseModal = () => {
        setIsModalOpen(false);
        setEditingLocation(null);
    };

    // Цена удаления спрашивается у бэка: если к локации привязаны корпуса — удалить нельзя.
    const handleDeleteRequest = async (l: LocationDto) => {
        setDeletingLocation(l);
        setImpact(null);
        setImpactFailed(false);
        try {
            setImpact(await ResourceService.getLocationDeleteImpact(l.id));
        } catch (e) {
            console.error('Не удалось получить последствия удаления локации:', e);
            setImpactFailed(true);
        }
    };

    const handleDeleteCancel = () => {
        setDeletingLocation(null);
        setImpact(null);
        setImpactFailed(false);
    };

    const handleDeleteConfirm = async () => {
        if (!deletingLocation) return;
        setIsDeleting(true);
        try {
            await ResourceService.deleteLocation(deletingLocation.id);
            handleDeleteCancel();
            reload();
            onChanged?.();
        } catch (err: any) {
            console.error('Ошибка удаления локации:', err);
            toast.failure(err, 'Не удалось удалить локацию.');
        } finally {
            setIsDeleting(false);
        }
    };

    // Можно ли удалять — решает бэк (`deletable`); фронт это не выводит, а показывает.
    const blocked = !!impact && !impact.deletable;

    const deleteMessage = (): string => {
        const name = deletingLocation?.name ?? '';
        if (impactFailed) {
            return `Не удалось проверить, есть ли у «${name}» корпуса. Попробовать удалить?`;
        }
        if (!impact) return `Проверяем последствия удаления «${name}»…`;
        if (blocked) {
            return `К локации «${name}» привязано ${impact.buildingCount} ${pluralBuilding(impact.buildingCount)}, `
                + 'поэтому удалить её нельзя. Сначала перенесите или удалите эти корпуса.';
        }
        return `Удалить «${name}»? К локации не привязано ни одного корпуса.`;
    };

    if (loading) {
        return (
            <div className="py-12 flex items-center justify-center text-slate-400">
                <Loader2 size={22} className="animate-spin" />
            </div>
        );
    }

    return (
        <div className="space-y-4">
            {loadError && <ErrorBanner message={loadError} onRetry={reload} />}

            {/* Панель действий */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-slate-500">
                    <MapPin size={16} />
                    <span className="text-sm font-medium">
                        Всего: <span className="font-black text-slate-900">{locations.length}</span>
                    </span>
                </div>
                <button
                    onClick={handleCreate}
                    className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
                >
                    <Plus size={16} />
                    Добавить локацию
                </button>
            </div>

            {/* Сетка */}
            {locations.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    {locations.map((l) => (
                        <div
                            key={l.id}
                            className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card"
                        >
                            {/* Шапка */}
                            <div className="px-3 py-2.5 border-b border-slate-50">
                                <div className="flex items-center justify-between gap-2">
                                    <h3 className="text-xs font-black text-slate-900 truncate flex-1" title={l.name}>
                                        {l.name}
                                    </h3>
                                    <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity shrink-0">
                                        <button
                                            onClick={() => handleEdit(l)}
                                            className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                                            title="Редактировать"
                                        >
                                            <Pencil size={13} />
                                        </button>
                                        <button
                                            onClick={() => handleDeleteRequest(l)}
                                            className="p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                                            title="Удалить"
                                        >
                                            <Trash2 size={13} />
                                        </button>
                                    </div>
                                </div>
                            </div>

                            {/* Содержимое */}
                            <div className="px-3 py-2 space-y-2">
                                {l.address && (
                                    <p className="text-[11px] text-slate-500 line-clamp-2" title={l.address}>
                                        {l.address}
                                    </p>
                                )}
                                <div className="flex items-center gap-1.5">
                                    <Building2 size={12} className="text-slate-400 shrink-0" />
                                    <span className="text-[11px] text-slate-700 font-bold">
                                        {l.buildings?.length ?? 0} {pluralBuilding(l.buildings?.length ?? 0)}
                                    </span>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
                    <MapPin size={36} className="mx-auto text-slate-300 mb-3" />
                    <h3 className="text-sm font-bold text-slate-700 mb-1">Локаций пока нет</h3>
                    <p className="text-xs text-slate-500 mb-4">Добавьте первую локацию — к ней привязываются корпуса</p>
                    <button
                        onClick={handleCreate}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors"
                    >
                        <Plus size={16} />
                        Добавить локацию
                    </button>
                </div>
            )}

            {/* Модалка */}
            {isModalOpen && (
                <LocationFormModal
                    location={editingLocation}
                    onClose={handleCloseModal}
                    onSaved={handleSaved}
                />
            )}

            {/* Подтверждение удаления */}
            {deletingLocation && (
                <ConfirmDialog
                    title={blocked ? 'Удаление невозможно' : 'Удалить локацию?'}
                    message={deleteMessage()}
                    // Если к локации привязаны корпуса, кнопка не удаляет, а закрывает диалог.
                    confirmLabel={blocked ? 'Понятно' : 'Удалить'}
                    cancelLabel="Отмена"
                    variant={blocked ? 'warning' : 'danger'}
                    isLoading={isDeleting}
                    onConfirm={blocked ? handleDeleteCancel : handleDeleteConfirm}
                    onCancel={handleDeleteCancel}
                />
            )}
        </div>
    );
};

/** Склонение слова «корпус» по количеству. */
function pluralBuilding(n: number): string {
    const mod10 = n % 10;
    const mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) return 'корпус';
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return 'корпуса';
    return 'корпусов';
}
