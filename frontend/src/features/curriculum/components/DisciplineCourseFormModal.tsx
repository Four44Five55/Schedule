import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, Layers } from 'lucide-react';
import { DisciplineCourseDto, DisciplineCourseCreateDto, DisciplineCourseUpdateDto, DisciplineDto, StudyPeriodDto } from '../../../types/api';
import { CurriculumService, ResourceService } from '../../../services/apiServices';
import { cn } from '../../../utils/cn';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

interface DisciplineCourseFormModalProps {
    course: DisciplineCourseDto | null;
    disciplineId?: number; // Для создания нового курса
    lockedPeriodId?: number; // Жёстко привязать курс к периоду (контекст планировщика)
    onClose: () => void;
    onSaved: (course: DisciplineCourseDto) => void;
}

export const DisciplineCourseFormModal: React.FC<DisciplineCourseFormModalProps> = ({
    course,
    disciplineId: propDisciplineId,
    lockedPeriodId,
    onClose,
    onSaved
}) => {
    const isEditMode = course !== null;

    // Состояние формы
    const [disciplineId, setDisciplineId] = useState<number>(
        course?.discipline?.id || propDisciplineId || 0
    );
    const [studyPeriodId, setStudyPeriodId] = useState<number | null>(lockedPeriodId ?? null);
    const [semester, setSemester] = useState(course?.semester || 1);

    // Списки для выбора
    const [disciplines, setDisciplines] = useState<DisciplineDto[]>([]);
    const [studyPeriods, setStudyPeriods] = useState<StudyPeriodDto[]>([]);
    const [loadingData, setLoadingData] = useState(true);

    // Состояние отправки
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Валидация
    const [disciplineError, setDisciplineError] = useState<string | null>(null);
    const [studyPeriodError, setStudyPeriodError] = useState<string | null>(null);
    const [semesterError, setSemesterError] = useState<string | null>(null);

    // Загрузка данных
    useEffect(() => {
        setLoadingData(true);
        Promise.all([
            CurriculumService.getDisciplines(),
            ResourceService.getStudyPeriods()
        ])
            .then(([discs, periods]) => {
                setDisciplines(discs);
                setStudyPeriods(periods);

                // Приоритет: жёстко привязанный период (контекст планировщика) →
                // период редактируемого курса → первый из списка по умолчанию.
                if (lockedPeriodId != null) {
                    setStudyPeriodId(lockedPeriodId);
                } else if (course && course.studyPeriod) {
                    setStudyPeriodId(course.studyPeriod.id);
                } else if (periods.length > 0) {
                    setStudyPeriodId(periods[0].id);
                }
            })
            .catch((err) => {
                setDisciplines([]);
                setStudyPeriods([]);
                setError(errorMessage(err, 'Не удалось загрузить дисциплины и периоды.'));
            })
            .finally(() => setLoadingData(false));
    }, [course]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // Валидация
        let hasError = false;

        if (!disciplineId) {
            setDisciplineError('Выберите дисциплину');
            hasError = true;
        } else {
            setDisciplineError(null);
        }

        if (!studyPeriodId) {
            setStudyPeriodError('Выберите учебный период');
            hasError = true;
        } else {
            setStudyPeriodError(null);
        }

        if (semester < 1 || semester > 12) {
            setSemesterError('Номер семестра должен быть от 1 до 12');
            hasError = true;
        } else {
            setSemesterError(null);
        }

        if (hasError) return;

        setSaving(true);
        setError(null);

        try {
            let saved: DisciplineCourseDto;
            if (isEditMode && course) {
                // Бэкенд при обновлении принимает только учебный период.
                const payload: DisciplineCourseUpdateDto = { studyPeriodId: studyPeriodId! };
                saved = await CurriculumService.updateCourse(course.id, payload);
            } else {
                const payload: DisciplineCourseCreateDto = { disciplineId, studyPeriodId: studyPeriodId!, semester };
                saved = await CurriculumService.createCourse(payload);
            }
            onSaved(saved);
        } catch (err: any) {
            console.error('Ошибка сохранения курса:', err);
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
                {/* Заголовок */}
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-indigo-100 rounded-lg">
                                <Layers size={20} className="text-indigo-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать курс' : 'Новый учебный курс'}
                            </h2>
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
                            <ErrorBanner message={error} />
                        )}

                        {loadingData ? (
                            <div className="flex items-center justify-center py-8">
                                <Loader2 size={24} className="animate-spin text-blue-600" />
                            </div>
                        ) : (
                            <>
                                {/* Дисциплина */}
                                {!propDisciplineId && (
                                    <div className="space-y-1.5">
                                        <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Дисциплина *</label>
                                        <select
                                            value={disciplineId}
                                            onChange={(e) => { setDisciplineId(Number(e.target.value)); setDisciplineError(null); }}
                                            className={cn(
                                                "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white",
                                                disciplineError
                                                    ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                                    : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                            )}
                                            disabled={saving || isEditMode}
                                        >
                                            <option value="">Выберите дисциплину</option>
                                            {disciplines.map(d => (
                                                <option key={d.id} value={d.id}>{d.name} ({d.abbreviation || 'без аббревиатуры'})</option>
                                            ))}
                                        </select>
                                        {disciplineError && <p className="text-xs text-red-600 font-medium">{disciplineError}</p>}
                                    </div>
                                )}

                                {/* Учебный период */}
                                <div className="space-y-1.5">
                                    <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Учебный период *</label>
                                    <select
                                        value={studyPeriodId || ''}
                                        onChange={(e) => { setStudyPeriodId(Number(e.target.value)); setStudyPeriodError(null); }}
                                        className={cn(
                                            "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none appearance-none bg-white",
                                            studyPeriodError
                                                ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                                : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20",
                                            lockedPeriodId != null && "bg-slate-100 cursor-not-allowed"
                                        )}
                                        disabled={saving || lockedPeriodId != null}
                                    >
                                        <option value="">Выберите период</option>
                                        {studyPeriods.map(p => (
                                            <option key={p.id} value={p.id}>{p.name} ({p.studyYear})</option>
                                        ))}
                                    </select>
                                    {studyPeriodError && <p className="text-xs text-red-600 font-medium">{studyPeriodError}</p>}
                                </div>

                                {/* Семестр */}
                                <div className="space-y-1.5">
                                    <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Номер семестра *</label>
                                    <input
                                        type="number"
                                        min="1"
                                        max="12"
                                        value={semester}
                                        onChange={(e) => { setSemester(Number(e.target.value)); setSemesterError(null); }}
                                        className={cn(
                                            "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                            semesterError
                                                ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                                : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20",
                                            isEditMode && "bg-slate-100 cursor-not-allowed"
                                        )}
                                        disabled={saving || isEditMode}
                                    />
                                    {semesterError && <p className="text-xs text-red-600 font-medium">{semesterError}</p>}
                                    <p className="text-[11px] text-slate-400">
                                        {isEditMode ? 'Семестр нельзя изменить после создания курса' : 'Номер семестра (1-12)'}
                                    </p>
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
