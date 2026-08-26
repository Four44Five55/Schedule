import React, { useEffect, useMemo, useState } from 'react';
import { X, Plus, Trash2, Save, Loader2, BookMarked } from 'lucide-react';
import { EducatorDictionaryService, type EducatorDictionaryKind } from '../../../services/apiServices';
import type { DictionaryEntryDto, DictionaryKindDto } from '../../../types/api';
import { cn } from '../../../utils/cn';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

interface Props {
    onClose: () => void;
    /** Зовётся после любой правки: подписи преподавателей могли измениться. */
    onChanged: () => void;
}

/**
 * Справочники регалий: специальные звания, роды службы, отрасли науки.
 *
 * <p><b>Один экран на все виды.</b> Справочники отличаются только тем, куда шлются запросы, —
 * три копии этого компонента были бы ровно той находкой аудита («~10 форм-модалок с одинаковым
 * каркасом»), а будущая должность потребовала бы четвёртой. Вкладки приходят с бэка
 * (`GET /educator-dictionaries`), поэтому новый справочник появится здесь сам, без правки
 * фронта.</p>
 *
 * <p>Живёт рядом с разделом «Преподаватели»: перечень правят там же, где им пользуются.</p>
 */
export const EducatorDictionaryManager: React.FC<Props> = ({ onClose, onChanged }) => {
    const [kinds, setKinds] = useState<DictionaryKindDto[]>([]);
    const [activeKind, setActiveKind] = useState<EducatorDictionaryKind | null>(null);
    const [entries, setEntries] = useState<DictionaryEntryDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        EducatorDictionaryService.getKinds()
            .then((list) => {
                setKinds(list);
                if (list.length > 0) setActiveKind(list[0].slug as EducatorDictionaryKind);
            })
            .catch((e) => {
                console.error('Ошибка загрузки видов справочников:', e);
                setError('Не удалось загрузить список справочников');
                setLoading(false);
            });
    }, []);

    const reload = useMemo(() => async (kind: EducatorDictionaryKind) => {
        setLoading(true);
        setError(null);
        try {
            setEntries(await EducatorDictionaryService.getAll(kind));
        } catch (e) {
            console.error('Ошибка загрузки справочника:', e);
            setError('Не удалось загрузить справочник');
            setEntries([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (activeKind) void reload(activeKind);
    }, [activeKind, reload]);

    const afterMutation = async () => {
        if (activeKind) await reload(activeKind);
        onChanged();
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl overflow-hidden max-h-[90vh] flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-indigo-100 rounded-lg">
                            <BookMarked size={20} className="text-indigo-600" />
                        </div>
                        <div>
                            <h2 className="text-lg font-black text-slate-900">Справочники регалий</h2>
                            <p className="text-[11px] text-slate-500">
                                Сокращения — без точек: «п-к», «юст», «т». Подпись собирается тоже без них:
                                «т» + кандидат → «ктн». Так же пишут документы, из которых мы читаем данные.
                            </p>
                        </div>
                    </div>
                    <button type="button" onClick={onClose} className="p-1 hover:bg-slate-200 rounded-lg transition-colors">
                        <X size={20} className="text-slate-500" />
                    </button>
                </div>

                <div className="px-6 pt-3 border-b border-slate-100 flex gap-1 shrink-0">
                    {kinds.map((kind) => (
                        <button
                            key={kind.slug}
                            onClick={() => setActiveKind(kind.slug as EducatorDictionaryKind)}
                            className={cn(
                                'px-3 py-2 text-xs font-bold rounded-t-lg transition-colors',
                                activeKind === kind.slug
                                    ? 'bg-white text-blue-700 border border-b-white border-slate-200 -mb-px'
                                    : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50',
                            )}
                        >
                            {kind.label}
                        </button>
                    ))}
                </div>

                <div className="p-6 space-y-3 overflow-y-auto flex-1">
                    {error && (
                        <ErrorBanner message={error} />
                    )}

                    {loading ? (
                        <div className="py-10 text-center text-slate-400">
                            <Loader2 size={22} className="mx-auto animate-spin" />
                        </div>
                    ) : activeKind ? (
                        <>
                            <div className="grid grid-cols-[1fr_120px_70px_auto] gap-2 px-1 text-[10px] font-black uppercase tracking-wider text-slate-400">
                                <span>Наименование</span>
                                <span>Сокращение</span>
                                <span>Порядок</span>
                                <span />
                            </div>

                            {entries.map((entry) => (
                                <DictionaryRow
                                    key={entry.id}
                                    kind={activeKind}
                                    entry={entry}
                                    onChanged={afterMutation}
                                    onError={setError}
                                />
                            ))}

                            <NewDictionaryRow kind={activeKind} onCreated={afterMutation} onError={setError} />
                        </>
                    ) : null}
                </div>
            </div>
        </div>
    );
};

interface RowProps {
    kind: EducatorDictionaryKind;
    entry: DictionaryEntryDto;
    onChanged: () => void | Promise<void>;
    onError: (message: string | null) => void;
}

/**
 * Строка справочника с правкой на месте.
 *
 * <p>Удаление доступно, только если {@code deletable} — <b>это решение бэка</b>, а не вывод из
 * числа: ссылки стоят с {@code RESTRICT}, и занятую строку БД удалить не даст. Занятую предлагаем
 * погасить ({@code active = false}) — история при этом цела.</p>
 */
const DictionaryRow: React.FC<RowProps> = ({ kind, entry, onChanged, onError }) => {
    const [name, setName] = useState(entry.name);
    const [shortName, setShortName] = useState(entry.shortName);
    const [sortOrder, setSortOrder] = useState(String(entry.sortOrder ?? 0));
    const [active, setActive] = useState(entry.active);
    const [saving, setSaving] = useState(false);

    const dirty =
        name !== entry.name ||
        shortName !== entry.shortName ||
        String(entry.sortOrder ?? 0) !== sortOrder ||
        active !== entry.active;

    const save = async () => {
        onError(null);
        setSaving(true);
        try {
            await EducatorDictionaryService.update(kind, entry.id, {
                name: name.trim(),
                shortName: shortName.trim(),
                sortOrder: Number(sortOrder) || 0,
                active,
            });
            await onChanged();
        } catch (e: any) {
            // Занятое сокращение → 400 с текстом: разбор по нему обязан быть однозначным.
            onError(errorMessage(e, 'Не удалось сохранить значение'));
        } finally {
            setSaving(false);
        }
    };

    const remove = async () => {
        onError(null);
        setSaving(true);
        try {
            await EducatorDictionaryService.delete(kind, entry.id);
            await onChanged();
        } catch (e: any) {
            onError(errorMessage(e, 'Не удалось удалить значение'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="grid grid-cols-[1fr_120px_70px_auto] gap-2 items-center">
            <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={saving}
                className={cn(
                    'px-2.5 py-1.5 border rounded-lg text-xs font-medium outline-none focus:border-blue-500',
                    active ? 'border-slate-200' : 'border-slate-200 bg-slate-50 text-slate-400 line-through',
                )}
            />
            <input
                value={shortName}
                onChange={(e) => setShortName(e.target.value)}
                disabled={saving}
                placeholder="п-к"
                className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-bold outline-none focus:border-blue-500"
            />
            <input
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                disabled={saving}
                inputMode="numeric"
                className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-medium outline-none focus:border-blue-500"
                title="Порядок в списке — по старшинству, шаг 10"
            />
            <div className="flex items-center gap-1">
                <button
                    type="button"
                    onClick={() => setActive((v) => !v)}
                    disabled={saving}
                    className={cn(
                        'px-2 py-1 rounded-lg text-[10px] font-black transition-colors',
                        active ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                            : 'bg-slate-100 text-slate-500 hover:bg-slate-200',
                    )}
                    title={active ? 'Активно — предлагается в выборе' : 'Погашено — в выборе не предлагается, история цела'}
                >
                    {active ? 'активно' : 'погашено'}
                </button>

                <span
                    className="px-1.5 py-1 text-[10px] font-bold text-slate-400"
                    title="Сколько преподавателей ссылается на значение"
                >
                    {entry.educatorCount}
                </span>

                {dirty && (
                    <button
                        type="button"
                        onClick={save}
                        disabled={saving}
                        className="p-1 text-blue-600 hover:bg-blue-50 rounded transition-colors"
                        title="Сохранить"
                    >
                        {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                    </button>
                )}

                <button
                    type="button"
                    onClick={remove}
                    disabled={saving || !entry.deletable}
                    className={cn(
                        'p-1 rounded transition-colors',
                        entry.deletable ? 'text-slate-400 hover:text-red-600 hover:bg-red-50' : 'text-slate-200 cursor-not-allowed',
                    )}
                    title={entry.deletable
                        ? 'Удалить'
                        : `Значение указано у ${entry.educatorCount} преподавателей — его можно погасить, но не удалить`}
                >
                    <Trash2 size={14} />
                </button>
            </div>
        </div>
    );
};

interface NewRowProps {
    kind: EducatorDictionaryKind;
    onCreated: () => void | Promise<void>;
    onError: (message: string | null) => void;
}

/** Строка добавления: те же поля, что и у существующих — форма справочника одна. */
const NewDictionaryRow: React.FC<NewRowProps> = ({ kind, onCreated, onError }) => {
    const [name, setName] = useState('');
    const [shortName, setShortName] = useState('');
    const [sortOrder, setSortOrder] = useState('');
    const [saving, setSaving] = useState(false);

    const create = async () => {
        if (!name.trim() || !shortName.trim()) {
            onError('Наименование и сокращение обязательны');
            return;
        }
        onError(null);
        setSaving(true);
        try {
            await EducatorDictionaryService.create(kind, {
                name: name.trim(),
                shortName: shortName.trim(),
                sortOrder: Number(sortOrder) || 0,
                active: true,
            });
            setName('');
            setShortName('');
            setSortOrder('');
            await onCreated();
        } catch (e: any) {
            onError(errorMessage(e, 'Не удалось добавить значение'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="grid grid-cols-[1fr_120px_70px_auto] gap-2 items-center pt-2 border-t border-dashed border-slate-200">
            <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="полковник"
                disabled={saving}
                className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-medium outline-none focus:border-blue-500"
            />
            <input
                value={shortName}
                onChange={(e) => setShortName(e.target.value)}
                placeholder="п-к"
                disabled={saving}
                className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-bold outline-none focus:border-blue-500"
            />
            <input
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                placeholder="10"
                inputMode="numeric"
                disabled={saving}
                className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-medium outline-none focus:border-blue-500"
            />
            <button
                type="button"
                onClick={create}
                disabled={saving}
                className="flex items-center gap-1 px-2.5 py-1.5 bg-blue-600 text-white rounded-lg font-bold text-xs hover:bg-blue-700 transition-colors disabled:opacity-50"
            >
                {saving ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                Добавить
            </button>
        </div>
    );
};
