import React, { useState } from 'react';
import { X, Save, Loader2, Network } from 'lucide-react';
import type { OrgUnitCreateDto, OrgUnitDto, OrgUnitType, OrgUnitUpdateDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';
import type { OrgUnitNode } from '../hooks/useOrgUnits';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

interface OrgUnitFormModalProps {
    /** Редактируемое подразделение либо null для создания. */
    orgUnit: OrgUnitDto | null;
    /** Плоский список дерева — для выбора родителя (с отступами по глубине). */
    allUnits: OrgUnitNode[];
    /** Предзаполнить родителя (создание «внутрь» выбранного узла). */
    defaultParentId?: number | null;
    onClose: () => void;
    onSaved: () => void;
}

/**
 * Создание и правка подразделения, включая перенос в другого родителя.
 *
 * <p>Допустимость родителя и вида проверяет <b>бэк</b> (ранг вида, кольцо, «сам себе родитель») —
 * форма не дублирует эти правила, а показывает ответ: 400 с текстом причины. Дублирование
 * означало бы два источника правды, расходящихся при первом же новом виде подразделения.</p>
 */
export const OrgUnitFormModal: React.FC<OrgUnitFormModalProps> = ({
                                                                      orgUnit,
                                                                      allUnits,
                                                                      defaultParentId = null,
                                                                      onClose,
                                                                      onSaved,
                                                                  }) => {
    const isEditMode = orgUnit !== null;
    const { orgUnitTypes, loading: enumsLoading } = useEnums();

    const [name, setName] = useState(orgUnit?.name ?? '');
    const [shortName, setShortName] = useState(orgUnit?.shortName ?? '');
    const [type, setType] = useState<OrgUnitType | ''>(orgUnit?.type ?? '');
    const [parentId, setParentId] = useState<number | null>(orgUnit?.parentId ?? defaultParentId);
    const [active, setActive] = useState<boolean>(orgUnit?.active ?? true);

    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [errors, setErrors] = useState<{ name?: string; type?: string }>({});

    // Себя и собственных потомков в родители не предлагаем: бэк такое отклонит, но показывать
    // заведомо неверный вариант — плохая подсказка.
    const forbiddenIds = new Set<number>();
    if (isEditMode && orgUnit) {
        const collect = (id: number) => {
            forbiddenIds.add(id);
            allUnits.filter((u) => u.parentId === id).forEach((child) => collect(child.id));
        };
        collect(orgUnit.id);
    }

    const validate = (): boolean => {
        const next: typeof errors = {};
        if (!name.trim()) {
            next.name = 'Название обязательно';
        } else if (name.length > 255) {
            next.name = 'Название не должно превышать 255 символов';
        }
        if (!type) {
            next.type = 'Выберите вид подразделения';
        }
        setErrors(next);
        return Object.keys(next).length === 0;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validate()) return;

        setSaving(true);
        setError(null);
        try {
            if (isEditMode && orgUnit) {
                const payload: OrgUnitUpdateDto = {
                    name: name.trim(),
                    shortName: shortName.trim() || null,
                    type: type as OrgUnitType,
                    parentId,
                    active,
                };
                await ResourceService.updateOrgUnit(orgUnit.id, payload);
            } else {
                const payload: OrgUnitCreateDto = {
                    name: name.trim(),
                    shortName: shortName.trim() || null,
                    type: type as OrgUnitType,
                    parentId,
                };
                await ResourceService.createOrgUnit(payload);
            }
            onSaved();
        } catch (err: any) {
            console.error('Ошибка сохранения подразделения:', err);
            // Причина отказа приходит с бэка текстом — правило вложенности живёт там.
            setError(errorMessage(err, 'Не удалось сохранить. Попробуйте ещё раз.'));
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
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-sky-100 rounded-lg">
                                <Network size={20} className="text-sky-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать подразделение' : 'Новое подразделение'}
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

                <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
                    <div className="p-6 space-y-5 overflow-y-auto flex-1">
                        {error && (
                            <ErrorBanner message={error} />
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
                                placeholder="Например: Кафедра высшей математики"
                                className={cn(
                                    'w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none',
                                    errors.name
                                        ? 'border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
                                )}
                                disabled={saving}
                                autoFocus
                            />
                            {errors.name && <p className="text-xs text-red-600 font-medium">{errors.name}</p>}
                        </div>

                        {/* Сокращение */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                                Сокращение
                            </label>
                            <input
                                type="text"
                                value={shortName ?? ''}
                                onChange={(e) => setShortName(e.target.value)}
                                placeholder="Например: ВМ"
                                maxLength={50}
                                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                disabled={saving}
                            />
                        </div>

                        {/* Вид */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                                Вид *
                            </label>
                            <select
                                value={type}
                                onChange={(e) => {
                                    setType(e.target.value as OrgUnitType);
                                    if (errors.type) setErrors((p) => ({ ...p, type: undefined }));
                                }}
                                disabled={saving || enumsLoading}
                                className={cn(
                                    'w-full px-4 py-2.5 border rounded-xl text-sm font-medium outline-none appearance-none cursor-pointer bg-white',
                                    errors.type
                                        ? 'border-red-300 bg-red-50 focus:border-red-500'
                                        : 'border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'
                                )}
                            >
                                <option value="">— Выберите вид —</option>
                                {orgUnitTypes.map((t) => (
                                    <option key={t.value} value={t.value}>
                                        {t.label}
                                    </option>
                                ))}
                            </select>
                            {errors.type && <p className="text-xs text-red-600 font-medium">{errors.type}</p>}
                        </div>

                        {/* Родитель */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                                Входит в состав
                            </label>
                            <select
                                value={parentId ?? ''}
                                onChange={(e) => setParentId(e.target.value ? parseInt(e.target.value) : null)}
                                disabled={saving}
                                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium outline-none appearance-none cursor-pointer bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                            >
                                <option value="">— Верхний уровень —</option>
                                {allUnits
                                    .filter((u) => !forbiddenIds.has(u.id))
                                    .map((u) => (
                                        <option key={u.id} value={u.id}>
                                            {' '.repeat(u.depth * 4)}
                                            {u.name}
                                        </option>
                                    ))}
                            </select>
                            <p className="text-[11px] text-slate-400 font-medium">
                                Верхний уровень — нормальное место: кафедра может не входить в факультет.
                            </p>
                        </div>

                        {/* Действующее */}
                        {isEditMode && (
                            <label className="flex items-center gap-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={active}
                                    onChange={(e) => setActive(e.target.checked)}
                                    disabled={saving}
                                    className="w-4 h-4 rounded border-slate-300"
                                />
                                <span className="text-sm font-medium text-slate-700">
                                    Действующее
                                    <span className="block text-[11px] text-slate-400 font-normal">
                                        Расформированное скрывается из выбора, но остаётся в базе — на него
                                        ссылаются исторические данные.
                                    </span>
                                </span>
                            </label>
                        )}
                    </div>

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
                            disabled={saving || enumsLoading}
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
