import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { DisciplineCourseDto, StudyPeriodDto } from '../../../types/api';
import { ScheduleSessionDto, CoursePlacementCountDto } from '../../../types/cqrs';
import { CQRSService } from '../../../services/cqrsApiService';
import { useEnums } from '../../../context/EnumContext';
import { Calendar, Play, Loader2, Sparkles, Eraser, Info, RefreshCw } from 'lucide-react';
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
    catch (e) { console.error(e); setMessage('❌ Ошибка операции'); }
    finally { setBusy(null); }
  };

  const kindsFor = (mode: KindMode): string[] | undefined =>
    mode === 'all' ? undefined : mode === 'lectures' ? ['LECTURE'] : nonLectureKinds;
  const modeLabel = (mode: KindMode) => (mode === 'all' ? 'всё' : mode === 'lectures' ? 'лекции' : 'практики');

  const genCourse = (c: DisciplineCourseDto, mode: KindMode) => run(`gen-${c.id}`, async () => {
    await CQRSService.generateCourse(session!.id, selectedPeriod!.id, c.id, kindsFor(mode));
    setMessage(`✅ «${c.discipline.name}» · ${modeLabel(mode)}: разложено (аддитивно, вокруг стоящего)`);
  });

  const clearCourse = (c: DisciplineCourseDto, mode: KindMode) => run(`clr-${c.id}`, async () => {
    const n = await CQRSService.clearPlacements(session!.id, { courseId: c.id, kinds: kindsFor(mode) });
    setMessage(`🧹 «${c.discipline.name}» · ${modeLabel(mode)}: удалено ${n} (кроме замков)`);
  });

  const clearAll = () => run('clr-all', async () => {
    const n = await CQRSService.clearPlacements(session!.id, {});
    setMessage(`🧹 Очищено всё расписание: удалено ${n} (кроме замков)`);
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
        </p>

        {!session && (
          <div className="text-xs text-slate-400 flex items-center gap-2 px-1 py-2">
            <Loader2 size={13} className="animate-spin" /> Открываю сессию периода…
          </div>
        )}

        {courses.map((c) => {
          const genBusy = busy === `gen-${c.id}`;
          const rowBusy = genBusy || busy === `clr-${c.id}`;
          const cnt = counts.get(c.id);
          const done = cnt && cnt.total > 0 && cnt.placed >= cnt.total;
          return (
            <div key={c.id} className="bg-white border border-slate-200 rounded-lg px-3 py-2 space-y-1.5">
              <div className="flex items-center gap-1.5 text-sm font-semibold text-slate-800">
                {genBusy && <Loader2 size={12} className="animate-spin text-blue-500 shrink-0" />}
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
              </div>
              <div className="flex items-center gap-1.5 flex-wrap text-[11px]">
                <span className="text-slate-400 shrink-0">Сген:</span>
                <ModeBtn label="всё" tone="blue" onClick={() => genCourse(c, 'all')} disabled={!session || rowBusy} />
                <ModeBtn label="лекции" tone="blue" onClick={() => genCourse(c, 'lectures')} disabled={!session || rowBusy} />
                <ModeBtn label="практики" tone="blue" onClick={() => genCourse(c, 'nonLectures')} disabled={!session || rowBusy} />
                <span className="mx-0.5 text-slate-200 select-none">│</span>
                <span className="text-slate-400 shrink-0">Очист:</span>
                <ModeBtn label="всё" tone="red" onClick={() => clearCourse(c, 'all')} disabled={!session || rowBusy} />
                <ModeBtn label="лекции" tone="red" onClick={() => clearCourse(c, 'lectures')} disabled={!session || rowBusy} />
                <ModeBtn label="практики" tone="red" onClick={() => clearCourse(c, 'nonLectures')} disabled={!session || rowBusy} />
              </div>
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
