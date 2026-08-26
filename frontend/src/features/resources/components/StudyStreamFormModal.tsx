import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, Layers, Users, BookOpen, Check } from 'lucide-react';
import { StudyStreamDto, StudyStreamCreateDto, StudyStreamUpdateDto, GroupDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { cn } from '../../../utils/cn';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

interface StudyStreamFormModalProps {
    stream: StudyStreamDto | null;
    onClose: () => void;
    onSaved: (stream: StudyStreamDto) => void;
}

export const StudyStreamFormModal: React.FC<StudyStreamFormModalProps> = ({
                                                                              stream,
                                                                              onClose,
                                                                              onSaved
                                                                          }) => {
    const isEditMode = stream !== null;

    const [name, setName] = useState(stream?.name ?? '');
    const [semester, setSemester] = useState(stream?.semester ?? 1);
    const [selectedGroupIds, setSelectedGroupIds] = useState<Set<number>>(
        new Set(stream?.groups.map((g) => g.id) ?? [])
    );

    const [groups, setGroups] = useState<GroupDto[]>([]);
    const [loadingGroups, setLoadingGroups] = useState(true);

    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [errors, setErrors] = useState<{ name?: string; semester?: string; groups?: string }>({});

    useEffect(() => {
        setLoadingGroups(true);
        ResourceService.getGroups()
            .then(setGroups)
            // Поток — это состав групп: пустой список равнозначен неработающей форме.
            .catch((err) => { setGroups([]); setError(errorMessage(err, 'Не удалось загрузить список групп.')); })
            .finally(() => setLoadingGroups(false));
    }, []);

    const toggleGroup = (id: number) => {
        setSelectedGroupIds((prev) => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const validate = (): boolean => {
        const nextErrors: typeof errors = {};
        if (!name.trim()) nextErrors.name = 'Название потока обязательно';
        if (name.length > 255) nextErrors.name = 'Название не должно превышать 255 символов';
        if (semester < 1) nextErrors.semester = 'Семестр должен быть больше 0';
        if (selectedGroupIds.size === 0) nextErrors.groups = 'Выберите хотя бы одну группу';
        setErrors(nextErrors);
        return Object.keys(nextErrors).length === 0;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validate()) return;

        setSaving(true);
        setError(null);

        try {
            const payload: StudyStreamCreateDto | StudyStreamUpdateDto = {
                name: name.trim(),
                semester,
                groupIds: Array.from(selectedGroupIds)
            };

            console.log('📤 Отправка потока:', JSON.stringify(payload, null, 2));

            let saved: StudyStreamDto;
            if (isEditMode && stream) {
                saved = await ResourceService.updateStream(stream.id, payload);
            } else {
                saved = await ResourceService.createStream(payload);
            }
            onSaved(saved);
        } catch (err: any) {
            console.error('Ошибка сохранения потока:', err);
            // Текст отказа пишет бэк — он один знает, что именно совпало; здесь только запасной.
            setError(errorMessage(err, 'Не удалось сохранить поток. Попробуйте ещё раз.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden max-h-[90vh] flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Заголовок */}
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-teal-100 rounded-lg">
                                <Layers size={20} className="text-teal-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать поток' : 'Новый поток'}
                            </h2>
                        </div>
                        <button type="button" onClick={onClose} className="p-1 hover:bg-slate-200 rounded-lg transition-colors" disabled={saving}>
                            <X size={20} className="text-slate-500" />
                        </button>
                    </div>
                </div>

                <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
                    <div className="p-6 space-y-5 overflow-y-auto flex-1">
                        {/* Ошибка */}
                        {error && (
                            <ErrorBanner message={error} />
                        )}

                        {/* Название */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Название потока *</label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => { setName(e.target.value); setErrors((p) => ({ ...p, name: undefined })); }}
                                placeholder="Например: Поток ИВТ-3"
                                className={cn(
                                    "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                    errors.name
                                        ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                        : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                )}
                                disabled={saving}
                                autoFocus
                            />
                            {errors.name && <p className="text-xs text-red-600 font-medium">{errors.name}</p>}
                        </div>

                        {/* Семестр */}
                        <div className="space-y-1.5">
                            <label className="flex items-center gap-2 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <BookOpen size={12} />
                                Семестр *
                            </label>
                            <input
                                type="number"
                                value={semester}
                                onChange={(e) => { setSemester(parseInt(e.target.value) || 0); setErrors((p) => ({ ...p, semester: undefined })); }}
                                min={1}
                                className={cn(
                                    "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                    errors.semester
                                        ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                        : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                                )}
                                disabled={saving}
                            />
                            {errors.semester && <p className="text-xs text-red-600 font-medium">{errors.semester}</p>}
                        </div>

                        {/* Группы */}
                        <div className="space-y-2">
                            <label className="flex items-center gap-2 text-xs font-bold text-slate-600 uppercase tracking-wider">
                                <Users size={12} />
                                Группы в потоке *
                            </label>

                            {loadingGroups ? (
                                <div className="text-xs text-slate-400 italic">Загрузка групп...</div>
                            ) : groups.length === 0 ? (
                                <div className="text-xs text-red-500">Сначала создайте группы</div>
                            ) : (
                                <div className="border border-slate-200 rounded-xl overflow-hidden">
                                    <div className="max-h-[180px] overflow-y-auto custom-scrollbar">
                                        {groups.map((group) => (
                                            <button
                                                key={group.id}
                                                type="button"
                                                onClick={() => toggleGroup(group.id)}
                                                disabled={saving}
                                                className={cn(
                                                    "w-full flex items-center justify-between px-4 py-2.5 text-sm transition-colors border-b border-slate-50 last:border-none",
                                                    selectedGroupIds.has(group.id)
                                                        ? "bg-blue-50 text-blue-800 font-bold"
                                                        : "bg-white text-slate-700 hover:bg-slate-50"
                                                )}
                                            >
                                                <span>{group.name}</span>
                                                {selectedGroupIds.has(group.id) && (
                                                    <Check size={16} className="text-blue-600" />
                                                )}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )}

                            <div className="flex items-center justify-between text-[11px] text-slate-500">
                                <span>Выбрано: <span className="font-black text-slate-900">{selectedGroupIds.size}</span></span>
                                {errors.groups && <span className="text-red-600 font-medium">{errors.groups}</span>}
                            </div>
                        </div>

                        {/* Сводка */}
                        {selectedGroupIds.size > 0 && (
                            <div className="p-3 bg-emerald-50 border border-emerald-100 rounded-xl">
                                <div className="flex items-center gap-2 text-emerald-700 text-xs font-bold">
                                    <Users size={14} />
                                    Всего студентов:{' '}
                                    {groups
                                        .filter((g) => selectedGroupIds.has(g.id))
                                        .reduce((sum, g) => sum + g.size, 0)}
                                </div>
                            </div>
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
                            disabled={saving || loadingGroups}
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
