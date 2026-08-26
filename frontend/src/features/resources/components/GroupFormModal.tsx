import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, Users, Home, Network, CalendarDays } from 'lucide-react';
import { GroupDto, GroupCreateDto, GroupUpdateDto, AuditoriumDto } from '../../../types/api';
import { ResourceService } from '../../../services/apiServices';
import { useOrgUnits } from '../../orgUnit/hooks/useOrgUnits';
import { cn } from '../../../utils/cn';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

interface GroupFormModalProps {
    /** Группа для редактирования (null = создание новой) */
    group: GroupDto | null;
    /** Закрытие модального окна */
    onClose: () => void;
    /** Callback после успешного сохранения */
    onSaved: (group: GroupDto) => void;
}

export const GroupFormModal: React.FC<GroupFormModalProps> = ({
                                                                  group,
                                                                  onClose,
                                                                  onSaved
                                                              }) => {
    const isEditMode = group !== null;
    const { flat: orgUnits, loading: orgUnitsLoading } = useOrgUnits();

    // Состояние формы
    const [name, setName] = useState(group?.name || '');
    const [size, setSize] = useState(group?.size || 1);
    const [baseAuditoriumId, setBaseAuditoriumId] = useState<number | null>(
        group?.baseAuditorium?.id || null
    );
    // Год набора (поступления); null — не указан, это легитимно для уже заведённых групп.
    const [enrollmentYear, setEnrollmentYear] = useState<number | null>(group?.enrollmentYear ?? null);
    // Подразделение группы — кафедра или факультет; «не распределена» легитимно.
    const [orgUnitId, setOrgUnitId] = useState<number | null>(group?.orgUnitId ?? null);

    // Список аудиторий для выбора
    const [auditoriums, setAuditoriums] = useState<AuditoriumDto[]>([]);
    const [loadingAuditoriums, setLoadingAuditoriums] = useState(true);

    // Состояние отправки
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Валидация
    const [errors, setErrors] = useState<{ name?: string; size?: string }>({});

    // Загрузка списка аудиторий
    useEffect(() => {
        setLoadingAuditoriums(true);
        ResourceService.getAuditoriums()
            .then(setAuditoriums)
            .catch((err) => { setAuditoriums([]); setError(errorMessage(err, 'Не удалось загрузить список аудиторий — домашнюю комнату выбрать не из чего.')); })
            .finally(() => setLoadingAuditoriums(false));
    }, []);

    // Валидация формы
    const validate = (): boolean => {
        const newErrors: { name?: string; size?: string } = {};

        if (!name.trim()) {
            newErrors.name = 'Название группы обязательно';
        } else if (name.length > 255) {
            newErrors.name = 'Название не должно превышать 255 символов';
        }

        if (size < 1) {
            newErrors.size = 'Количество студентов должно быть больше 0';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Отправка формы
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validate()) return;

        setSaving(true);
        setError(null);

        try {
            const payload: GroupCreateDto | GroupUpdateDto = {
                name: name.trim(),
                size,
                baseAuditoriumId: baseAuditoriumId || null,
                enrollmentYear: enrollmentYear || null,
                orgUnitId: orgUnitId || null
            };

            let savedGroup: GroupDto;

            if (isEditMode && group) {
                savedGroup = await ResourceService.updateGroup(group.id, payload);
            } else {
                savedGroup = await ResourceService.createGroup(payload);
            }

            onSaved(savedGroup);
        } catch (err: any) {
            console.error('Ошибка сохранения группы:', err);

            // Текст отказа пишет бэк — он один знает, что именно совпало; здесь только запасной.
            setError(errorMessage(err, 'Не удалось сохранить группу. Попробуйте ещё раз.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
                {/* Заголовок */}
                <div className="px-6 py-4 border-b border-slate-200 bg-slate-50">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-cyan-100 rounded-lg">
                                <Users size={20} className="text-cyan-600" />
                            </div>
                            <h2 className="text-lg font-black text-slate-900">
                                {isEditMode ? 'Редактировать группу' : 'Новая группа'}
                            </h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1 hover:bg-slate-200 rounded-lg transition-colors"
                            disabled={saving}
                        >
                            <X size={20} className="text-slate-500" />
                        </button>
                    </div>
                </div>

                {/* Форма */}
                <form onSubmit={handleSubmit} className="p-6 space-y-5">
                    {/* Ошибка общая */}
                    {error && (
                        <ErrorBanner message={error} />
                    )}

                    {/* Название */}
                    <div className="space-y-1.5">
                        <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                            Название группы *
                        </label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => {
                                setName(e.target.value);
                                if (errors.name) setErrors({ ...errors, name: undefined });
                            }}
                            placeholder="Например: ПИ-401"
                            className={cn(
                                "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                errors.name
                                    ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                    : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                            )}
                            disabled={saving}
                            autoFocus
                        />
                        {errors.name && (
                            <p className="text-xs text-red-600 font-medium">{errors.name}</p>
                        )}
                    </div>

                    {/* Количество студентов */}
                    <div className="space-y-1.5">
                        <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                            Количество студентов *
                        </label>
                        <input
                            type="number"
                            value={size}
                            onChange={(e) => {
                                setSize(parseInt(e.target.value) || 0);
                                if (errors.size) setErrors({ ...errors, size: undefined });
                            }}
                            min={1}
                            className={cn(
                                "w-full px-4 py-2.5 border rounded-xl text-sm font-medium transition-all outline-none",
                                errors.size
                                    ? "border-red-300 bg-red-50 focus:border-red-500 focus:ring-2 focus:ring-red-500/20"
                                    : "border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                            )}
                            disabled={saving}
                        />
                        {errors.size && (
                            <p className="text-xs text-red-600 font-medium">{errors.size}</p>
                        )}
                    </div>

                    {/* Год набора (поступления) */}
                    <div className="space-y-1.5">
                        <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
              <span className="flex items-center gap-1.5">
                <CalendarDays size={12} />
                Год набора
              </span>
                        </label>
                        <input
                            type="number"
                            value={enrollmentYear ?? ''}
                            onChange={(e) =>
                                setEnrollmentYear(e.target.value ? parseInt(e.target.value) : null)
                            }
                            placeholder={`Например: ${new Date().getFullYear()}`}
                            min={1900}
                            max={2200}
                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                            disabled={saving}
                        />
                        <p className="text-xs text-slate-400">
                            Год поступления. Отличает одноимённые группы разных наборов
                        </p>
                    </div>

                    {/* Базовая аудитория */}
                    <div className="space-y-1.5">
                        <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
              <span className="flex items-center gap-1.5">
                <Home size={12} />
                Базовая аудитория
              </span>
                        </label>
                        <select
                            value={baseAuditoriumId || ''}
                            onChange={(e) => setBaseAuditoriumId(e.target.value ? parseInt(e.target.value) : null)}
                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 appearance-none cursor-pointer bg-white"
                            disabled={saving || loadingAuditoriums}
                        >
                            <option value="">— Не указана —</option>
                            {auditoriums.map((aud) => (
                                <option key={aud.id} value={aud.id}>
                                    {aud.name} ({aud.capacity} мест)
                                </option>
                            ))}
                        </select>
                        <p className="text-xs text-slate-400">
                            Аудитория, закреплённая за группой по умолчанию
                        </p>
                    </div>

                    {/* Подразделение (кафедра или факультет) */}
                    <div className="space-y-1.5">
                        <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
              <span className="flex items-center gap-1.5">
                <Network size={12} />
                Подразделение
              </span>
                        </label>
                        <select
                            value={orgUnitId || ''}
                            onChange={(e) => setOrgUnitId(e.target.value ? parseInt(e.target.value) : null)}
                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium transition-all outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 appearance-none cursor-pointer bg-white"
                            disabled={saving || orgUnitsLoading}
                        >
                            <option value="">— Не распределена —</option>
                            {orgUnits
                                // Расформированные не предлагаем, но уже выбранное показываем,
                                // иначе правка карточки молча стёрла бы привязку.
                                .filter((u) => u.active || u.id === orgUnitId)
                                .map((u) => (
                                    <option key={u.id} value={u.id}>
                                        {' '.repeat(u.depth * 4)}
                                        {u.name}
                                    </option>
                                ))}
                        </select>
                    </div>

                    {/* Кнопки */}
                    <div className="flex gap-3 pt-4">
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={saving}
                            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-slate-50 transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
                        <button
                            type="submit"
                            disabled={saving}
                            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {saving ? (
                                <>
                                    <Loader2 size={16} className="animate-spin" />
                                    Сохранение...
                                </>
                            ) : (
                                <>
                                    <Save size={16} />
                                    {isEditMode ? 'Сохранить' : 'Создать'}
                                </>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};
