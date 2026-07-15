import React, { useCallback, useEffect, useState } from 'react';
import { BuildingDto, BuildingDeletionImpactDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { BuildingFormModal } from './BuildingFormModal';
import { Building2, MapPin, Home, Plus, Pencil, Trash2, Loader2 } from 'lucide-react';

interface BuildingListProps {
    /** Вызывается, когда удаление корпуса каскадом сносит его аудитории — чтобы хост перечитал их. */
    onAuditoriumsChanged?: () => void;
}

export const BuildingList: React.FC<BuildingListProps> = ({ onAuditoriumsChanged }) => {
    const [buildings, setBuildings] = useState<BuildingDto[]>([]);
    const [loading, setLoading] = useState(true);

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingBuilding, setEditingBuilding] = useState<BuildingDto | null>(null);

    const [deletingBuilding, setDeletingBuilding] = useState<BuildingDto | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);
    // Последствия удаления (с бэка): аудитории уйдут каскадом, занятия останутся без комнаты,
    // а если аудитории требует учебный план — удалить нельзя.
    const [impact, setImpact] = useState<BuildingDeletionImpactDto | null>(null);
    const [impactFailed, setImpactFailed] = useState(false);

    const reload = useCallback(() => {
        setLoading(true);
        ResourceService.getBuildings()
            .then(setBuildings)
            .catch((err) => console.error('Ошибка загрузки корпусов:', err))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        reload();
    }, [reload]);

    const handleCreate = () => {
        setEditingBuilding(null);
        setIsModalOpen(true);
    };

    const handleEdit = (b: BuildingDto) => {
        setEditingBuilding(b);
        setIsModalOpen(true);
    };

    const handleSaved = () => {
        setIsModalOpen(false);
        setEditingBuilding(null);
        reload();
    };

    const handleCloseModal = () => {
        setIsModalOpen(false);
        setEditingBuilding(null);
    };

    // Цена удаления спрашивается у бэка: аудитории корпуса уйдут каскадом, занятия в них останутся
    // без комнаты, а если аудиторию требует учебный план — удалить нельзя.
    const handleDeleteRequest = async (b: BuildingDto) => {
        setDeletingBuilding(b);
        setImpact(null);
        setImpactFailed(false);
        try {
            setImpact(await ResourceService.getBuildingDeleteImpact(b.id));
        } catch (e) {
            // Проверка не удалась — не запрещаем удаление (бэк всё равно откажет, если нельзя),
            // но и не притворяемся, что цена известна: диалог скажет об этом прямо.
            console.error('Не удалось получить последствия удаления корпуса:', e);
            setImpactFailed(true);
        }
    };

    const handleDeleteCancel = () => {
        setDeletingBuilding(null);
        setImpact(null);
        setImpactFailed(false);
    };

    const handleDeleteConfirm = async () => {
        if (!deletingBuilding) return;
        const removedAuditoriums = (impact?.auditoriumCount ?? deletingBuilding.auditoriums?.length ?? 0) > 0;
        setIsDeleting(true);
        try {
            await ResourceService.deleteBuilding(deletingBuilding.id);
            handleDeleteCancel();
            reload();
            // Удаление корпуса каскадом сносит его аудитории — освежаем и их список у хоста.
            if (removedAuditoriums) onAuditoriumsChanged?.();
        } catch (err: any) {
            console.error('Ошибка удаления корпуса:', err);
            alert(err?.response?.data || 'Не удалось удалить корпус.');
        } finally {
            setIsDeleting(false);
        }
    };

    // Можно ли удалять — решает бэк (`deletable`); фронт это не выводит, а показывает.
    const blocked = !!impact && !impact.deletable;

    const deleteMessage = (): string => {
        const name = deletingBuilding?.name ?? '';
        if (impactFailed) {
            return `Не удалось проверить, где используются аудитории корпуса «${name}». `
                + 'Если они заняты в расписании, эти занятия останутся без комнаты. Удалить?';
        }
        if (!impact) return `Проверяем последствия удаления «${name}»…`;
        if (blocked) {
            return `Аудитории корпуса «${name}» указаны требуемыми или приоритетными в `
                + `${impact.slotsRequiringIt} занятиях учебного плана, поэтому удалить его нельзя. `
                + 'Сначала уберите эти аудитории из плана.';
        }
        if (impact.auditoriumCount === 0) {
            return `Удалить «${name}»? В корпусе нет аудиторий.`;
        }
        const parts: string[] = [
            `${impact.auditoriumCount} ${pluralAud(impact.auditoriumCount)} корпуса будут удалены каскадом`,
        ];
        if (impact.placedLessons > 0) {
            parts.push(`${impact.placedLessons} занятий в них останутся БЕЗ комнаты`
                + (impact.lockedLessons > 0 ? `, из них ${impact.lockedLessons} закреплены вручную` : '')
                + ' — подобрать новую можно только генерацией или переносом');
        }
        if (impact.groupsUsingAsBase > 0) {
            parts.push(`у ${impact.groupsUsingAsBase} групп они указаны домашними — эта связь обнулится`);
        }
        return `Удалить «${name}»? ${parts.join('. ')}. Это действие нельзя отменить.`;
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
            {/* Панель действий */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-slate-500">
                    <Building2 size={16} />
                    <span className="text-sm font-medium">
                        Всего: <span className="font-black text-slate-900">{buildings.length}</span>
                    </span>
                </div>
                <button
                    onClick={handleCreate}
                    className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors shadow-md shadow-blue-600/20"
                >
                    <Plus size={16} />
                    Добавить корпус
                </button>
            </div>

            {/* Сетка */}
            {buildings.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    {buildings.map((b) => (
                        <div
                            key={b.id}
                            className="bg-white rounded-lg border border-slate-100 shadow-sm hover:shadow-md transition-all overflow-hidden group/card"
                        >
                            {/* Шапка */}
                            <div className="px-3 py-2.5 border-b border-slate-50">
                                <div className="flex items-center justify-between gap-2">
                                    <h3 className="text-xs font-black text-slate-900 truncate flex-1" title={b.name}>
                                        {b.name}
                                    </h3>
                                    <div className="flex items-center gap-0.5 opacity-0 group-hover/card:opacity-100 transition-opacity shrink-0">
                                        <button
                                            onClick={() => handleEdit(b)}
                                            className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                                            title="Редактировать"
                                        >
                                            <Pencil size={13} />
                                        </button>
                                        <button
                                            onClick={() => handleDeleteRequest(b)}
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
                                <div className="flex items-center gap-1.5">
                                    <MapPin size={12} className="text-slate-400 shrink-0" />
                                    <span className="text-[11px] text-slate-600 truncate" title={b.location?.name}>
                                        {b.location?.name ?? '—'}
                                    </span>
                                </div>
                                <div className="flex items-center gap-1.5">
                                    <Home size={12} className="text-slate-400 shrink-0" />
                                    <span className="text-[11px] text-slate-700 font-bold">
                                        {b.auditoriums?.length ?? 0} {pluralAud(b.auditoriums?.length ?? 0)}
                                    </span>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="py-12 text-center bg-white rounded-xl border-2 border-dashed border-slate-200">
                    <Building2 size={36} className="mx-auto text-slate-300 mb-3" />
                    <h3 className="text-sm font-bold text-slate-700 mb-1">Корпусов пока нет</h3>
                    <p className="text-xs text-slate-500 mb-4">Добавьте первый учебный корпус</p>
                    <button
                        onClick={handleCreate}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors"
                    >
                        <Plus size={16} />
                        Добавить корпус
                    </button>
                </div>
            )}

            {/* Модалка */}
            {isModalOpen && (
                <BuildingFormModal
                    building={editingBuilding}
                    onClose={handleCloseModal}
                    onSaved={handleSaved}
                />
            )}

            {/* Подтверждение удаления */}
            {deletingBuilding && (
                <ConfirmDialog
                    title={blocked ? 'Удаление невозможно' : 'Удалить корпус?'}
                    message={deleteMessage()}
                    // Если аудитории корпуса требует учебный план, кнопка не удаляет, а закрывает диалог.
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

/** Склонение слова «аудитория» по количеству. */
function pluralAud(n: number): string {
    const mod10 = n % 10;
    const mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) return 'аудитория';
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return 'аудитории';
    return 'аудиторий';
}
