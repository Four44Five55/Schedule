import React, { useCallback, useEffect, useState } from 'react';
import { cn } from '../../../utils/cn';
import { CQRSService } from '../../../services/cqrsApiService';
import { AuditoriumOptionDto } from '../../../types/cqrs';
import { ScheduledLessonDto } from '../../../types/api';
import { AlertTriangle, Check, DoorOpen, Loader2, Lock, Users, X } from 'lucide-react';

interface AuditoriumPickerModalProps {
  lesson: ScheduledLessonDto;
  currentVersion?: number;
  onClose: () => void;
  /** Успешная смена: новая версия сессии + сигнал хосту перечитать расписание. */
  onChanged: (newVersion: number) => void;
}

/**
 * Выбор аудитории для стоящего занятия.
 *
 * <p>Раньше комнату нельзя было выбрать вообще: её всегда и только назначал алгоритм. Диспетчер
 * мог двигать занятие во времени, закреплять, снимать — но не сказать «пусть идёт в 205-3».</p>
 *
 * <b>Показываем ВСЕ комнаты, включая занятые.</b> Список «куда можно» отвечал бы на вопрос
 * системы, а диспетчеру нужен ответ на свой: почему нельзя вот в эту. «Занята Философией 954» —
 * информация, по которой он примет решение; молчаливое отсутствие строки — не информация.
 *
 * <b>Несколько комнат — это норма, поэтому выбор множественный.</b> Экзамен с рассадкой по двум
 * аудиториям, деление группы на полупотоки на английском, и, по словам заказчика, «возможны ещё
 * другие случаи». Вывести число автоматически нельзя — из вместимости следует только «не влезли»,
 * а рассадка на экзамене к размеру группы отношения не имеет. Поэтому сколько комнат и каких,
 * говорит человек, а система проверяет физику.
 *
 * <b>Теснота не запрещает выбор.</b> Она видна числом заранее («не хватит 2 мест») — и всё.
 * Отдельного «подтверждаю тесноту» нет намеренно: это второе подтверждение того же самого.
 */
