import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, Layers, AlertCircle, Info } from 'lucide-react';
import { CurriculumSlotDto, CurriculumSlotCreateDto, CurriculumSlotUpdateDto, KindOfStudy, ThemeLessonDto, AuditoriumDto, AuditoriumPoolDto } from '../../../types/api';
import { CurriculumService, ResourceService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';

interface CurriculumSlotFormModalProps {
    slot: CurriculumSlotDto | null;
    disciplineCourseId: number;
    nextPosition: number; // Следующая доступная позиция в курсе
    onClose: () => void;
    onSaved: (slot: CurriculumSlotDto) => void;
}

export const CurriculumSlotFormModal: React.FC<CurriculumSlotFormModalProps> = ({
    slot,
    disciplineCourseId,
    nextPosition,
    onClose,
    onSaved
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

    // Состояние отправки
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Валидация
    const [positionError, setPositionError] = useState<string | null>(null);
    const [kindOfStudyError, setKindOfStudyError] = useState<string | null>(null);

    // Загрузка данных
    useEffect(() => {
        if (!disciplineCourseId) {
            console.error('No disciplineCourseId provided');
            setThemeLessons([]);
            setAuditoriums([]);
            setAuditoriumPools([]);
            setLoadingData(false);
            return;
        }

        setLoadingData(true);
        console.log('Loading data for course:', disciplineCourseId);

        // Загружаем курс, чтобы получить ID дисциплины
        CurriculumService.getCourse(disciplineCourseId)
            .then(course => {
                console.log('Course loaded:', course);
                // Загружаем темы для дисциплины курса
                return Promise.all([
                    CurriculumService.getThemesByDiscipline(course.discipline.id).catch(() => []),
                    ResourceService.getAuditoriums(),
                    ResourceService.getAuditoriumPools()
                ]);
            })
            .then(([themes, auds, pools]) => {
                console.log('Data loaded:', { themesCount: themes.length, audsCount: auds.length, poolsCount: pools.length });
                setThemeLessons(themes);
                setAuditoriums(auds);
                setAuditoriumPools(pools);
            })
            .catch((error) => {
                console.error('Error loading data:', error);
                setThemeLessons([]);
                setAuditoriums([]);
                setAuditoriumPools([]);
            })
            .finally(() => setLoadingData(false));
    }, [disciplineCourseId]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // Валидация
        let hasError = false;

        if (position < 0) {
            setPositionError('Позиция должна быть 0 или больше');
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
            let saved: CurriculumSlotDto;
            if (isEditMode && slot) {
                const payload: CurriculumSlotUpdateDto = {
                    kindOfStudy: kindOfStudy as KindOfStudy,
                    themeLessonId: themeLessonId || undefined,
                    requiredAuditoriumId: requiredAuditoriumId || undefined,
                    priorityAuditoriumId: priorityAuditoriumId || undefined,
                    allowedAuditoriumPoolId: allowedAuditoriumPoolId || undefined
                };
                saved = await CurriculumService.updateSlot(slot.id, payload);
            } else {
                const payload: CurriculumSlotCreateDto = {
                    disciplineCourseId,
                    position,
                    kindOfStudy: kindOfStudy as KindOfStudy,
                    themeLessonId: themeLessonId || undefined,
                    requiredAuditoriumId: requiredAuditoriumId || undefined,
                    priorityAuditoriumId: priorityAuditoriumId || undefined,
                    allowedAuditoriumPoolId: allowedAuditoriumPoolId || undefined
                };
                saved = await CurriculumService.createSlot(payload);
            }
            onSaved(saved);
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
                                        min="0"
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
                                        <label className="block text-xs font-medium text-slate-600">Тема занятия (опционально)</label>
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
                            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
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
