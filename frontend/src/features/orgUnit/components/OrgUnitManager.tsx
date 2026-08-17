import React, { useCallback, useMemo, useState } from 'react';
import {
    Network,
    Plus,
    Pencil,
    Trash2,
    Loader2,
    ChevronRight,
    ChevronDown,
    Users,
    GraduationCap,
    AlertCircle,
    CornerDownRight,
} from 'lucide-react';
import type { EducatorDto, GroupDto, OrgUnitDeletionImpactDto, OrgUnitDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { cn } from '../../../utils/cn';
import { useOrgUnits, type OrgUnitNode } from '../hooks/useOrgUnits';
import { OrgUnitFormModal } from './OrgUnitFormModal';

interface OrgUnitManagerProps {
    /** Преподаватели и группы нужны только для счётчиков — отдельных запросов не делаем. */
    educators: EducatorDto[];
    groups: GroupDto[];
    /** Дёргается после правок: хост может освежить зависимые данные. */
    onChanged?: () => void;
}

/**
 * Раздел «Оргструктура»: дерево подразделений с созданием, правкой и удалением.
 *
 * <p>Счётчики преподавателей и групп считаются <b>по прямой привязке</b>, без разворота
 * поддерева: суммирование по ветке — это уже разрешение охвата, и владеть им должен бэк
 * (будущий {@code OrgUnitScopeResolver}), иначе та же логика разъедется по двум сторонам.</p>
 */
export const OrgUnitManager: React.FC<OrgUnitManagerProps> = ({ educators, groups, onChanged }) => {
    const { tree, flat, loading, error, reload } = useOrgUnits();
    const { getOrgUnitTypeLabel } = useEnums();

    const [collapsed, setCollapsed] = useState<Set<number>>(new Set());
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editing, setEditing] = useState<OrgUnitDto | null>(null);
    const [defaultParentId, setDefaultParentId] = useState<number | null>(null);

    const [deleting, setDeleting] = useState<OrgUnitDto | null>(null);
    const [impact, setImpact] = useState<OrgUnitDeletionImpactDto | null>(null);
    const [impactFailed, setImpactFailed] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);

    // Прямые привязки: id подразделения → сколько преподавателей / групп.
    const educatorCounts = useMemo(() => countBy(educators.map((e) => e.orgUnitId)), [educators]);
    const groupCounts = useMemo(() => countBy(groups.map((g) => g.orgUnitId)), [groups]);

    const unassignedEducators = educators.filter((e) => e.orgUnitId == null).length;
    const unassignedGroups = groups.filter((g) => g.orgUnitId == null).length;

    const toggle = (id: number) =>
        setCollapsed((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });

    const handleCreate = (parentId: number | null) => {
        setEditing(null);
        setDefaultParentId(parentId);
        setIsModalOpen(true);
    };

    const handleEdit = (unit: OrgUnitDto) => {
        setEditing(unit);
        setDefaultParentId(null);
        setIsModalOpen(true);
    };

    const handleSaved = () => {
        setIsModalOpen(false);
        setEditing(null);
        void reload();
        onChanged?.();
    };

    // Цену удаления считает бэк: любая ссылка (дети/преподаватели/группы) удаление запрещает.
    const handleDeleteRequest = useCallback(async (unit: OrgUnitDto) => {
        setDeleting(unit);
        setImpact(null);
        setImpactFailed(false);
        try {
            setImpact(await ResourceService.getOrgUnitDeleteImpact(unit.id));
        } catch (e) {
            console.error('Не удалось получить последствия удаления подразделения:', e);
            setImpactFailed(true);
        }
    }, []);

    const handleDeleteCancel = () => {
        setDeleting(null);
        setImpact(null);
        setImpactFailed(false);
    };

    const handleDeleteConfirm = async () => {
        if (!deleting) return;
        setIsDeleting(true);
        try {
            await ResourceService.deleteOrgUnit(deleting.id);
            handleDeleteCancel();
            void reload();
            onChanged?.();
        } catch (err: any) {
            console.error('Ошибка удаления подразделения:', err);
            alert(err?.response?.data || 'Не удалось удалить подразделение.');
        } finally {
            setIsDeleting(false);
        }
    };

    const blocked = !!impact && !impact.deletable;

    const deleteMessage = (): string => {
        const name = deleting?.name ?? '';
        if (impactFailed) {
            return `Не удалось проверить связи «${name}». Попробовать удалить?`;
        }
        if (!impact) return `Проверяем связи «${name}»…`;
        if (blocked) {
            return (
                `На «${name}» ссылаются: вложенных подразделений — ${impact.childUnits}, ` +
                `преподавателей — ${impact.educators}, групп — ${impact.groups}, ` +
                `аудиторий — ${impact.auditoriums}. ` +
                'Поэтому удалить нельзя: сначала перепривяжите их. ' +
                'Если подразделение просто расформировано — снимите флаг «действующее», ' +
                'тогда исторические связи сохранятся.'
            );
        }
        return `Удалить «${name}»? На него ничто не ссылается.`;
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
            {/* Шапка */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                    <div className="p-2 bg-sky-100 rounded-lg">
                        <Network size={18} className="text-sky-600" />
                    </div>
                    <div>
                        <h2 className="font-black text-slate-900 leading-none">Оргструктура</h2>
                        <p className="text-xs text-slate-400 font-medium mt-1">
                            Факультеты, кафедры и отделы — {flat.length} шт.
                        </p>
                    </div>
                </div>
                <button
                    onClick={() => handleCreate(null)}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors"
                >
                    <Plus size={16} /> Подразделение
                </button>
            </div>

            {error && (
                <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700">
                    <AlertCircle size={18} className="shrink-0 mt-0.5" />
                    <span>{error}</span>
                </div>
            )}

            {/* Нераспределённые: не дефект данных, но видеть их надо */}
            {(unassignedEducators > 0 || unassignedGroups > 0) && (
                <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-xl text-sm text-amber-800">
                    <AlertCircle size={18} className="shrink-0 mt-0.5" />
                    <span>
                        Без подразделения: преподавателей — <b>{unassignedEducators}</b>, групп —{' '}
                        <b>{unassignedGroups}</b>. Привязка задаётся в карточке преподавателя или группы.
                    </span>
                </div>
            )}

            {/* Дерево */}
            {tree.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 gap-3 border-2 border-dashed border-slate-200 rounded-2xl bg-white">
                    <Network size={28} className="text-slate-300" />
                    <p className="font-bold text-slate-600">Подразделений пока нет</p>
                    <p className="text-sm text-slate-400">
                        Начните с факультета или кафедры — вложенность можно задать позже.
                    </p>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-2xl divide-y divide-slate-100 overflow-hidden">
                    {tree.map((node) => (
                        <OrgUnitRows
                            key={node.id}
                            node={node}
                            collapsed={collapsed}
                            onToggle={toggle}
                            onEdit={handleEdit}
                            onDelete={handleDeleteRequest}
                            onAddChild={handleCreate}
                            typeLabel={getOrgUnitTypeLabel}
                            educatorCounts={educatorCounts}
                            groupCounts={groupCounts}
                        />
                    ))}
                </div>
            )}

            {isModalOpen && (
                <OrgUnitFormModal
                    orgUnit={editing}
                    allUnits={flat}
                    defaultParentId={defaultParentId}
                    onClose={() => {
                        setIsModalOpen(false);
                        setEditing(null);
                    }}
                    onSaved={handleSaved}
                />
            )}

            {deleting && (
                <ConfirmDialog
                    title={blocked ? 'Удаление невозможно' : 'Удалить подразделение?'}
                    message={deleteMessage()}
                    confirmLabel={blocked ? 'Понятно' : 'Удалить'}
                    variant={blocked ? 'info' : 'danger'}
                    isLoading={isDeleting}
                    onConfirm={blocked ? handleDeleteCancel : handleDeleteConfirm}
                    onCancel={handleDeleteCancel}
                />
            )}
        </div>
    );
};

