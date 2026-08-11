import React, { useState, useEffect, useRef, useCallback, useId } from 'react';
import { CurriculumSlotDto, SlotChainDto, ThemeLessonDto, KindOfStudy } from '../../../types/api';
import { Plus, Edit2, Trash2, School, FileText, Loader2, Link2, Copy } from 'lucide-react';
import { CurriculumSlotFormModal } from './CurriculumSlotFormModal';
import { CurriculumPlanSource, SlotFormValues } from '../planSource';
import { CurriculumService } from '../../../services/apiServices';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';

export const KIND_LABELS: Record<string, string> = {
  LECTURE: 'Лекция',
  PRACTICAL_WORK: 'Практика',
  LAB_WORK: 'Лаб. работа',
  SEMINAR: 'Семинар',
  GROUP_WORK: 'Групп. работа',
  GROUP_EXERCISE: 'Упражнение',
  QUIZ: 'Контр. работа',
  INDIVIDUAL_REVIEW_INTERVIEW: 'Инд. опрос',
  CREDIT_WITH_GRADE: 'Зачёт с оценкой',
  CREDIT_WITHOUT_GRADE: 'Зачёт б/о',
  EXAM: 'Экзамен',
  INDEPENDENT_STUDY: 'Самост. работа',
};

export const KIND_COLORS: Record<string, string> = {
  LECTURE: 'bg-violet-100 text-violet-700 border-violet-200',
  EXAM: 'bg-red-100 text-red-700 border-red-200',
  LAB_WORK: 'bg-green-100 text-green-700 border-green-200',
  PRACTICAL_WORK: 'bg-blue-100 text-blue-700 border-blue-200',
  SEMINAR: 'bg-indigo-100 text-indigo-700 border-indigo-200',
  CREDIT_WITH_GRADE: 'bg-amber-100 text-amber-700 border-amber-200',
  CREDIT_WITHOUT_GRADE: 'bg-amber-100 text-amber-700 border-amber-200',
  QUIZ: 'bg-amber-100 text-amber-700 border-amber-200',
};

const SlotsSummary: React.FC<{ slots: CurriculumSlotDto[] }> = ({ slots }) => {
  const counts = slots.reduce<Record<string, number>>((acc, s) => {
    acc[s.kindOfStudy] = (acc[s.kindOfStudy] || 0) + 1;
    return acc;
  }, {});

  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);

  return (
    <div className="flex items-center gap-2 flex-wrap px-8 py-3 border-b border-blue-100 bg-white/60">
      {entries.map(([kind, count]) => (
        <div
          key={kind}
          className={cn(
            'flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-xs font-semibold',
            KIND_COLORS[kind] || 'bg-slate-100 text-slate-600 border-slate-200'
          )}
        >
          <span>{KIND_LABELS[kind] || kind}</span>
          <span className="font-black opacity-70">·</span>
          <span className="font-black">{count}</span>
        </div>
      ))}
      <div className="ml-auto text-xs text-slate-400 font-medium">
        Всего: <span className="font-bold text-slate-700">{slots.length}</span> зан.
      </div>
    </div>
  );
};

interface CurriculumPlanEditorProps {
  /** Дисциплина — для списка тем в модале. */
  disciplineId: number;
  /** Откуда брать и куда писать слоты (курс сейчас, шаблон — в будущем). */
  planSource: CurriculumPlanSource;
  /** Вызывается после любой мутации — хост может обновить свои производные данные (счётчики). */
  onChanged?: () => void;
}

/**
 * Переиспользуемый редактор учебного плана: список слотов + добавить/редактировать/удалить.
 * Не зависит от курса напрямую — только от {@link CurriculumPlanSource} (Strategy) и
 * `disciplineId`. Владеет своим списком слотов (single source of truth для редактора);
 * наружу сообщает об изменениях через `onChanged`.
 */
