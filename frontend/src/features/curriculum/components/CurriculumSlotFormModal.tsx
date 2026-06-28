import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, Layers, AlertCircle, Info, Plus, Edit2 } from 'lucide-react';
import { CurriculumSlotDto, KindOfStudy, ThemeLessonDto, AuditoriumDto, AuditoriumPoolDto } from '../../../types/api';
import { ResourceService, CurriculumService } from '../../../services/apiServices';
import { SlotFormValues } from '../planSource';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';

interface CurriculumSlotFormModalProps {
    slot: CurriculumSlotDto | null;
    /** Дисциплина (для списка тем). Модал не знает о курсе/шаблоне — запись идёт через onSave. */
    disciplineId: number;
    nextPosition: number; // Следующая доступная позиция (считает редактор)
    onClose: () => void;
    /** Стратегия сохранения: редактор связывает её с источником плана (курс/шаблон). */
    onSave: (values: SlotFormValues) => Promise<void>;
}

export const CurriculumSlotFormModal: React.FC<CurriculumSlotFormModalProps> = ({
    slot,
    disciplineId,
    nextPosition,
    onClose,
    onSave
}) => {
    const isEditMode = slot !== null;
    const { kindOfStudy: kindsOfStudyEnum } = useEnums();

    // Состояние формы
    const [position, setPosition] = useState(slot?.position ?? nextPosition);
    const [kindOfStudy, setKindOfStudy] = useState<KindOfStudy | ''>(slot?.kindOfStudy ?? '');
    const [themeLessonId, setThemeLessonId] = useState<number | null>(slot?.themeLesson?.id ?? null);
    const [requiredAuditoriumId, setRequiredAuditoriumId] = useState<number | null>(slot?.requiredAuditorium?.id ?? null);
    const [priorityAuditoriumId, setPriorityAuditoriumId] = useState<number | null>(slot?.priorityAuditorium?.id ?? null);
    const [allowedAuditoriumPoolId, setAllowedAuditoriumPoolId] = useState<number | null>(slot?.allowedAuditoriumPool?.id ?? null);

    // Списки для выбора
    const [themeLessons, setThemeLessons] = useState<ThemeLessonDto[]>([]);
    const [auditoriums, setAuditoriums] = useState<AuditoriumDto[]>([]);
    const [auditoriumPools, setAuditoriumPools] = useState<AuditoriumPoolDto[]>([]);
    const [loadingData, setLoadingData] = useState(true);

    // Inline-создание темы прямо из формы занятия (тема глобальна на дисциплину).
    const [showNewTheme, setShowNewTheme] = useState(false);
    const [newThemeNumber, setNewThemeNumber] = useState('');
    const [newThemeTitle, setNewThemeTitle] = useState('');
    const [creatingTheme, setCreatingTheme] = useState(false);
    const [themeError, setThemeError] = useState<string | null>(null);

    // Inline-правка названия/номера уже выбранной темы (тема глобальна — меняется везде).
    const [showEditTheme, setShowEditTheme] = useState(false);
    const [editThemeNumber, setEditThemeNumber] = useState('');
    const [editThemeTitle, setEditThemeTitle] = useState('');
    const [savingTheme, setSavingTheme] = useState(false);
    const [editThemeError, setEditThemeError] = useState<string | null>(null);

    const selectedTheme = themeLessons.find(t => t.id === themeLessonId) ?? null;

    // Состояние отправки
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Валидация
    const [positionError, setPositionError] = useState<string | null>(null);
    const [kindOfStudyError, setKindOfStudyError] = useState<string | null>(null);

    // Загрузка справочников. Темы — напрямую по дисциплине (без round-trip за курсом).
    useEffect(() => {
        setLoadingData(true);
        Promise.all([
            CurriculumService.getThemesByDiscipline(disciplineId).catch(() => []),
            ResourceService.getAuditoriums(),
            ResourceService.getAuditoriumPools()
        ])
            .then(([themes, auds, pools]) => {
                setThemeLessons(themes);
                setAuditoriums(auds);
                setAuditoriumPools(pools);
            })
            .catch(() => {
                setThemeLessons([]);
                setAuditoriums([]);
                setAuditoriumPools([]);
            })
            .finally(() => setLoadingData(false));
    }, [disciplineId]);

    // keepOpen=true — «Сохранить и добавить ещё»: окно не закрываем, готовим форму к
    // следующему занятию (позиция++, тема сбрасывается, вид/аудитории сохраняем —
    // обычно подряд добавляют однотипные занятия).
    const persist = async (keepOpen: boolean) => {
        // Валидация
        let hasError = false;

        if (position < 1) {
            setPositionError('Позиция должна быть 1 или больше');
            hasError = true;
        } else {
            setPositionError(null);
        }

        if (!kindOfStudy) {
            setKindOfStudyError('Выберите вид занятия');
            hasError = true;
        } else {
            setKindOfStudyError(null);
        }

        if (hasError) return;

        setSaving(true);
        setError(null);

        try {
            // Запись делегируется стратегии (источник плана): модал не знает курс/шаблон.
            await onSave({
                position,
                kindOfStudy: kindOfStudy as KindOfStudy,
                themeLessonId: themeLessonId || undefined,
                requiredAuditoriumId: requiredAuditoriumId || undefined,
                priorityAuditoriumId: priorityAuditoriumId || undefined,
                allowedAuditoriumPoolId: allowedAuditoriumPoolId || undefined,
            });
            if (keepOpen) {
                setPosition(p => p + 1);
                setThemeLessonId(null);
            } else {
                onClose();
            }
        } catch (err: any) {
            console.error('Ошибка сохранения слота:', err);
            if (err.response?.status === 400) {
                const serverError = err.response.data;
                setError(typeof serverError === 'string' ? serverError : serverError.message || 'Ошибка валидации');
            } else {
                setError('Не удалось сохранить. Попробуйте ещё раз.');
            }
        } finally {
            setSaving(false);
        }
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        persist(false);
    };

    const handleCreateTheme = async () => {
        if (!newThemeNumber.trim()) {
            setThemeError('Укажите номер темы');
            return;
        }
        setCreatingTheme(true);
        setThemeError(null);
        try {
            const created = await CurriculumService.createTheme({
                themeNumber: newThemeNumber.trim(),
                title: newThemeTitle.trim() || undefined,
                disciplineId,
            });
            setThemeLessons(prev => [...prev, created]);
            setThemeLessonId(created.id);
            setShowNewTheme(false);
            setNewThemeNumber('');
            setNewThemeTitle('');
        } catch (err: any) {
            const data = err?.response?.data;
            // 409 — тема с таким номером уже есть у дисциплины.
            setThemeError(typeof data === 'string' ? data : (data?.message || 'Не удалось создать тему.'));
        } finally {
            setCreatingTheme(false);
        }
    };

    const openEditTheme = () => {
        if (!selectedTheme) return;
        setEditThemeNumber(selectedTheme.themeNumber);
        setEditThemeTitle(selectedTheme.title ?? '');
        setEditThemeError(null);
        setShowNewTheme(false);
        setShowEditTheme(true);
    };

    const handleUpdateTheme = async () => {
        if (!selectedTheme) return;
        if (!editThemeNumber.trim()) {
            setEditThemeError('Укажите номер темы');
            return;
        }
        setSavingTheme(true);
        setEditThemeError(null);
        try {
            const updated = await CurriculumService.updateTheme(selectedTheme.id, {
                themeNumber: editThemeNumber.trim(),
                title: editThemeTitle.trim() || undefined,
                disciplineId,
            });
            // Тема глобальна — обновляем её в списке (подпись в селекте поменяется сразу).
            setThemeLessons(prev => prev.map(t => (t.id === updated.id ? updated : t)));
            setShowEditTheme(false);
        } catch (err: any) {
            const data = err?.response?.data;
            setEditThemeError(typeof data === 'string' ? data : (data?.message || 'Не удалось сохранить тему.'));
        } finally {
            setSavingTheme(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl overflow-hidden max-h-[90vh] flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Заголовок */}
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-indigo-100 rounded-lg">
                                <Layers size={20} className="text-indigo-600" />
                            </div>
                            <div>
                                <h2 className="text-lg font-black text-slate-900">
                                    {isEditMode ? 'Редактировать занятие' : 'Новое занятие'}
                                </h2>
                                <p className="text-xs text-slate-500 font-medium mt-0.5">
                                    Позиция в учебном плане: {position}
                                </p>
                            </div>
                        </div>
                        <button type="button" onClick={onClose} className="p-1 hover:bg-slate-200 rounded-lg transition-colors" disabled={saving}>
                            <X size={20} className="text-slate-500" />
                        </button>
                    </div>
                </div>

                {/* Форма */}
                <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
                    <div className="p-6 space-y-5 overflow-y-auto flex-1">
                        {/* Ошибка */}
                        {error && (
                            <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                                <AlertCircle size={18} className="shrink-0 mt-0.5" />
                                <span>{error}</span>
                            </div>
                        )}

                        {loadingData ? (
                            <div className="flex items-center justify-center py-8">
                                <Loader2 size={24} className="animate-spin text-blue-600" />
                            </div>
                        ) : (
                            <>
                                {/* Позиция */}
                                <div className="space-y-1.5">
                                    <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Позиция *</label>
                                    <input
                                        type="number"
                                        min="1"
                                        value={position}
                                        onChange={(e) => { setPosition(Number(e.target.value)); setPositionError(null); }}
                                        className={cn(
                                            "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                            positionError
                                                ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                                : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                        )}
                                        disabled={saving}
                                    />
                                    {positionError && <p className="text-xs text-red-600 font-medium">{positionError}</p>}
                                    <p className="text-[11px] text-slate-400">Порядковый номер занятия в курсе</p>
                                </div>

                                {/* Вид занятия */}
                                <div className="space-y-1.5">
                                    <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Вид занятия *</label>
                                    <select
                                        value={kindOfStudy}
                                        onChange={(e) => { setKindOfStudy(e.target.value as KindOfStudy); setKindOfStudyError(null); }}
                                        className={cn(
                                            "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white",
                                            kindOfStudyError
                                                ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                                : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                        )}
                                        disabled={saving}
                                    >
                                        <option value="">Выберите вид занятия</option>
                                        {kindsOfStudyEnum.map((kind) => (
                                            <option key={kind.value} value={kind.value}>
                                                {kind.label} ({kind.abbreviation})
                                            </option>
                                        ))}
                                    </select>
                                    {kindOfStudyError && <p className="text-xs text-red-600 font-medium">{kindOfStudyError}</p>}
                                </div>

                                {/* Разделитель */}
                                <div className="border-t border-slate-200 my-4" />

                                {/* Необязательные поля */}
                                <div className="space-y-4">
                                    <div className="flex items-center gap-2 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                        <Info size={14} />
                                        Дополнительные параметры
                                    </div>

                                    {/* Тема занятия */}
                                    <div className="space-y-1.5">
                                        <div className="flex items-center justify-between">
                                            <label className="block text-xs font-medium text-slate-600">Тема занятия (опционально)</label>
                                            <div className="flex items-center gap-3">
                                                {selectedTheme && !showNewTheme && (
                                                    <button
                                                        type="button"
                                                        onClick={() => (showEditTheme ? setShowEditTheme(false) : openEditTheme())}
                                                        disabled={saving}
                                                        className="flex items-center gap-1 text-xs font-semibold text-slate-600 hover:text-slate-800 disabled:opacity-50"
                                                    >
                                                        {showEditTheme ? <><X size={12} /> Отмена</> : <><Edit2 size={12} /> Изменить</>}
                                                    </button>
                                                )}
                                                <button
                                                    type="button"
                                                    onClick={() => { setShowNewTheme(v => !v); setShowEditTheme(false); setThemeError(null); }}
                                                    disabled={saving}
                                                    className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-800 disabled:opacity-50"
                                                >
                                                    {showNewTheme ? <><X size={12} /> Отмена</> : <><Plus size={12} /> Новая тема</>}
                                                </button>
                                            </div>
                                        </div>
                                        <select
                                            value={themeLessonId || ''}
                                            onChange={(e) => setThemeLessonId(e.target.value ? Number(e.target.value) : null)}
                                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                            disabled={saving}
                                        >
                                            <option value="">Без темы</option>
                                            {themeLessons.map((theme) => (
                                                <option key={theme.id} value={theme.id}>
                                                    Тема {theme.themeNumber}: {theme.title || 'Без названия'}
                                                </option>
                                            ))}
                                        </select>

                                        {/* Inline-создание темы: номер + название → сразу в список и выбрано */}
                                        {showNewTheme && (
                                            <div className="mt-2 p-3 border border-blue-200 rounded-xl bg-blue-50 space-y-2">
                                                {themeError && (
                                                    <div className="text-xs text-red-600 font-medium">{themeError}</div>
                                                )}
                                                <div className="flex gap-2">
                                                    <input
                                                        value={newThemeNumber}
                                                        onChange={(e) => { setNewThemeNumber(e.target.value); setThemeError(null); }}
                                                        placeholder="№ темы"
                                                        className="w-24 shrink-0 px-3 py-2 border border-slate-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        disabled={creatingTheme}
                                                    />
                                                    <input
                                                        value={newThemeTitle}
                                                        onChange={(e) => setNewThemeTitle(e.target.value)}
                                                        placeholder="Название (опционально)"
                                                        className="flex-1 min-w-0 px-3 py-2 border border-slate-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        disabled={creatingTheme}
                                                    />
                                                    <button
                                                        type="button"
                                                        onClick={handleCreateTheme}
                                                        disabled={creatingTheme || !newThemeNumber.trim()}
                                                        className="shrink-0 flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-sm font-bold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                                                    >
                                                        {creatingTheme ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                                                        Создать
                                                    </button>
                                                </div>
                                            </div>
                                        )}

                                        {/* Inline-правка выбранной темы: меняет тему глобально (во всех занятиях) */}
                                        {showEditTheme && selectedTheme && (
                                            <div className="mt-2 p-3 border border-slate-300 rounded-xl bg-slate-50 space-y-2">
                                                {editThemeError && (
                                                    <div className="text-xs text-red-600 font-medium">{editThemeError}</div>
                                                )}
                                                <div className="flex gap-2">
                                                    <input
                                                        value={editThemeNumber}
                                                        onChange={(e) => { setEditThemeNumber(e.target.value); setEditThemeError(null); }}
                                                        placeholder="№ темы"
                                                        className="w-24 shrink-0 px-3 py-2 border border-slate-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        disabled={savingTheme}
                                                    />
                                                    <input
                                                        value={editThemeTitle}
                                                        onChange={(e) => setEditThemeTitle(e.target.value)}
                                                        placeholder="Название темы"
                                                        className="flex-1 min-w-0 px-3 py-2 border border-slate-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        disabled={savingTheme}
                                                    />
                                                    <button
                                                        type="button"
                                                        onClick={handleUpdateTheme}
                                                        disabled={savingTheme || !editThemeNumber.trim()}
                                                        className="shrink-0 flex items-center gap-1 px-3 py-2 bg-slate-800 text-white text-sm font-bold rounded-lg hover:bg-slate-900 disabled:opacity-50 transition-colors"
                                                    >
                                                        {savingTheme ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                                                        Сохранить
                                                    </button>
                                                </div>
                                                <p className="text-[11px] text-slate-400">Тема глобальна — изменение затронет все занятия с ней.</p>
                                            </div>
                                        )}
                                    </div>

                                    {/* Обязательная аудитория */}
                                    <div className="space-y-1.5">
                                        <label className="block text-xs font-medium text-slate-600">Обязательная аудитория (опционально)</label>
                                        <select
                                            value={requiredAuditoriumId || ''}
                                            onChange={(e) => setRequiredAuditoriumId(e.target.value ? Number(e.target.value) : null)}
                                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                            disabled={saving}
                                        >
                                            <option value="">Не задана</option>
                                            {auditoriums.map((aud) => (
                                                <option key={aud.id} value={aud.id}>
                                                    {aud.name} (вместимость: {aud.capacity})
                                                </option>
                                            ))}
                                        </select>
                                    </div>

                                    {/* Приоритетная аудитория */}
                                    <div className="space-y-1.5">
                                        <label className="block text-xs font-medium text-slate-600">Приоритетная аудитория (опционально)</label>
                                        <select
                                            value={priorityAuditoriumId || ''}
                                            onChange={(e) => setPriorityAuditoriumId(e.target.value ? Number(e.target.value) : null)}
                                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                            disabled={saving}
                                        >
                                            <option value="">Не задана</option>
                                            {auditoriums.map((aud) => (
                                                <option key={aud.id} value={aud.id}>
                                                    {aud.name} (вместимость: {aud.capacity})
                                                </option>
                                            ))}
                                        </select>
                                    </div>

                                    {/* Пул аудиторий */}
                                    <div className="space-y-1.5">
                                        <label className="block text-xs font-medium text-slate-600">Пул аудиторий (опционально)</label>
                                        <select
                                            value={allowedAuditoriumPoolId || ''}
                                            onChange={(e) => setAllowedAuditoriumPoolId(e.target.value ? Number(e.target.value) : null)}
                                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                            disabled={saving}
                                        >
                                            <option value="">Не задан</option>
                                            {auditoriumPools.map((pool) => (
                                                <option key={pool.id} value={pool.id}>
                                                    {pool.name} {pool.description && `(${pool.description})`}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                            </>
                        )}
                    </div>

                    {/* Кнопки */}
                    <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3 shrink-0">
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={saving}
                            className="px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
                        {/* Поток создания: сохранить и сразу начать следующее занятие (окно не закрывается). */}
                        {!isEditMode && (
                            <button
                                type="button"
                                onClick={() => persist(true)}
                                disabled={saving || loadingData}
                                className="flex-1 px-4 py-2.5 border border-blue-300 text-blue-700 bg-white rounded-xl font-bold text-sm hover:bg-blue-50 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                            >
                                <Plus size={16} /> Сохранить и ещё
                            </button>
                        )}
                        <button
                            type="submit"
                            disabled={saving || loadingData}
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