export const AuditoriumPickerModal: React.FC<AuditoriumPickerModalProps> = ({
  lesson, currentVersion, onClose, onChanged
}) => {
  const [options, setOptions] = useState<AuditoriumOptionDto[] | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const placementId = lesson.placementId;

  useEffect(() => {
    if (!placementId) return;
    let cancelled = false;
    CQRSService.getAuditoriumOptions(placementId)
      .then((data) => {
        if (cancelled) return;
        setOptions(data);
        setSelected(new Set(data.filter((o) => o.current).map((o) => o.auditoriumId)));
      })
      .catch((e) => {
        console.error('Не удалось загрузить варианты аудиторий:', e);
        if (!cancelled) setOptions([]);
      });
    return () => { cancelled = true; };
  }, [placementId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const toggle = (option: AuditoriumOptionDto) => {
    // Занятую выбрать нельзя — это физика, а не предпочтение. Но свою текущую разрешаем снять
    // даже если она почему-то помечена занятой: иначе из такого состояния не выбраться.
    if (option.status !== 'FREE' && !option.current) return;
    setError(null);
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(option.auditoriumId)) next.delete(option.auditoriumId);
      else next.add(option.auditoriumId);
      return next;
    });
  };

  const save = useCallback(async () => {
    if (!placementId || selected.size === 0) return;
    setSaving(true);
    setError(null);
    try {
      const session = await CQRSService.changeAuditorium(placementId, [...selected], currentVersion);
      onChanged(session.version);
      onClose();
    } catch (e: any) {
      const conflict = e?.response?.data;
      if (e?.response?.status === 409) {
        setError(conflict?.message ?? 'Расписание изменилось параллельно — обновите данные.');
        // Версию из ответа отдаём хосту всегда: иначе вкладка залипнет на устаревшей.
        if (conflict?.currentVersion != null) onChanged(conflict.currentVersion);
      } else {
        setError('Не удалось сменить аудиторию.');
        console.error('Смена аудитории:', e);
      }
    } finally {
      setSaving(false);
    }
  }, [placementId, selected, currentVersion, onChanged, onClose]);

  const currentIds = new Set((options ?? []).filter((o) => o.current).map((o) => o.auditoriumId));
  const changed = selected.size !== currentIds.size || [...selected].some((id) => !currentIds.has(id));

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4"
         onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[80vh] flex flex-col"
           onClick={(e) => e.stopPropagation()}>

        <div className="flex items-start justify-between gap-3 px-5 pt-4 pb-3 border-b border-slate-100">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-sm font-black text-slate-800">
              <DoorOpen size={16} className="text-slate-400" />
              Аудитория занятия
            </div>
            <div className="mt-1 text-[11px] text-slate-500 truncate">
              {lesson.disciplineAbbreviation} · {lesson.kindOfStudyAbbr}
              {lesson.themeNumber ? `/Т.${lesson.themeNumber}` : ''} · {lesson.groupNames.join(', ') || '—'}
            </div>
            <div className="text-[11px] text-slate-400">{lesson.date} · {lesson.timeSlotPair}</div>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 shrink-0">
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-auto custom-scrollbar px-2 py-2">
          {options === null ? (
            <div className="py-10 text-center text-xs text-slate-400 flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Смотрим, какие комнаты свободны…
            </div>
          ) : options.length === 0 ? (
            <div className="py-10 text-center text-xs text-slate-400">Аудиторий нет</div>
          ) : (
            <div className="space-y-0.5">
              {options.map((o) => {
                const isSelected = selected.has(o.auditoriumId);
                const selectable = o.status === 'FREE' || o.current;
                const tight = o.shortfall > 0;
                return (
                  <button
                    key={o.auditoriumId}
                    type="button"
                    onClick={() => toggle(o)}
                    disabled={!selectable}
                    title={
                      o.status === 'BUSY' ? `Занята: ${o.occupiedBy}`
                        : o.status === 'CONSTRAINED' ? 'Закрыта ограничением'
                        : tight ? `Не хватит ${o.shortfall} мест — решать вам`
                        : 'Свободна'
                    }
                    className={cn(
                      'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors',
                      isSelected ? 'bg-emerald-50 ring-1 ring-inset ring-emerald-400' : 'hover:bg-slate-50',
                      !selectable && 'opacity-50 cursor-not-allowed hover:bg-transparent'
                    )}
                  >
                    <span className={cn(
                      'w-4 h-4 rounded border flex items-center justify-center shrink-0',
                      isSelected ? 'bg-emerald-500 border-emerald-500' : 'border-slate-300'
                    )}>
                      {isSelected && <Check size={11} className="text-white" />}
                    </span>

                    <span className="font-bold text-xs text-slate-700 w-16 shrink-0">{o.name}</span>

                    <span className="flex items-center gap-1 text-[10px] text-slate-400 w-14 shrink-0">
                      <Users size={10} /> {o.capacity}
                    </span>

                    <span className="flex-1 min-w-0 text-[10px] truncate">
                      {o.status === 'BUSY' ? (
                        <span className="text-red-600 font-semibold flex items-center gap-1">
                          <Lock size={10} className="shrink-0" /> {o.occupiedBy}
                        </span>
                      ) : o.status === 'CONSTRAINED' ? (
                        <span className="text-slate-500 font-semibold">ограничение</span>
                      ) : tight ? (
                        <span className="text-amber-600 font-semibold flex items-center gap-1">
                          <AlertTriangle size={10} className="shrink-0" /> тесно на {o.shortfall}
                        </span>
                      ) : (
                        <span className="text-emerald-600 font-semibold">свободна</span>
                      )}
                    </span>

                    {o.current && (
                      <span className="text-[9px] font-black uppercase tracking-tight text-slate-400 shrink-0">
                        сейчас
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {error && (
          <div className="mx-4 mb-2 px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-[11px] text-red-800">
            {error}
          </div>
        )}

        <div className="flex items-center justify-between gap-3 px-5 py-3 border-t border-slate-100">
          <span className="text-[10px] text-slate-400">
            {selected.size === 0
              ? 'Выберите хотя бы одну'
              : selected.size > 1
                ? `Выбрано комнат: ${selected.size} — занятие пройдёт в них одновременно`
                : 'Выбрана 1 комната'}
          </span>
          <div className="flex items-center gap-2">
            <button onClick={onClose}
                    className="px-3 py-1.5 text-xs font-semibold text-slate-500 hover:text-slate-700">
              Отмена
            </button>
            <button
              onClick={save}
              disabled={saving || selected.size === 0 || !changed}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-900 text-white text-xs font-semibold rounded-lg hover:bg-slate-700 transition-colors disabled:opacity-40"
            >
              {saving ? <Loader2 size={13} className="animate-spin" /> : <Check size={13} />}
              Сохранить
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
