import React, { useEffect, useMemo, useState } from 'react';
import { eachDayOfInterval, getDay, parseISO } from 'date-fns';
import { cn } from '../../../utils/cn';
import { Card } from '../../../components/ui/Card';
import { ScheduleService } from '../../../services/apiServices';
import { ScheduleResultDto, PeriodReadinessDto, PeriodScheduleQualityDto, TimeSlotPair } from '../../../types/api';
import { usePeriod } from '../../period/PeriodContext';
import {
  Users, School, BookOpen, Layers, Loader2, CalendarRange,
  AlertTriangle, CalendarClock, ArrowRight, Info
} from 'lucide-react';

interface DashboardProps {
  stats: {
    educators: number;
    auditoriums: number;
    groups: number;
    disciplines: number;
  };
  // Навигация по разделам (быстрые действия). Прокидывается из App (setActiveTab).
  onNavigate?: (tab: 'planner' | 'schedule') => void;
}

const SLOTS_13: ReadonlySet<TimeSlotPair> = new Set<TimeSlotPair>(['FIRST', 'SECOND', 'THIRD']);

interface GroupDensity { group: string; occ13: number; inFourth: number; free13: number; total: number; }

/**
 * Дашборд «Готовность периода». Все метрики считаются НА ФРОНТЕ из одного запроса
 * `ScheduleService.loadExisting(активный период)` — сколько занятий размещено/не
 * размещено, плотность групп в парах 1–3 (сколько свободно / уже в 4-й паре) и
 * субботняя нагрузка по преподавателям (для равномерного распределения).
 *
 * ВАЖНО (тех-долг, см. docs/FOLLOWUPS.md): ёмкость 1–3 здесь считается упрощённо
 * (рабочие дни Пн–Сб × 3), без учёта закрытых бэком дней/пар (ScheduleDaysSlotsConfig)
 * и без вычитания ограничений сущностей. Для точности эти агрегаты должны переехать
 * на бэк отдельными эндпоинтами.
 */
