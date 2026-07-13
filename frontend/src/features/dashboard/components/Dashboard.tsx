import React, { useEffect, useMemo, useState } from 'react';
import { eachDayOfInterval, getDay, parseISO } from 'date-fns';
import { cn } from '../../../utils/cn';
import { Card } from '../../../components/ui/Card';
import { ScheduleService } from '../../../services/apiServices';
import { CQRSService } from '../../../services/cqrsApiService';
import {
  PeriodReadinessDto, PeriodScheduleQualityDto, GroupDensityDto, ExportAxis, ProjectionHealthDto
} from '../../../types/api';
import { usePeriod } from '../../period/PeriodContext';
import {
  Users, School, BookOpen, Layers, Loader2, CalendarRange,
  AlertTriangle, CalendarClock, ArrowRight, Info, FileSpreadsheet, RefreshCw
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

/**
 * Дашборд «Готовность периода». Метрики берутся из бэковых отчётов по активному периоду:
 * готовность (всего/размещено/не размещено), плотность групп в парах 1–3 (спрос, занято,
 * остаток и ЧЕСТНАЯ свободная ёмкость) и качество расписания преподавателей.
 *
 * Плотность 1–3 раньше считалась на фронте упрощённо (Пн–Сб × 3, без ограничений); теперь
 * ёмкость честная и живёт на бэке (getGroupDensity): учтены закрытые пары
 * (ScheduleDaysSlotsConfig) и групповые ограничения. На фронте — только рабочие дни для шапки.
 */
export const Dashboard: React.FC<DashboardProps> = ({ stats, onNavigate }) => {
  // Учебный период — из общего контекста (единый выбор в шапке приложения).
  const { periods, selectedPeriodId, selectedPeriod: period, loading: periodsLoading } = usePeriod();
  const [readiness, setReadiness] = useState<PeriodReadinessDto | null>(null);
  const [quality, setQuality] = useState<PeriodScheduleQualityDto | null>(null);
  const [density, setDensity] = useState<GroupDensityDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [exportAxis, setExportAxis] = useState<ExportAxis>('GROUP');
  const [exporting, setExporting] = useState(false);
  // Здоровье проекции: сбой асинхронной синхронизации иначе виден только в логе,
  // то есть не виден никому — занятие просто не появляется в сетке.
  const [health, setHealth] = useState<ProjectionHealthDto | null>(null);
  const [repairing, setRepairing] = useState(false);

  // Выгрузка расписания периода в Excel (все сущности выбранной оси). Бэк отдаёт файл,
  // ScheduleService сам запускает скачивание — здесь только состояние кнопки.
  const handleExport = async () => {
    if (!period) return;
    setExporting(true);
    try {
      await ScheduleService.exportSchedule(period.id, exportAxis);
    } catch (e) {
      console.error('Не удалось выгрузить расписание:', e);
    } finally {
      setExporting(false);
    }
  };

  // Данные выбранного периода — перезагружаются при смене периода.
  // Готовность + плотность групп (честная ёмкость с бэка) + качество преподавателей + сверка проекции.
  useEffect(() => {
    if (!period) { setReadiness(null); setQuality(null); setDensity([]); setHealth(null); setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    Promise.all([
      ScheduleService.getReadiness(period.id),
      ScheduleService.getEducatorQuality(period.id),
      ScheduleService.getGroupDensity(period.id),
      ScheduleService.getProjectionHealth(period.id),
    ])
      .then(([rd, q, dens, hp]) => {
        if (cancelled) return;
        setReadiness(rd);
        setQuality(q);
        setDensity(dens);
        setHealth(hp);
      })
      .catch((e) => {
        console.error('Дашборд: не удалось загрузить данные периода:', e);
        if (!cancelled) { setReadiness(null); setQuality(null); setDensity([]); setHealth(null); }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [selectedPeriodId, period?.id]);

  // Ремонт: пересобрать read-модель сессии из размещений (расписание не двигается) и пересверить.
  const handleRepairProjection = async () => {
    if (!period || !health?.sessionId) return;
    setRepairing(true);
    try {
      await CQRSService.reproject(health.sessionId);
      setHealth(await ScheduleService.getProjectionHealth(period.id));
    } catch (e) {
      console.error('Не удалось пересобрать read-модель:', e);
    } finally {
      setRepairing(false);
    }
  };

  const metrics = useMemo(() => {
    if (!period) return null;
    // Рабочие дни периода Пн–Сб (getDay: 0=Вс … 6=Сб). Воскресенья не учебные.
    const workingDays = eachDayOfInterval({
      start: parseISO(period.startDate),
      end: parseISO(period.endDate),
    }).filter((d) => { const g = getDay(d); return g >= 1 && g <= 6; }).length;
    return { workingDays };
  }, [period]);

  const readyPct = readiness && readiness.total > 0
    ? Math.round((readiness.placed / readiness.total) * 100)
    : 0;

  // Все задействованные в расписании преподаватели. Бэк уже отсортировал: с требованием
  // компактности первыми, худшие (больший штраф) сверху. Для остальных компактность не
  // приоритет — штраф/подсветку к ним не применяем (см. таблицу ниже).
  const educators = quality?.educators ?? [];

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
        {period && (
          <div className="flex items-center gap-2 ml-auto">
            <select
              value={exportAxis}
              onChange={(e) => setExportAxis(e.target.value as ExportAxis)}
              disabled={exporting}
              className="text-xs font-semibold border border-slate-200 rounded-lg px-2 py-1.5 bg-white text-slate-700"
              title="Перспектива выгрузки"
            >
              <option value="GROUP">Группы</option>
              <option value="EDUCATOR">Преподаватели</option>
              <option value="AUDITORIUM">Аудитории</option>
            </select>
            <button
              onClick={handleExport}
              disabled={exporting}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 text-white text-xs font-semibold rounded-lg hover:bg-emerald-700 transition-colors disabled:opacity-60"
              title="Выгрузить расписание периода в Excel"
            >
              {exporting ? <Loader2 size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}
              Excel
            </button>
          </div>
        )}
        {onNavigate && (
          <div className="flex items-center gap-2">
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
      {/* Проекция отстала: часть занятий стоит на write-стороне, но не доехала до сетки.
          Раньше это было видно только в логе — то есть не видно вовсе. Лечится перепроекцией
          (расписание не двигается: даты, слоты и замки берутся из тех же размещений). */}
      {health && health.missing > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3">
          <AlertTriangle size={18} className="text-amber-600 shrink-0" />
          <div className="min-w-0 text-sm text-amber-900">
            <span className="font-bold">Расписание отображается неполно:</span>{' '}
            {health.missing} из {health.placements} занятий не доехало до сетки — синхронизация
            отображения отстала или сорвалась. Сами занятия целы.
          </div>
          <button
            onClick={handleRepairProjection}
            disabled={repairing}
            title="Пересобрать отображение из размещений. Расписание не меняется: даты, пары и замки те же."
            className="ml-auto flex items-center gap-1.5 px-3 py-1.5 bg-amber-600 text-white text-xs font-semibold rounded-lg hover:bg-amber-700 transition-colors disabled:opacity-60 shrink-0"
          >
            {repairing ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
            Восстановить отображение
          </button>
        </div>
      )}

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

      {density.length === 0 ? (
        <EmptyState
          title="За период нет данных по группам"
          subtitle="Сформируйте план в планировщике (нужны курсы с назначениями на этот период)"
          onNavigate={onNavigate}
        />
      ) : (
        <div className="space-y-6">
          {/* Плотность групп 1–3 */}
          <Card title="Плотность групп (пары 1–3)">
            <div className="space-y-3">
              <p className="text-[11px] text-slate-400 flex items-start gap-1.5">
                <Info size={13} className="shrink-0 mt-0.5" />
                «Всего» — сколько занятий нужно разместить группе; «Осталось» — ещё не размещено.
                Ёмкость 1–3 считается на бэке честно: закрытые пары (Вс, Сб-4) и групповые
                ограничения уже вычтены. «Своб. 1–3» может стать отрицательной при перегрузе.
              </p>
              <div className="max-h-[320px] overflow-auto custom-scrollbar -mx-1 px-1">
                <table className="w-full text-xs">
                  <thead className="text-[10px] uppercase tracking-wide text-slate-400">
                    <tr className="border-b border-slate-100">
                      <th className="text-left font-bold py-1.5">Группа</th>
                      <th className="text-right font-bold px-2">Всего</th>
                      <th className="text-right font-bold px-2">Осталось</th>
                      <th className="text-right font-bold px-2">Своб. 1–3</th>
                      <th className="text-right font-bold px-2">Занято 1–3</th>
                      <th className="text-right font-bold pl-2">В 4-й</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {density.map((g) => {
                      const tight = g.free13 <= 0;
                      return (
                        <tr key={g.groupId} className={cn('transition-colors', tight && 'bg-red-50/60')}>
                          <td className="py-1.5 font-bold text-slate-700 truncate max-w-[120px]">{g.groupName}</td>
                          <td className="text-right px-2 tabular-nums text-slate-500">{g.demand}</td>
                          <td className={cn('text-right px-2 tabular-nums font-bold', g.remaining > 0 ? 'text-blue-600' : 'text-emerald-600')}>
                            {g.remaining}
                          </td>
                          <td className={cn('text-right px-2 font-black tabular-nums', tight ? 'text-red-600' : 'text-emerald-600')}>
                            {g.free13}
                          </td>
                          <td className="text-right px-2 tabular-nums text-slate-500">{g.placed13}</td>
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
          {educators.length > 0 && (
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
                  Цель — 2–3 пары в учебный день без окон. Штраф и подсветка — только для преподавателей
                  с требованием компактности (отмечены точкой), худшие сверху; для остальных метрики
                  справочные и штрафом не считаются.
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
                      {educators.map((e) => {
                        // Штраф/подсветка — только там, где компактность важна (флаг). Для
                        // остальных «проблемные» колонки нейтральны: окна/одиночные — не нарушение.
                        const tracked = e.compact;
                        const hot = tracked && (e.windowSlots > 0 || e.singlePairDays >= 5);
                        return (
                          <tr key={e.educatorId} className={cn('transition-colors', hot && 'bg-red-50/60')}>
                            <td className="py-1.5 font-bold text-slate-700 truncate max-w-[160px]">
                              {tracked && (
                                <span
                                  title="Требуется компактное расписание"
                                  className="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 mr-1.5 align-middle"
                                />
                              )}
                              {e.educatorName}
                            </td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{e.avgPairsPerDay.toFixed(1)}</td>
                            <td className={cn('text-right px-2 tabular-nums font-bold',
                              !tracked ? 'text-slate-300' : e.singlePairDays > 0 ? 'text-amber-600' : 'text-slate-300')}>
                              {e.singlePairDays}
                            </td>
                            <td className={cn('text-right px-2 tabular-nums font-bold',
                              !tracked ? 'text-slate-300' : e.windowSlots > 0 ? 'text-red-600' : 'text-slate-300')}>
                              {e.windowSlots}
                            </td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{e.saturdayPairs}</td>
                            <td className={cn('text-right px-2 tabular-nums font-bold',
                              e.saturdayDeviation > 0.05 ? 'text-red-500' : e.saturdayDeviation < -0.05 ? 'text-emerald-500' : 'text-slate-300')}>
                              {e.saturdayDeviation > 0 ? '+' : ''}{e.saturdayDeviation.toFixed(1)}
                            </td>
                            <td className={cn('text-right pl-2 tabular-nums font-black',
                              !tracked ? 'text-slate-300' : e.penalty === 0 ? 'text-emerald-600' : hot ? 'text-red-600' : 'text-slate-600')}
                              title={!tracked ? 'Компактность не требуется — штрафом не считается' : undefined}>
                              {tracked ? e.penalty : '—'}
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