interface OrgUnitRowsProps {
    node: OrgUnitNode;
    collapsed: Set<number>;
    onToggle: (id: number) => void;
    onEdit: (unit: OrgUnitDto) => void;
    onDelete: (unit: OrgUnitDto) => void;
    onAddChild: (parentId: number) => void;
    typeLabel: (value: string) => string;
    educatorCounts: Map<number, number>;
    groupCounts: Map<number, number>;
}

/**
 * Узел дерева и, рекурсивно, его дети. Отступ задаётся глубиной узла.
 */
const OrgUnitRows: React.FC<OrgUnitRowsProps> = ({
                                                     node,
                                                     collapsed,
                                                     onToggle,
                                                     onEdit,
                                                     onDelete,
                                                     onAddChild,
                                                     typeLabel,
                                                     educatorCounts,
                                                     groupCounts,
                                                 }) => {
    const hasChildren = node.children.length > 0;
    const isCollapsed = collapsed.has(node.id);
    const educators = educatorCounts.get(node.id) ?? 0;
    const groups = groupCounts.get(node.id) ?? 0;

    return (
        <>
            <div
                className={cn(
                    'flex items-center gap-2 px-4 py-2.5 hover:bg-slate-50 transition-colors group',
                    !node.active && 'opacity-50'
                )}
                style={{ paddingLeft: `${16 + node.depth * 22}px` }}
            >
                {hasChildren ? (
                    <button
                        onClick={() => onToggle(node.id)}
                        className="p-0.5 rounded hover:bg-slate-200 text-slate-400 shrink-0"
                        aria-label={isCollapsed ? 'Развернуть' : 'Свернуть'}
                    >
                        {isCollapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
                    </button>
                ) : (
                    <span className="w-[22px] shrink-0 text-slate-200 flex justify-center">
                        {node.depth > 0 && <CornerDownRight size={12} />}
                    </span>
                )}

                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-slate-800 truncate">{node.name}</span>
                        {node.shortName && (
                            <span className="text-[11px] font-bold text-slate-400 shrink-0">
                                {node.shortName}
                            </span>
                        )}
                        {!node.active && (
                            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 border border-slate-200 rounded px-1.5 py-0.5 shrink-0">
                                расформировано
                            </span>
                        )}
                    </div>
                    <span className="text-[11px] font-medium text-slate-400">{typeLabel(node.type)}</span>
                </div>

                {/* Счётчики — прямая привязка, без разворота поддерева */}
                <div className="flex items-center gap-3 text-[11px] font-bold text-slate-400 shrink-0">
                    {educators > 0 && (
                        <span className="flex items-center gap-1" title="Преподавателей (прямая привязка)">
                            <Users size={12} /> {educators}
                        </span>
                    )}
                    {groups > 0 && (
                        <span className="flex items-center gap-1" title="Групп (прямая привязка)">
                            <GraduationCap size={12} /> {groups}
                        </span>
                    )}
                </div>

                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
                    <button
                        onClick={() => onAddChild(node.id)}
                        title="Добавить внутрь"
                        className="p-1.5 rounded-lg text-slate-400 hover:bg-blue-50 hover:text-blue-600 transition-colors"
                    >
                        <Plus size={14} />
                    </button>
                    <button
                        onClick={() => onEdit(node)}
                        title="Редактировать"
                        className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition-colors"
                    >
                        <Pencil size={14} />
                    </button>
                    <button
                        onClick={() => onDelete(node)}
                        title="Удалить"
                        className="p-1.5 rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors"
                    >
                        <Trash2 size={14} />
                    </button>
                </div>
            </div>

            {!isCollapsed &&
                node.children.map((child) => (
                    <OrgUnitRows
                        key={child.id}
                        node={child}
                        collapsed={collapsed}
                        onToggle={onToggle}
                        onEdit={onEdit}
                        onDelete={onDelete}
                        onAddChild={onAddChild}
                        typeLabel={typeLabel}
                        educatorCounts={educatorCounts}
                        groupCounts={groupCounts}
                    />
                ))}
        </>
    );
};

/** Считает, сколько раз встретился каждый id (null/undefined пропускаются). */
function countBy(ids: (number | null | undefined)[]): Map<number, number> {
    const counts = new Map<number, number>();
    for (const id of ids) {
        if (id != null) counts.set(id, (counts.get(id) ?? 0) + 1);
    }
    return counts;
}
