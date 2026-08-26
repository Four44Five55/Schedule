import React, { useState } from 'react';
import { X, Save, Loader2, BookOpen } from 'lucide-react';
import { DisciplineDto, DisciplineCreateDto, DisciplineUpdateDto } from '../../../types/api';
import { CurriculumService } from '../../../services/apiServices';
import { cn } from '../../../utils/cn';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

interface DisciplineFormModalProps {
    discipline: DisciplineDto | null;
    onClose: () => void;
    onSaved: (discipline: DisciplineDto) => void;
}

export const DisciplineFormModal: React.FC<DisciplineFormModalProps> = ({
    discipline,
    onClose,
    onSaved
}) => {
    const isEditMode = discipline !== null;

    const [name, setName] = useState(discipline?.name ?? '');
    const [abbreviation, setAbbreviation] = useState(discipline?.abbreviation ?? '');

    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [nameError, setNameError] = useState<string | null>(null);
    const [abbreviationError, setAbbreviationError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // Валидация
        let hasError = false;

        if (!name.trim()) {
            setNameError('Название дисциплины обязательно');
            hasError = true;
        } else if (name.length < 2 || name.length > 255) {
            setNameError('Длина названия должна быть от 2 до 255 символов');
            hasError = true;
        } else {
            setNameError(null);
        }

        if (abbreviation && abbreviation.length > 50) {
            setAbbreviationError('Длина аббревиатуры не должна превышать 50 символов');
            hasError = true;
        } else {
            setAbbreviationError(null);
        }

        if (hasError) return;

        setSaving(true);
        setError(null);

        try {
            const payload: DisciplineCreateDto | DisciplineUpdateDto = {
                name: name.trim(),
                abbreviation: abbreviation.trim() || undefined
            };

            console.log('📤 Отправка:', JSON.stringify(payload, null, 2));

            let saved: DisciplineDto;
            if (isEditMode && discipline) {
                saved = await CurriculumService.updateDiscipline(discipline.id, payload);
            } else {
                saved = await CurriculumService.createDiscipline(payload);
            }
            onSaved(saved);
        } catch (err: any) {
            console.error('Ошибка сохранения дисциплины:', err);
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
                                <BookOpen size={20} className="text-indigo-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать дисциплину' : 'Новая дисциплина'}
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

                        {/* Название */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Название дисциплины *</label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => { setName(e.target.value); setNameError(null); }}
                                placeholder="Программная инженерия"
                                className={cn(
                                    "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                    nameError
                                        ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                        : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                )}
                                disabled={saving}
                                autoFocus
                            />
                            {nameError && <p className="text-xs text-red-600 font-medium">{nameError}</p>}
                        </div>

                        {/* Аббревиатура */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Аббревиатура</label>
                            <input
                                type="text"
                                value={abbreviation}
                                onChange={(e) => { setAbbreviation(e.target.value); setAbbreviationError(null); }}
                                placeholder="ПИ"
                                maxLength={50}
                                className={cn(
                                    "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                    abbreviationError
                                        ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                        : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                )}
                                disabled={saving}
                            />
                            {abbreviationError && <p className="text-xs text-red-600 font-medium">{abbreviationError}</p>}
                            <p className="text-[11px] text-slate-400">Краткое обозначение (необязательно)</p>
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
                            disabled={saving}
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