export const Dashboard: React.FC<DashboardProps> = ({ stats, onNavigate }) => {
  // Учебный период — из общего контекста (единый выбор в шапке приложения).
  const { periods, selectedPeriodId, selectedPeriod: period, loading: periodsLoading } = usePeriod();
  const [result, setResult] = useState<ScheduleResultDto | null>(null);
  const [readiness, setReadiness] = useState<PeriodReadinessDto | null>(null);
  const [quality, setQuality] = useState<PeriodScheduleQualityDto | null>(null);
  const [loading, setLoading] = useState(true);

  // Данные выбранного периода — перезагружаются при смене периода.
  // Размещённое расписание (для плотности/суббот) + готовность (всего/размещено/не размещено).
  useEffect(() => {
    if (!period) { setResult(null); setReadiness(null); setQuality(null); setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    Promise.all([
      ScheduleService.loadExisting(period.startDate, period.endDate),
      ScheduleService.getReadiness(period.id),
      ScheduleService.getEducatorQuality(period.id),
    ])
      .then(([res, rd, q]) => {
        if (cancelled) return;
        setResult(res);
        setReadiness(rd);
        setQuality(q);
      })
      .catch((e) => {
        console.error('Дашборд: не удалось загрузить данные периода:', e);
        if (!cancelled) { setResult(null); setReadiness(null); setQuality(null); }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [selectedPeriodId, period?.startDate, period?.endDate]);

  const metrics = useMemo(() => {
    if (!result || !period) return null;
    const lessons = result.lessons;

    // Рабочие дни периода Пн–Сб (getDay: 0=Вс … 6=Сб). Воскресенья не учебные.
    const workingDays = eachDayOfInterval({
      start: parseISO(period.startDate),
      end: parseISO(period.endDate),
    }).filter((d) => { const g = getDay(d); return g >= 1 && g <= 6; }).length;

    const capacity13 = workingDays * 3; // ячеек «1–3 пара» на одну сущность за период

    // Плотность групп в парах 1–3.
    const groupSet = new Set<string>();
    lessons.forEach((l) => l.groupNames.forEach((g) => groupSet.add(g)));
    const groupStats: GroupDensity[] = Array.from(groupSet).map((group) => {
      const grp = lessons.filter((l) => l.groupNames.includes(group));
      const occ13 = grp.filter((l) => SLOTS_13.has(l.timeSlotPair)).length;
      const inFourth = grp.filter((l) => l.timeSlotPair === 'FOURTH').length;
      return { group, occ13, inFourth, free13: capacity13 - occ13, total: grp.length };
    }).sort((a, b) => a.free13 - b.free13); // самые «забитые» сверху

    // Субботняя нагрузка преподавателей больше НЕ считается здесь — она уехала на бэк
    // (единый отчёт качества расписания преподавателей, см. getEducatorQuality).
    return { workingDays, capacity13, groupStats };
  }, [result, period]);

  const readyPct = readiness && readiness.total > 0
    ? Math.round((readiness.placed / readiness.total) * 100)
    : 0;

  // Только преподаватели с флагом компактности (для остальных это не приоритет). Уже
  // отсортированы бэком: компактные первыми, худшие (больший штраф) сверху.
  const compactEducators = quality?.educators.filter((e) => e.compact) ?? [];

  if (periodsLoading) {
    return (
      <div className="flex items-center justify-center h-96 text-slate-400 gap-3">
        <Loader2 className="animate-spin" size={20} />
        <span className="text-sm font-medium">Загрузка…</span>
      </div>
    );
  }

  if (periods.length === 0) {
    return (
      <EmptyState
        title="Нет учебных периодов"
        subtitle="Создайте учебный период в планировщике"
        onNavigate={onNavigate}
      />
    );
  }

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      {/* Контекст периода */}
      <div className="flex flex-wrap items-center gap-3 bg-white rounded-xl border border-slate-200 px-4 py-3">
        <div className="p-2 bg-blue-600 rounded-lg text-white shrink-0"><CalendarRange size={18} /></div>
        <div className="flex items-center gap-2 min-w-0">
          <span className="max-w-[240px] text-sm font-black text-slate-900 truncate">
            {period ? `${period.name} (${period.studyYear})` : '— период не выбран —'}
          </span>
          {period && (
            <span className="text-[11px] text-slate-400 font-medium hidden sm:inline">
              {period.startDate} — {period.endDate}
              {metrics && <> · {metrics.workingDays} дн. (Пн–Сб)</>}
            </span>
          )}
          {loading && <Loader2 size={14} className="animate-spin text-blue-600" />}
        </div>
        {onNavigate && (
          <div className="flex items-center gap-2 ml-auto">
            <button
              onClick={() => onNavigate('planner')}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
            >
              Планировщик <ArrowRight size={14} />
            </button>
            <button
              onClick={() => onNavigate('schedule')}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-white text-blue-600 border border-blue-200 text-xs font-semibold rounded-lg hover:bg-blue-50 transition-colors"
            >
              Расписание
            </button>
          </div>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64 text-slate-400 gap-3">
          <Loader2 className="animate-spin" size={20} />
          <span className="text-sm font-medium">Загрузка расписания периода…</span>
        </div>
      ) : (
      <>
      {/* KPI готовности расписания — «всего» из набора генерации бэка (не query-сторона) */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Kpi
          label="Не размещено"
          value={readiness?.unplaced ?? 0}
          tone={(readiness?.unplaced ?? 0) > 0 ? 'red' : 'emerald'}
          icon={AlertTriangle}
          hint={(readiness?.unplaced ?? 0) > 0 ? 'требуют места' : 'всё размещено'}
        />
        <Kpi label="Размещено" value={readiness?.placed ?? 0} tone="blue" icon={CalendarClock}
             hint={`из ${readiness?.total ?? 0} · ${readyPct}%`} />
        <Kpi label="Всего к размещению" value={readiness?.total ?? 0} tone="slate" icon={Layers}
             hint="набор периода" />
        <Kpi label="Учебных дней" value={metrics?.workingDays ?? 0} tone="slate" icon={CalendarRange} hint="Пн–Сб" />
      </div>

      {!result || result.lessons.length === 0 ? (
        <EmptyState
          title="За период нет размещённого расписания"
          subtitle="Сформируйте план и запустите генерацию в планировщике"
          onNavigate={onNavigate}
        />
      ) : (
        <div className="space-y-6">
          {/* Плотность групп 1–3 */}
          <Card title="Плотность групп (пары 1–3)">
            <div className="space-y-3">
              <p className="text-[11px] text-slate-400 flex items-start gap-1.5">
                <Info size={13} className="shrink-0 mt-0.5" />
                Ёмкость 1–3 = {metrics?.capacity13 ?? 0} пар/группу (Пн–Сб × 3). Оценка без учёта
                ограничений и закрытых пар — точный расчёт см. тех-долг.
              </p>
              <div className="max-h-[320px] overflow-auto custom-scrollbar -mx-1 px-1">
                <table className="w-full text-xs">
                  <thead className="text-[10px] uppercase tracking-wide text-slate-400">
                    <tr className="border-b border-slate-100">
                      <th className="text-left font-bold py-1.5">Группа</th>
                      <th className="text-right font-bold px-2">Своб. 1–3</th>
                      <th className="text-right font-bold px-2">Занято 1–3</th>
                      <th className="text-right font-bold pl-2">В 4-й</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {metrics?.groupStats.map((g) => {
                      const tight = g.free13 <= 0;
                      return (
                        <tr key={g.group} className={cn('transition-colors', tight && 'bg-red-50/60')}>
                          <td className="py-1.5 font-bold text-slate-700 truncate max-w-[120px]">{g.group}</td>
                          <td className={cn('text-right px-2 font-black tabular-nums', tight ? 'text-red-600' : 'text-emerald-600')}>
                            {g.free13}
                          </td>
                          <td className="text-right px-2 tabular-nums text-slate-500">{g.occ13}</td>
                          <td className={cn('text-right pl-2 tabular-nums font-bold', g.inFourth > 0 ? 'text-amber-600' : 'text-slate-300')}>
                            {g.inFourth}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </Card>

          {/* Преподаватели: компактность + равномерность (единый отчёт с бэка) */}
          {compactEducators.length > 0 && (
            <Card title="Преподаватели: компактность и нагрузка">
              <div className="space-y-3">
                <div className="flex flex-wrap gap-2 text-[11px]">
                  <Chip label="Плотно уложены" value={`${quality!.wellPacked}/${quality!.compactEducators}`} tone="emerald" />
                  <Chip label="Ср. штраф" value={quality!.avgPenalty} tone="slate" />
                  <Chip label="Одиночных дней" value={quality!.totalSinglePairDays} tone="amber" />
                  <Chip label="Окон" value={quality!.totalWindowSlots} tone="red" />
                  <Chip label="Ср. суббота" value={quality!.avgSaturday.toFixed(1)} tone="slate" />
                </div>
                <p className="text-[11px] text-slate-400 flex items-start gap-1.5">
                  <Info size={13} className="shrink-0 mt-0.5" />
                  Штраф = окна + 2·одиночные дни + лишние дни (меньше — плотнее; суббота в штраф не входит).
                  Цель — 2–3 пары в учебный день без окон. Флаговые преподаватели, худшие сверху.
                </p>
                <div className="max-h-[360px] overflow-auto custom-scrollbar -mx-1 px-1">
                  <table className="w-full text-xs">
                    <thead className="text-[10px] uppercase tracking-wide text-slate-400">
                      <tr className="border-b border-slate-100">
                        <th className="text-left font-bold py-1.5">Преподаватель</th>
                        <th className="text-right font-bold px-2">Пар/день</th>
                        <th className="text-right font-bold px-2">Одиноч.</th>
                        <th className="text-right font-bold px-2">Окна</th>
                        <th className="text-right font-bold px-2">Суббот</th>
                        <th className="text-right font-bold px-2">± ср.</th>
                        <th className="text-right font-bold pl-2">Штраф</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-50">
                      {compactEducators.map((e) => {
                        const hot = e.windowSlots > 0 || e.singlePairDays >= 5;
                        return (
                          <tr key={e.educatorId} className={cn('transition-colors', hot && 'bg-red-50/60')}>
                            <td className="py-1.5 font-bold text-slate-700 truncate max-w-[160px]">{e.educatorName}</td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{e.avgPairsPerDay.toFixed(1)}</td>
                            <td className={cn('text-right px-2 tabular-nums font-bold', e.singlePairDays > 0 ? 'text-amber-600' : 'text-slate-300')}>
                              {e.singlePairDays}
                            </td>
                            <td className={cn('text-right px-2 tabular-nums font-bold', e.windowSlots > 0 ? 'text-red-600' : 'text-slate-300')}>
                              {e.windowSlots}
                            </td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{e.saturdayPairs}</td>
                            <td className={cn('text-right px-2 tabular-nums font-bold',
                              e.saturdayDeviation > 0.05 ? 'text-red-500' : e.saturdayDeviation < -0.05 ? 'text-emerald-500' : 'text-slate-300')}>
                              {e.saturdayDeviation > 0 ? '+' : ''}{e.saturdayDeviation.toFixed(1)}
                            </td>
                            <td className={cn('text-right pl-2 tabular-nums font-black', e.penalty === 0 ? 'text-emerald-600' : hot ? 'text-red-600' : 'text-slate-600')}>
                              {e.penalty}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </Card>
          )}
        </div>
      )}
      </>
      )}

      {/* Инвентарь системы (реальные счётчики) */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Inventory label="Преподаватели" value={stats.educators} icon={Users} />
        <Inventory label="Аудитории" value={stats.auditoriums} icon={School} />
        <Inventory label="Группы" value={stats.groups} icon={Users} />
        <Inventory label="Дисциплины" value={stats.disciplines} icon={BookOpen} />
      </div>
    </div>
  );
};

const TONES: Record<string, string> = {
  blue: 'text-blue-600 bg-blue-50',
  emerald: 'text-emerald-600 bg-emerald-50',
  red: 'text-red-600 bg-red-50',
  amber: 'text-amber-600 bg-amber-50',
  slate: 'text-slate-600 bg-slate-100',
};

const Kpi = ({ label, value, tone, icon: Icon, hint }: {
  label: string; value: React.ReactNode; tone: keyof typeof TONES | string; icon: React.ElementType; hint?: string;
}) => (
  <Card className="p-4">
    <div className="flex items-start justify-between gap-2">
      <div className="min-w-0">
        <p className="text-3xl font-black text-slate-900 tabular-nums leading-none">{value}</p>
        <p className="text-xs font-bold text-slate-500 mt-1.5">{label}</p>
        {hint && <p className="text-[10px] text-slate-400 mt-0.5">{hint}</p>}
      </div>
      <div className={cn('p-2 rounded-xl shrink-0', TONES[tone] ?? TONES.slate)}>
        <Icon size={18} />
      </div>
    </div>
  </Card>
);

const Chip = ({ label, value, tone }: { label: string; value: React.ReactNode; tone: keyof typeof TONES | string }) => (
  <span className={cn('inline-flex items-center gap-1 px-2 py-1 rounded-lg font-bold', TONES[tone] ?? TONES.slate)}>
    {label}: <span className="font-black tabular-nums">{value}</span>
  </span>
);

const Inventory = ({ label, value, icon: Icon }: { label: string; value: number; icon: React.ElementType }) => (
  <div className="flex items-center gap-3 bg-white rounded-xl border border-slate-100 px-4 py-3">
    <div className="p-2 rounded-lg bg-slate-100 text-slate-500 shrink-0"><Icon size={16} /></div>
    <div>
      <p className="text-xl font-black text-slate-900 tabular-nums leading-none">{value}</p>
      <p className="text-[11px] font-semibold text-slate-400 mt-0.5">{label}</p>
    </div>
  </div>
);

const EmptyState = ({ title, subtitle, onNavigate }: {
  title: string; subtitle: string; onNavigate?: (tab: 'planner' | 'schedule') => void;
}) => (
  <div className="flex flex-col items-center justify-center h-72 gap-3 border-2 border-dashed border-slate-200 rounded-2xl bg-white text-center px-6">
    <div className="w-12 h-12 bg-amber-50 rounded-2xl flex items-center justify-center">
      <CalendarClock size={24} className="text-amber-400" />
    </div>
    <div>
      <p className="font-black text-slate-700">{title}</p>
      <p className="text-sm text-slate-400 mt-1">{subtitle}</p>
    </div>
    {onNavigate && (
      <button
        onClick={() => onNavigate('planner')}
        className="mt-1 flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
      >
        В планировщик <ArrowRight size={14} />
      </button>
    )}
  </div>
);
