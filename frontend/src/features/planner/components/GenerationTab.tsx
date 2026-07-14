import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { DisciplineCourseDto, StudyPeriodDto } from '../../../types/api';
import { ScheduleSessionDto, CoursePlacementCountDto, EducatorPlacementCountDto } from '../../../types/cqrs';
import { CQRSService } from '../../../services/cqrsApiService';
import { useEnums } from '../../../context/EnumContext';
import {
  Calendar, Play, Loader2, Sparkles, Eraser, Info, RefreshCw,
  ChevronDown, ChevronRight, UserSquare2,
} from 'lucide-react';
import { cn } from '../../../utils/cn';

type KindMode = 'all' | 'lectures' | 'nonLectures';

/**
 * Вкладка генерации: полная генерация (перегенерация с сохранением замков) + инкрементальная
 * сборка по дисциплинам (аддитивная генерация одной дисциплины вокруг уже стоящего + очистка
 * «кроме замков» по видам/дисциплине/всему). Ядро операций — на бэке (session-scoped).
 */
export const GenerationTab: React.FC<{
  selectedCourses: Set<number>;
  allCourses: DisciplineCourseDto[];
  totalSlots: number;
  selectedPeriod: StudyPeriodDto | null;
  onGenerate: (courseIds: number[], period: StudyPeriodDto) => void;
  isGenerating: boolean;
}> = ({ selectedCourses, allCourses, totalSlots, selectedPeriod, onGenerate, isGenerating }) => {
  const { kindOfStudy } = useEnums();
  const [session, setSession] = useState<ScheduleSessionDto | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  // Счётчики «распределено N/M» по курсам (обновляются после генерации/очистки).
  const [counts, setCounts] = useState<Map<number, CoursePlacementCountDto>>(new Map());
  // Раскрытые дисциплины: внутри — преподаватели с теми же генерацией и очисткой в своём охвате.
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  // Сессия периода — для инкрементальных операций (та же, что в «Расписании» планировщика).
  useEffect(() => {
    if (!selectedPeriod) { setSession(null); return; }
    let cancelled = false;
    CQRSService.getSessionForPeriod(selectedPeriod.id)
      .then((s) => { if (!cancelled) setSession(s); })
      .catch(() => { if (!cancelled) setSession(null); });
    return () => { cancelled = true; };
  }, [selectedPeriod?.id]);

  const courses = useMemo(
    () => allCourses.filter((c) => selectedCourses.has(c.id)),
    [allCourses, selectedCourses]
  );
  const nonLectureKinds = useMemo(
    () => kindOfStudy.map((k) => k.value).filter((v) => v !== 'LECTURE'),
    [kindOfStudy]
  );

  // Счётчики размещённости выбранных курсов; перезапрашиваются после каждой операции.
  const loadCounts = useCallback(async () => {
    if (!session || selectedCourses.size === 0) { setCounts(new Map()); return; }
    try {
      const list = await CQRSService.getPlacementCounts(session.id, Array.from(selectedCourses));
      setCounts(new Map(list.map((c) => [c.courseId, c])));
    } catch { /* счётчик не критичен */ }
  }, [session, selectedCourses]);

  useEffect(() => { loadCounts(); }, [loadCounts]);

  const run = async (key: string, fn: () => Promise<void>) => {
    if (!session || busy) return;
    setBusy(key); setMessage(null);
    try { await fn(); await loadCounts(); }
    catch (e: any) {
      console.error(e);
      // 409 «устаревшая версия»: расписание изменили параллельно (соседняя вкладка/другой
      // пользователь). Подхватываем актуальную версию из тела — иначе вкладка залипнет на старой
      // и каждая следующая генерация/очистка будет отвергнута до F5.
      const body = e?.response?.data;
      if (e?.response?.status === 409 && body?.error === 'CONFLICT') {
        if (body.currentVersion != null) {
          setSession((s) => (s ? { ...s, version: body.currentVersion } : s));
        }
        setMessage('⚠️ Расписание изменено параллельно. Данные обновлены — повторите операцию.');
      } else {
        setMessage('❌ Ошибка операции');
      }
    }
    finally { setBusy(null); }
  };

  const kindsFor = (mode: KindMode): string[] | undefined =>
    mode === 'all' ? undefined : mode === 'lectures' ? ['LECTURE'] : nonLectureKinds;
  const modeLabel = (mode: KindMode) => (mode === 'all' ? 'всё' : mode === 'lectures' ? 'лекции' : 'практики');

  // Ключ «занятости» строки: у дисциплины и у каждого её преподавателя он свой, чтобы спиннер
  // и блокировка кнопок относились ровно к той строке, по которой кликнули.
  const rowKey = (op: 'gen' | 'clr', courseId: number, educatorId?: number) =>
    `${op}-${courseId}${educatorId != null ? `-e${educatorId}` : ''}`;
  const rowBusyFor = (courseId: number, educatorId?: number) =>
    busy === rowKey('gen', courseId, educatorId) || busy === rowKey('clr', courseId, educatorId);

  const toggleExpanded = (courseId: number) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(courseId)) next.delete(courseId); else next.add(courseId);
      return next;
    });

  // Генерация/очистка дисциплины. `educator` (опц.) сужает охват до одного преподавателя —
  // те же две операции, что и на всей дисциплине, просто в его наборе занятий.
  // Генерация и очистка поднимают версию сессии — подхватываем её из ответа. Вкладка держит
  // сессию в своём состоянии и раньше не освежала её никогда: пока версия не росла, хватало id.
  const genCourse = (c: DisciplineCourseDto, mode: KindMode, educator?: EducatorPlacementCountDto) =>
    run(rowKey('gen', c.id, educator?.educatorId), async () => {
      setSession(await CQRSService.generateCourse(
        session!.id, selectedPeriod!.id, c.id, kindsFor(mode),
        educator ? [educator.educatorId] : undefined, session!.version));
      setMessage(`✅ «${c.discipline.name}»${educator ? ` · ${educator.educatorName}` : ''} · ${modeLabel(mode)}: разложено (аддитивно, вокруг стоящего)`);
    });

  const clearCourse = (c: DisciplineCourseDto, mode: KindMode, educator?: EducatorPlacementCountDto) =>
    run(rowKey('clr', c.id, educator?.educatorId), async () => {
      const { removed, session: updated } = await CQRSService.clearPlacements(session!.id, {
        courseId: c.id,
        kinds: kindsFor(mode),
        educatorIds: educator ? [educator.educatorId] : undefined,
        version: session!.version,
      });
      setSession(updated);
      setMessage(`🧹 «${c.discipline.name}»${educator ? ` · ${educator.educatorName}` : ''} · ${modeLabel(mode)}: удалено ${removed} (кроме замков)`);
    });

  const clearAll = () => run('clr-all', async () => {
    const { removed, session: updated } = await CQRSService.clearPlacements(session!.id, {
      version: session!.version,
    });
    setSession(updated);
    setMessage(`🧹 Очищено всё расписание: удалено ${removed} (кроме замков)`);
  });

  // Ремонт отображения: переписывает преподавателя/группу/тему в сетке из текущих назначений,
  // не двигая занятия. Нужен для расписаний, собранных до автоперепроекции правок назначений.
  const reproject = () => run('reproject', async () => {
    const n = await CQRSService.reproject(session!.id);
    setMessage(`♻️ Отображение пересобрано из назначений: ${n} занятий`);
  });

  if (!selectedPeriod) {
    return (
      <div className="py-16 text-center text-slate-400 text-sm">
        <Calendar className="mx-auto mb-3 opacity-20" size={36} />
        <p>Выберите учебный период вверху страницы</p>
      </div>
    );
  }
  if (selectedCourses.size === 0) {
    return (
      <div className="py-16 text-center text-slate-400 text-sm">
        <Calendar className="mx-auto mb-3 opacity-20" size={36} />
        <p>Выберите курсы во вкладке «Курсы»</p>
      </div>
    );
  }

  return (
    <div className="p-5 space-y-5">
      {/* Полная генерация */}
      <div className="p-4 bg-blue-50 border border-blue-100 rounded-xl space-y-3">
        <div className="text-sm text-blue-900">
          Период: <span className="font-bold">{selectedPeriod.name}</span>
          <span className="text-blue-500"> · {selectedPeriod.startDate} — {selectedPeriod.endDate}</span>
          {' · '}<span className="font-bold">{selectedCourses.size}</span> курс.
          {' · '}<span className="font-bold">{totalSlots}</span> занятий
        </div>
        <button
          onClick={() => onGenerate(Array.from(selectedCourses), selectedPeriod)}
          disabled={isGenerating}
          className="w-full py-2.5 bg-blue-600 text-white font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors text-sm"
        >
          <Play size={16} />
          {isGenerating ? 'Генерация расписания...' : 'Сгенерировать всё (сохранив замки)'}
        </button>
      </div>

      {/* Инкрементальная сборка по дисциплинам */}
      <div className="space-y-2">
        <div className="flex items-center gap-1.5 text-xs font-black uppercase tracking-tight text-slate-500">
          <Sparkles size={13} /> Инкрементально по дисциплинам
        </div>
        <p className="text-[11px] text-slate-400 flex items-start gap-1.5">
          <Info size={13} className="shrink-0 mt-0.5" />
          Генерация раскладывает только неразмещённые занятия вокруг уже стоящего (ничего не двигая);
          «всё» учитывает равномерность и интервалы между лекциями — рекомендуется. Очистка не трогает
          закреплённые (замок). Поток: сгенерировать всё → очистить практики → поправить лекции вручную →
          сгенерировать практики. Результат — во вкладке «Расписание».
          {' '}Дисциплина раскрывается в список преподавателей — те же генерация и очистка доступны
          в охвате одного из них. Но помните: чем уже охват, тем меньше «кругозор» распределителя —
          равномерность он считает только по взятым занятиям, остальные для него неподвижны.
        </p>

        {!session && (
          <div className="text-xs text-slate-400 flex items-center gap-2 px-1 py-2">
            <Loader2 size={13} className="animate-spin" /> Открываю сессию периода…
          </div>
        )}

        {courses.map((c) => {
          const cnt = counts.get(c.id);
          const done = cnt && cnt.total > 0 && cnt.placed >= cnt.total;
          const rowBusy = rowBusyFor(c.id);
          const isOpen = expanded.has(c.id);
          const educators = cnt?.educators ?? [];
          return (
            <div key={c.id} className="bg-white border border-slate-200 rounded-lg px-3 py-2 space-y-1.5">
              {/* Заголовок дисциплины: раскрывается в список преподавателей */}
              <button
                type="button"
                onClick={() => toggleExpanded(c.id)}
                disabled={educators.length === 0}
                className="w-full flex items-center gap-1.5 text-sm font-semibold text-slate-800 text-left disabled:cursor-default"
              >
                {busy === rowKey('gen', c.id)
                  ? <Loader2 size={12} className="animate-spin text-blue-500 shrink-0" />
                  : educators.length > 0
                    ? (isOpen ? <ChevronDown size={13} className="text-slate-400 shrink-0" /> : <ChevronRight size={13} className="text-slate-400 shrink-0" />)
                    : <span className="w-[13px] shrink-0" />}
                <span className="truncate">{c.discipline.name}</span>
                <span className="text-slate-400 text-xs font-normal shrink-0">· сем. {c.semester}</span>
                {cnt && (
                  <span className={cn(
                    'ml-auto shrink-0 text-[11px] font-bold tabular-nums',
                    done ? 'text-emerald-600' : cnt.placed > 0 ? 'text-blue-600' : 'text-slate-400'
                  )}>
                    распределено {cnt.placed}/{cnt.total}
                  </span>
                )}
              </button>

              <ScopeActions
                disabled={!session || rowBusy}
                onGenerate={(mode) => genCourse(c, mode)}
                onClear={(mode) => clearCourse(c, mode)}
              />

              {/* Преподаватели дисциплины: те же операции, но в охвате одного преподавателя.
                  Совместное занятие двух преподавателей попадает в охват каждого — поэтому
                  сумма по строкам может превышать счётчик дисциплины. */}
              {isOpen && educators.length > 0 && (
                <div className="pt-1 mt-1 border-t border-slate-100 space-y-1.5">
                  {educators.map((e) => {
                    const eBusy = rowBusyFor(c.id, e.educatorId);
                    const eDone = e.total > 0 && e.placed >= e.total;
                    return (
                      <div key={e.educatorId} className="pl-4 space-y-1">
                        <div className="flex items-center gap-1.5 text-xs text-slate-600">
                          {busy === rowKey('gen', c.id, e.educatorId) && (
                            <Loader2 size={11} className="animate-spin text-blue-500 shrink-0" />
                          )}
                          <UserSquare2 size={11} className="text-slate-300 shrink-0" />
                          <span className="truncate font-medium">{e.educatorName}</span>
                          <span className={cn(
                            'ml-auto shrink-0 text-[10px] font-bold tabular-nums',
                            eDone ? 'text-emerald-600' : e.placed > 0 ? 'text-blue-600' : 'text-slate-400'
                          )}>
                            {e.placed}/{e.total}
                          </span>
                        </div>
                        <ScopeActions
                          compact
                          disabled={!session || eBusy}
                          onGenerate={(mode) => genCourse(c, mode, e)}
                          onClear={(mode) => clearCourse(c, mode, e)}
                        />
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}

        <button
          onClick={clearAll}
          disabled={!session || busy !== null}
          className="w-full mt-1 py-2 border border-red-200 text-red-600 bg-white font-semibold rounded-xl hover:bg-red-50 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors text-xs"
        >
          {busy === 'clr-all' ? <Loader2 size={14} className="animate-spin" /> : <Eraser size={14} />}
          Очистить всё расписание (кроме замков)
        </button>

        <button
          onClick={reproject}
          disabled={!session || busy !== null}
          title="Переписать преподавателя/группу/тему в стоящих занятиях из текущих назначений, не двигая расписание"
          className="w-full py-2 border border-slate-200 text-slate-600 bg-white font-semibold rounded-xl hover:bg-slate-50 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors text-xs"
        >
          {busy === 'reproject' ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
          Обновить отображение из назначений (не двигая занятия)
        </button>

        {message && <p className="text-xs text-center text-slate-500 pt-1">{message}</p>}
      </div>
    </div>
  );
};

/**
 * Строка действий охвата: «Сген: всё / лекции / практики │ Очист: всё / лекции / практики».
 * Один компонент на оба уровня (дисциплина и преподаватель внутри неё) — набор операций у них
 * одинаков, меняется только охват, который знает вызывающий (DRY: раньше разметка была бы дублем).
 */
const ScopeActions: React.FC<{
  disabled: boolean;
  compact?: boolean;
  onGenerate: (mode: KindMode) => void;
  onClear: (mode: KindMode) => void;
}> = ({ disabled, compact, onGenerate, onClear }) => (
  <div className={cn('flex items-center gap-1.5 flex-wrap', compact ? 'text-[10px]' : 'text-[11px]')}>
    <span className="text-slate-400 shrink-0">Сген:</span>
    <ModeBtn label="всё" tone="blue" onClick={() => onGenerate('all')} disabled={disabled} />
    <ModeBtn label="лекции" tone="blue" onClick={() => onGenerate('lectures')} disabled={disabled} />
    <ModeBtn label="практики" tone="blue" onClick={() => onGenerate('nonLectures')} disabled={disabled} />
    <span className="mx-0.5 text-slate-200 select-none">│</span>
    <span className="text-slate-400 shrink-0">Очист:</span>
    <ModeBtn label="всё" tone="red" onClick={() => onClear('all')} disabled={disabled} />
    <ModeBtn label="лекции" tone="red" onClick={() => onClear('lectures')} disabled={disabled} />
    <ModeBtn label="практики" tone="red" onClick={() => onClear('nonLectures')} disabled={disabled} />
  </div>
);

const ModeBtn: React.FC<{ label: string; tone: 'blue' | 'red'; onClick: () => void; disabled?: boolean }> = ({ label, tone, onClick, disabled }) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={cn(
      'px-2 py-1 rounded-md font-bold border transition-colors disabled:opacity-40',
      tone === 'blue'
        ? 'text-blue-600 border-blue-200 hover:bg-blue-50'
        : 'text-slate-500 border-slate-200 hover:bg-red-50 hover:text-red-600 hover:border-red-200'
    )}
  >
    {label}
  </button>
);