export const CurriculumPlanEditor: React.FC<CurriculumPlanEditorProps> = ({ disciplineId, planSource, onChanged }) => {
  // planSource пересоздаётся хостом на каждый рендер — держим актуальный в ref,
  // чтобы загрузка/мутации не зависели от его идентичности (иначе цикл перезагрузок).
  const sourceRef = useRef(planSource);
  sourceRef.current = planSource;

  const { kindOfStudy: kindsEnum, getStudyCategory } = useEnums();
  // Уникальный id для <datalist>: при нескольких раскрытых редакторах общий id
  // приводил к привязке input'а к чужому списку (темы другой дисциплины).
  const themeListId = useId();

  const [slots, setSlots] = useState<CurriculumSlotDto[]>([]);
  const [chains, setChains] = useState<SlotChainDto[]>([]);
  const [themes, setThemes] = useState<ThemeLessonDto[]>([]);
  const [loading, setLoading] = useState(true);

  const [showForm, setShowForm] = useState(false);
  const [selectedSlot, setSelectedSlot] = useState<CurriculumSlotDto | null>(null);
  const [deletingSlot, setDeletingSlot] = useState<number | null>(null);
  const [duplicatingSlot, setDuplicatingSlot] = useState<number | null>(null);

  // Быстрое добавление (inline): вид обязателен, тема опциональна.
  // База темы — НОМЕР (выбрать существующую или вписать новый); название —
  // отдельное опциональное поле (заполняется только при создании новой темы).
  const [quickKind, setQuickKind] = useState<KindOfStudy | ''>('');
  const [quickTheme, setQuickTheme] = useState('');
  const [quickThemeTitle, setQuickThemeTitle] = useState('');
  const [quickAdding, setQuickAdding] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [loadedSlots, allChains] = await Promise.all([
        sourceRef.current.list(),
        sourceRef.current.listChains(),
      ]);
      const ids = new Set(loadedSlots.map(s => s.id));
      setSlots(loadedSlots);
      // Сцепки приходят глобально — оставляем только внутри этого набора слотов.
      setChains(allChains.filter(c => ids.has(c.slotA.id) && ids.has(c.slotB.id)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  // Темы дисциплины — для inline-добавления (модал грузит свои отдельно).
  useEffect(() => {
    CurriculumService.getThemesByDiscipline(disciplineId).then(setThemes).catch(() => setThemes([]));
  }, [disciplineId]);

  const nextPosition = slots.length > 0 ? Math.max(...slots.map(s => s.position)) + 1 : 1;

  const openCreate = () => { setSelectedSlot(null); setShowForm(true); };
  const openEdit = (slot: CurriculumSlotDto) => { setSelectedSlot(slot); setShowForm(true); };

  const handleSave = async (values: SlotFormValues) => {
    if (selectedSlot) {
      await sourceRef.current.update(selectedSlot.id, values);
    } else {
      await sourceRef.current.create(values);
    }
    await reload();
    onChanged?.();
  };

  const handleDelete = async (slot: CurriculumSlotDto) => {
    // Цену удаления знает бэк: каскад curriculum_slot → assignment → lesson_placement уносит
    // и уже размещённые занятия, ВКЛЮЧАЯ закреплённые вручную, — молча и безвозвратно.
    // Источник-шаблон impact не отдаёт (там нечего терять) — тогда обычное подтверждение.
    let question = 'Удалить занятие?';
    try {
      const impact = await sourceRef.current.removeImpact?.(slot.id);
      if (impact && (impact.assignments > 0 || impact.placedLessons > 0)) {
        const loss = [
          impact.assignments > 0 ? `назначений: ${impact.assignments}` : null,
          impact.placedLessons > 0 ? `занятий в расписании: ${impact.placedLessons}` : null,
          impact.lockedLessons > 0 ? `из них закреплено вручную: ${impact.lockedLessons}` : null,
        ].filter(Boolean).join(', ');
        question = `Удалить занятие плана? Вместе с ним будет снесено — ${loss}.`
          + (impact.lockedLessons > 0 ? '\n\nРучная раскладка этих занятий будет потеряна.' : '')
          + '\n\nЭто действие нельзя отменить.';
      }
    } catch (e) {
      console.error('Не удалось получить последствия удаления занятия плана:', e);
    }
    if (!confirm(question)) return;
    setDeletingSlot(slot.id);
    try {
      await sourceRef.current.remove(slot.id);
      await reload();
      onChanged?.();
    } catch {
      alert('Не удалось удалить занятие');
    } finally {
      setDeletingSlot(null);
    }
  };

  // Существующая тема, совпавшая с введённым номером (или названием) — если есть,
  // создавать ничего не нужно, поле названия не используется.
  const quickThemeMatch = (() => {
    const t = quickTheme.trim();
    return t ? themes.find(x => x.themeNumber === t || x.title === t) : undefined;
  })();

  // Дубль занятия: идентичная копия (вид, тема, аудитории, пул) сразу после исходного.
  // Позиция = position+1; бэк сдвинет последующие слоты.
  const handleDuplicate = async (slot: CurriculumSlotDto) => {
    setDuplicatingSlot(slot.id);
    try {
      await sourceRef.current.create({
        position: slot.position + 1,
        kindOfStudy: slot.kindOfStudy,
        themeLessonId: slot.themeLesson?.id,
        requiredAuditoriumId: slot.requiredAuditorium?.id,
        priorityAuditoriumId: slot.priorityAuditorium?.id,
        allowedAuditoriumPoolId: slot.allowedAuditoriumPool?.id,
      });
      await reload();
      onChanged?.();
    } catch {
      alert('Не удалось дублировать занятие');
    } finally {
      setDuplicatingSlot(null);
    }
  };

  const handleQuickAdd = async () => {
    if (!quickKind) return;
    setQuickAdding(true);
    try {
      // Тема (опц.): пусто → без темы; номер совпал с существующей → берём её;
      // новый номер → создаём тему (название из отдельного поля, опционально).
      let themeLessonId: number | undefined;
      const num = quickTheme.trim();
      if (num) {
        if (quickThemeMatch) {
          themeLessonId = quickThemeMatch.id;
        } else {
          const created = await CurriculumService.createTheme({
            themeNumber: num,
            title: quickThemeTitle.trim() || undefined,
            disciplineId,
          });
          setThemes(prev => [...prev, created]);
          themeLessonId = created.id;
        }
      }
      await sourceRef.current.create({ position: nextPosition, kindOfStudy: quickKind, themeLessonId });
      // Вид оставляем — обычно добавляют подряд однотипные. Тему переносим на следующее
      // занятие (автоподстановка), КРОМЕ аттестаций (ЗО/ЗЧ/ЭКЗ) — у них темы нет.
      // Название темы всегда чистим: если номер сохранён и тема уже существует, её
      // название само подставится из quickThemeMatch (поле readonly).
      // Аттестации (ЗО/ЗЧ/ЭКЗ): у них нет «своей» темы лекции — тему не переносим. Что считать
      // аттестацией, решает бэк (KindOfStudy.Group), фронт своего списка видов не держит.
      if (getStudyCategory(quickKind) === 'ASSESSMENT') setQuickTheme('');
      setQuickThemeTitle('');
      await reload();
      onChanged?.();
    } catch {
      alert('Не удалось добавить занятие');
    } finally {
      setQuickAdding(false);
    }
  };

  // Сцепка соседних слотов (неразрывность): ищем пару в любом порядке.
  const chainBetween = (aId: number, bId: number) =>
    chains.find(c =>
      (c.slotA.id === aId && c.slotB.id === bId) ||
      (c.slotA.id === bId && c.slotB.id === aId));

  const toggleLink = async (slot: CurriculumSlotDto, next: CurriculumSlotDto) => {
    const existing = chainBetween(slot.id, next.id);
    try {
      if (existing) await sourceRef.current.unlink(existing.id);
      else await sourceRef.current.link(slot.id, next.id);
      await reload();
      onChanged?.();
    } catch {
      alert('Не удалось изменить сцепку');
    }
  };

  return (
    <div className="border-t border-blue-100 bg-blue-50/30">
      {/* Заголовок плана + добавление */}
      <div className="flex items-center justify-between px-8 py-2.5">
        <div className="flex items-center gap-2 text-xs font-bold text-slate-500 uppercase tracking-wider">
          <FileText size={12} />
          Учебный план
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-1 px-2.5 py-1.5 bg-blue-600 text-white text-xs font-bold rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={12} />
          Добавить занятие
        </button>
      </div>

      {slots.length > 0 && <SlotsSummary slots={slots} />}

      {/* Спиннер во весь список — только на первой загрузке. Обновления (сцепки,
          добавление/удаление) идут на месте, чтобы не размонтировать список и не
          сбрасывать скролл наверх. */}
      {loading && slots.length === 0 ? (
        <div className="flex items-center justify-center py-8 gap-2 text-slate-400 text-sm">
          <Loader2 size={16} className="animate-spin" />
          Загрузка...
        </div>
      ) : (
        <div className="px-8 pb-4">
          {slots.length === 0 && (
            <p className="text-center text-sm text-slate-400 py-4">
              Нет занятий — добавьте ниже или кнопкой «Добавить занятие».
            </p>
          )}

          {slots.map((slot, idx) => {
            const next = slots[idx + 1];
            const linkedToNext = next ? chainBetween(slot.id, next.id) : undefined;
            const linkedToPrev = idx > 0 ? chainBetween(slot.id, slots[idx - 1].id) : undefined;
            const inChain = linkedToNext || linkedToPrev;
            return (
              <React.Fragment key={slot.id}>
                <div
                  className={cn(
                    'relative flex items-center gap-3 bg-white border px-4 py-3 group hover:shadow-sm transition-all',
                    // Сцепленные соседи сливаются в один блок: убираем зазор и скругление/границу на стыке.
                    linkedToPrev ? 'mt-0 rounded-t-none border-t-0' : 'mt-1.5 rounded-t-xl',
                    linkedToNext ? 'rounded-b-none' : 'rounded-b-xl',
                    inChain ? 'border-indigo-200' : 'border-slate-200 hover:border-blue-200'
                  )}
                >
                  {/* Левая «скоба» вдоль звеньев цепочки (как в расписании) */}
                  {inChain && (
                    <div className={cn(
                      'absolute left-0 w-[2px] bg-indigo-400 z-10 pointer-events-none',
                      linkedToPrev ? 'top-0' : 'top-2 rounded-t-full',
                      linkedToNext ? 'bottom-0' : 'bottom-2 rounded-b-full'
                    )} />
                  )}
                  {/* Значок цепочки на стыке = кнопка размыкания */}
                  {linkedToNext && (
                    <button
                      type="button"
                      onClick={() => toggleLink(slot, next!)}
                      title="Разомкнуть сцепку (занятия не обязаны идти подряд)"
                      className="absolute left-0 bottom-0 -translate-x-1/2 translate-y-1/2 z-20 rounded-full bg-white ring-1 ring-indigo-300 p-0.5 hover:ring-indigo-500 transition-colors"
                    >
                      <Link2 size={12} className="text-indigo-600" />
                    </button>
                  )}

                  {/* Позиция */}
                  <div className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
                    <span className="text-xs font-black text-slate-500">{idx + 1}</span>
                  </div>

                  {/* Вид занятия */}
                  <span className={cn(
                    'shrink-0 text-xs font-semibold px-2 py-1 rounded-lg border',
                    KIND_COLORS[slot.kindOfStudy] || 'bg-slate-100 text-slate-600 border-slate-200'
                  )}>
                    {KIND_LABELS[slot.kindOfStudy] || slot.kindOfStudy}
                  </span>

                  {/* Тема */}
                  <div className="flex-1 min-w-0">
                    {slot.themeLesson ? (
                      <div className="flex items-baseline gap-2">
                        <span className="text-xs font-bold text-slate-400 shrink-0">
                          Т.{slot.themeLesson.themeNumber}
                        </span>
                        <span className="text-sm text-slate-700 truncate">
                          {slot.themeLesson.title}
                        </span>
                      </div>
                    ) : (
                      <span className="text-sm text-slate-300 italic">Тема не указана</span>
                    )}
                  </div>

                  {/* Требования к аудитории */}
                  <div className="shrink-0 flex items-center gap-2 text-xs">
                    {slot.requiredAuditorium && (
                      <span className="flex items-center gap-1 text-red-600 bg-red-50 px-2 py-0.5 rounded">
                        <School size={10} /> {slot.requiredAuditorium.name}
                      </span>
                    )}
                    {slot.priorityAuditorium && !slot.requiredAuditorium && (
                      <span className="flex items-center gap-1 text-blue-600 bg-blue-50 px-2 py-0.5 rounded">
                        <School size={10} /> {slot.priorityAuditorium.name}
                      </span>
                    )}
                  </div>

                  {/* Действия */}
                  <div className="shrink-0 flex items-center gap-1">
                    {/* Связать неразрывно со следующим — только когда ещё не связаны
                        (размыкание делается значком цепочки на стыке). */}
                    {next && !linkedToNext && (
                      <button
                        onClick={() => toggleLink(slot, next)}
                        className="p-1.5 rounded-lg text-slate-300 hover:text-indigo-600 hover:bg-indigo-50 opacity-0 group-hover:opacity-100 transition-colors"
                        title="Связать неразрывно со следующим занятием"
                      >
                        <Link2 size={13} />
                      </button>
                    )}
                    <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        onClick={() => openEdit(slot)}
                        className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        title="Редактировать"
                      >
                        <Edit2 size={13} />
                      </button>
                      <button
                        onClick={() => handleDuplicate(slot)}
                        disabled={duplicatingSlot === slot.id}
                        className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        title="Дублировать (создать идентичное следующее)"
                      >
                        {duplicatingSlot === slot.id
                          ? <Loader2 size={13} className="animate-spin text-blue-500" />
                          : <Copy size={13} />
                        }
                      </button>
                      <button
                        onClick={() => handleDelete(slot)}
                        disabled={deletingSlot === slot.id}
                        className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                        title="Удалить"
                      >
                        {deletingSlot === slot.id
                          ? <Loader2 size={13} className="animate-spin text-red-500" />
                          : <Trash2 size={13} />
                        }
                      </button>
                    </div>
                  </div>
                </div>
              </React.Fragment>
            );
          })}

          {/* Inline быстрое добавление в одну строку: вид (аббревиатура) + тема
              (выбрать печатанием или вписать новую) → занятие без модала. */}
          <div className="flex items-center gap-2 pt-1.5">
            <div className="w-7 h-7 rounded-lg bg-blue-50 flex items-center justify-center shrink-0">
              <Plus size={14} className="text-blue-500" />
            </div>
            <select
              value={quickKind}
              onChange={e => setQuickKind(e.target.value as KindOfStudy)}
              title="Вид занятия"
              className="shrink-0 w-20 text-xs font-semibold border border-slate-200 rounded-lg px-2 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Вид…</option>
              {kindsEnum.map(k => (
                <option key={k.value} value={k.value} title={k.label}>{k.abbreviation}</option>
              ))}
            </select>
            <input
              list={themeListId}
              value={quickTheme}
              onChange={e => setQuickTheme(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && quickKind && !quickAdding) handleQuickAdd(); }}
              placeholder="№ темы"
              title="Номер темы: выбрать существующую или вписать новый"
              className="shrink-0 w-24 text-xs border border-slate-200 rounded-lg px-2 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <datalist id={themeListId}>
              {themes.map(t => (
                <option key={t.id} value={t.themeNumber}>{t.title || '—'}</option>
              ))}
            </datalist>
            <input
              value={quickThemeMatch ? (quickThemeMatch.title ?? '') : quickThemeTitle}
              onChange={e => setQuickThemeTitle(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && quickKind && !quickAdding) handleQuickAdd(); }}
              placeholder="название (опц.)"
              disabled={!!quickThemeMatch}
              title={quickThemeMatch ? 'Тема уже существует — берётся её название' : 'Название новой темы (опционально)'}
              className="flex-1 min-w-0 text-xs border border-slate-200 rounded-lg px-2 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-slate-50 disabled:text-slate-400"
            />
            <button
              onClick={handleQuickAdd}
              disabled={!quickKind || quickAdding}
              className="shrink-0 flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-xs font-bold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {quickAdding ? <Loader2 size={12} className="animate-spin" /> : <Plus size={12} />}
              Добавить
            </button>
          </div>
        </div>
      )}

      {showForm && (
        <CurriculumSlotFormModal
          slot={selectedSlot}
          disciplineId={disciplineId}
          nextPosition={nextPosition}
          onClose={() => { setShowForm(false); setSelectedSlot(null); }}
          onSave={handleSave}
        />
      )}
    </div>
  );
};
