import React, { useEffect, useMemo, useState } from 'react';
import { eachDayOfInterval, getDay, parseISO } from 'date-fns';
import { cn } from '../../../utils/cn';
import { Card } from '../../../components/ui/Card';
import { HelpTip } from '../../../components/ui/HelpTip';
import { ScheduleService } from '../../../services/apiServices';
import { CQRSService } from '../../../services/cqrsApiService';
import {
  PeriodReadinessDto, PeriodScheduleQualityDto, GroupDensityDto, ExportAxis, ProjectionHealthDto,
  AuditoriumHealthDto, PeriodAuditoriumLoadDto
} from '../../../types/api';
import { usePeriod } from '../../period/PeriodContext';
import {
  Users, School, BookOpen, Layers, Loader2, CalendarRange,
  AlertTriangle, CalendarClock, ArrowRight, FileSpreadsheet, RefreshCw
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
  // Здоровье аудиторий: двойные бронирования и переполнения. Решатель их не видит (в его модели
  // занятости ячейка → одно занятие, второе перезаписывает первое), сетка тоже — строки
  // schedule_view друг о друге не знают. Без этого среза их вообще никак не заметить.
  const [rooms, setRooms] = useState<AuditoriumHealthDto | null>(null);
  // Загрузка аудиторий (утилизация) — формат как у преподавателей: занято/свободно/загрузка %.
  const [roomLoad, setRoomLoad] = useState<PeriodAuditoriumLoadDto | null>(null);

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
    if (!period) { setReadiness(null); setQuality(null); setDensity([]); setHealth(null); setRooms(null); setRoomLoad(null); setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    Promise.all([
      ScheduleService.getReadiness(period.id),
      ScheduleService.getEducatorQuality(period.id),
      ScheduleService.getGroupDensity(period.id),
      ScheduleService.getProjectionHealth(period.id),
      ScheduleService.getAuditoriumHealth(period.id),
      ScheduleService.getAuditoriumLoad(period.id),
    ])
      .then(([rd, q, dens, hp, rh, rl]) => {
        if (cancelled) return;
        setReadiness(rd);
        setQuality(q);
        setDensity(dens);
        setHealth(hp);
        setRooms(rh);
        setRoomLoad(rl);
      })
      .catch((e) => {
        console.error('Дашборд: не удалось загрузить данные периода:', e);
        if (!cancelled) { setReadiness(null); setQuality(null); setDensity([]); setHealth(null); setRooms(null); setRoomLoad(null); }
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

  // Худший перебор по всем комнатам. Именно величина отличает рабочую тесноту от фикции:
  // +2 человека диспетчер сажает не задумываясь, +80 не сажает никогда.
  const maxExcess = useMemo(
    () => (rooms?.rooms ?? []).reduce((max, r) => Math.max(max, r.maxExcess), 0),
    [rooms]
  );

  // Порядок групп в таблице плотности. Бэк сортирует ПО СМЫСЛУ (кому не хватает пар 1–3 —
  // наверх, см. подсказку к таблице), и этот порядок отвечает на вопрос «где горит».
  // Но он же мешает ответить на вопрос «а что у группы 955/2» — глазами её не найти.
  // Это два разных вопроса, поэтому переключатель, а не замена одного порядка другим.
  const [groupSort, setGroupSort] = useState<'problems' | 'number'>('problems');

  // Номера групп — не просто строки: «955/2» должна идти ПЕРЕД «955/12», а не после, как дало бы
  // обычное сравнение строк. numeric сравнивает цифровые куски числами, поэтому и «45/1 → 45/2 →
  // 46», и «955/2 → 955/12» выходят правильно, без ручного разбора имени на части.
  const groupCollator = useMemo(() => new Intl.Collator('ru', { numeric: true, sensitivity: 'base' }), []);

  const sortedDensity = useMemo(() => {
    if (groupSort === 'problems') return density; // порядок бэка — как пришёл
    return [...density].sort((a, b) => groupCollator.compare(a.groupName ?? '', b.groupName ?? ''));
  }, [density, groupSort, groupCollator]);

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

      {/*
        Аудитории. Два баннера, а не один, потому что метрики разной силы:
        двойное бронирование — физика (две группы не войдут в одну дверь), допустимо только ноль;
        переполнение — суждение (перебор на пару человек рабочий, на десятки — фикция).
        Кнопки «починить» нет намеренно: конфликт разрешается переносом занятия или сменой
        комнаты, то есть решением диспетчера, а не операцией. Разбивка по комнатам — самое
        полезное здесь: причина обычно в самой комнате (маленькая аудитория, назначенная базовой
        сразу нескольким группам, ловит конфликты пачками).
      */}
      {rooms && rooms.doubleBooked > 0 && (
        <div className="rounded-xl border border-red-300 bg-red-50 px-4 py-3 space-y-2">
          <div className="flex items-start gap-3">
            <AlertTriangle size={18} className="text-red-600 shrink-0 mt-0.5" />
            <div className="min-w-0 text-sm text-red-900">
              <span className="font-bold">Двойное бронирование аудиторий:</span>{' '}
              {rooms.doubleBooked} занятий делят комнату с другими — в {rooms.conflictingCells} ячейках
              расписания две группы придут в одну дверь. Расписание выглядит нормальным: ни сетка,
              ни распределитель этого не показывают.
            </div>
          </div>
          <div className="flex flex-wrap gap-1.5 pl-8">
            {rooms.rooms.filter((r) => r.conflictingCells > 0).map((r) => (
              <span
                key={r.auditoriumId}
                title={`${r.doubleBooked} занятий в ${r.conflictingCells} ячейках · вместимость ${r.capacity}`}
                className="px-2 py-0.5 rounded-md bg-white border border-red-200 text-[11px] font-semibold text-red-800"
              >
                {r.name ?? `#${r.auditoriumId}`} · {r.conflictingCells} ячеек · {r.capacity} мест
              </span>
            ))}
          </div>
        </div>
      )}

      {rooms && rooms.overCapacity > 0 && (
        <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 space-y-2">
          <div className="flex items-start gap-3">
            <AlertTriangle size={18} className="text-amber-600 shrink-0 mt-0.5" />
            <div className="min-w-0 text-sm text-amber-900">
              <span className="font-bold">Не помещаются в аудиторию:</span>{' '}
              {rooms.overCapacity} занятий — людей больше, чем мест
              {maxExcess > 0 && <>, перебор до {maxExcess} человек</>}. Решать вам: несколько
              человек — рабочая ситуация, десятки — нет.
            </div>
          </div>
          <div className="flex flex-wrap gap-1.5 pl-8">
            {rooms.rooms.filter((r) => r.overCapacity > 0).map((r) => (
              <span
                key={r.auditoriumId}
                title={`${r.overCapacity} занятий не помещается · вместимость ${r.capacity} · максимальный перебор ${r.maxExcess}`}
                className="px-2 py-0.5 rounded-md bg-white border border-amber-200 text-[11px] font-semibold text-amber-800"
              >
                {r.name ?? `#${r.auditoriumId}`} · {r.overCapacity} занятий · +{r.maxExcess} чел.
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Третий баннер того же датчика. Тоже про физику, но с другой причиной: комнаты нет вовсе,
          и «где идёт занятие» ответа не имеет. У импортированного расписания это массовое
          состояние — комната напечатана в файле, а у нас её нет либо одноимённых несколько. */}
      {rooms && rooms.withoutAuditorium > 0 && (
        <div className="rounded-xl border border-red-300 bg-red-50 px-4 py-3">
          <div className="flex items-start gap-3">
            <AlertTriangle size={18} className="text-red-600 shrink-0 mt-0.5" />
            <div className="min-w-0 text-sm text-red-900">
              <span className="font-bold">Без аудитории:</span>{' '}
              {rooms.withoutAuditorium} занятий стоят в расписании, но комната у них не назначена —
              где они идут, неизвестно. Так бывает, если комнату удалили либо расписание пришло
              импортом и комнаты из файла у нас не нашлось. В сетке такие занятия помечены «?».
            </div>
          </div>
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
          {/* Плотность групп 1–3: слева — сколько пар есть, справа — сколько занятий в них ставить */}
          <Card
            title="Плотность групп (пары 1–3)"
            bodyClassName="px-4 pb-4 pt-1"
            headerActions={
              <div className="flex items-center gap-2">
                <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5">
                  <button
                    type="button"
                    onClick={() => setGroupSort('problems')}
                    title="Сначала те, кому не хватает пар 1–3"
                    className={cn(
                      'px-2 py-0.5 rounded-md text-[10px] font-black uppercase tracking-tight transition-colors',
                      groupSort === 'problems' ? 'bg-white text-slate-700 shadow-sm' : 'text-slate-400 hover:text-slate-600'
                    )}
                  >
                    По проблемам
                  </button>
                  <button
                    type="button"
                    onClick={() => setGroupSort('number')}
                    title="По номеру группы: 45/1 → 45/2 → 46 → 955/2 → 955/12"
                    className={cn(
                      'px-2 py-0.5 rounded-md text-[10px] font-black uppercase tracking-tight transition-colors',
                      groupSort === 'number' ? 'bg-white text-slate-700 shadow-sm' : 'text-slate-400 hover:text-slate-600'
                    )}
                  >
                    По номеру
                  </button>
                </div>
                <HelpTip text={
                  'Слоты — учебные пары 1–3 за период: ёмкость считается на бэке честно (закрытые пары '
                  + 'Вс и Сб-4, а также групповые ограничения уже вычтены), свободно = ёмкость минус занятое.\n\n'
                  + 'Занятия — что нужно разместить группе: всего по учебному плану, уже стоит в 1–3, '
                  + 'стоит в 4-й паре, осталось разместить.\n\n'
                  + '«Не влезет в 1–3» — из оставшихся занятий столько не поместится в свободные пары 1–3, '
                  + 'их придётся ставить в 4-ю пару. В порядке «по проблемам» такие группы показаны '
                  + 'сверху; подсветка сохраняется в любом порядке.'
                } />
              </div>
            }
          >
            <div className="max-h-[320px] overflow-auto custom-scrollbar -mx-1 px-1">
              <table className="w-full text-xs">
                <thead className="text-[10px] uppercase tracking-wide text-slate-400">
                  {/* Две группы столбцов: ресурс (пары) и потребность (занятия) — их легко спутать */}
                  <tr className="border-b border-slate-100">
                    <th className="py-1" />
                    <th colSpan={2} className="text-center font-black text-slate-400 pb-1 border-l border-slate-100">
                      Слоты 1–3
                    </th>
                    <th colSpan={4} className="text-center font-black text-slate-400 pb-1 border-l border-slate-100">
                      Занятия
                    </th>
                    <th className="py-1 border-l border-slate-100" />
                  </tr>
                  <tr className="border-b border-slate-100">
                    <th className="text-left font-bold py-1.5">Группа</th>
                    <th className="text-right font-bold px-2 border-l border-slate-100">Ёмкость</th>
                    <th className="text-right font-bold px-2">Свободно</th>
                    <th className="text-right font-bold px-2 border-l border-slate-100">Всего</th>
                    <th className="text-right font-bold px-2">В 1–3</th>
                    <th className="text-right font-bold px-2">В 4-й</th>
                    <th className="text-right font-bold px-2">Осталось</th>
                    <th className="text-right font-bold px-2 border-l border-slate-100">Не влезет в 1–3</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {sortedDensity.map((g) => {
                    // Проблема — не «мало свободных пар», а «занятия не помещаются».
                    const overflow = g.mustGoToFourth > 0;
                    return (
                      <tr key={g.groupId} className={cn('transition-colors', overflow && 'bg-red-50/60')}>
                        <td className="py-1.5 font-bold text-slate-700 truncate max-w-[120px]">{g.groupName}</td>
                        <td className="text-right px-2 tabular-nums text-slate-400 border-l border-slate-100">{g.capacity13}</td>
                        <td className={cn('text-right px-2 tabular-nums font-bold', g.free13 > 0 ? 'text-emerald-600' : 'text-red-600')}>
                          {g.free13}
                        </td>
                        <td className="text-right px-2 tabular-nums text-slate-500 border-l border-slate-100">{g.demand}</td>
                        <td className="text-right px-2 tabular-nums text-slate-500">{g.placed13}</td>
                        <td className={cn('text-right px-2 tabular-nums font-bold', g.inFourth > 0 ? 'text-amber-600' : 'text-slate-300')}>
                          {g.inFourth}
                        </td>
                        <td className={cn('text-right px-2 tabular-nums font-bold', g.remaining > 0 ? 'text-blue-600' : 'text-emerald-600')}>
                          {g.remaining}
                        </td>
                        <td className={cn('text-right px-2 font-black tabular-nums border-l border-slate-100',
                          overflow ? 'text-red-600' : 'text-slate-300')}>
                          {g.mustGoToFourth}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </Card>

          {/* Преподаватели: компактность + равномерность (единый отчёт с бэка) */}
          {educators.length > 0 && (
            <Card
              title="Преподаватели: компактность и нагрузка"
              bodyClassName="px-4 pb-4 pt-1"
              headerActions={
                <HelpTip text={
                  'Штраф = окна + 2·одиночные дни + лишние дни (меньше — плотнее; суббота и 4-я пара '
                  + 'в штраф не входят). Цель — 2–3 пары в учебный день без окон.\n\n'
                  + 'Штраф и подсветка — только для преподавателей с требованием компактности (отмечены точкой), '
                  + 'худшие сверху; для остальных метрики справочные и штрафом не считаются.\n\n'
                  + '«В 4-й» — сколько дней у преподавателя занята 4-я (последняя) пара: она нежелательна, '
                  + 'и полезно видеть, на кого свалилась. Считается у всех.'
                } />
              }
            >
              <div className="space-y-2">
                <div className="flex flex-wrap gap-2 text-[11px]">
                  <Chip label="Плотно уложены" value={`${quality!.wellPacked}/${quality!.compactEducators}`} tone="emerald" />
                  <Chip label="Ср. штраф" value={quality!.avgPenalty} tone="slate" />
                  <Chip label="Одиночных дней" value={quality!.totalSinglePairDays} tone="amber" />
                  <Chip label="Окон" value={quality!.totalWindowSlots} tone="red" />
                  <Chip label="Ср. суббота" value={quality!.avgSaturday.toFixed(1)} tone="slate" />
                </div>
                <div className="max-h-[360px] overflow-auto custom-scrollbar -mx-1 px-1">
                  <table className="w-full text-xs">
                    <thead className="text-[10px] uppercase tracking-wide text-slate-400">
                      <tr className="border-b border-slate-100">
                        <th className="text-left font-bold py-1.5">Преподаватель</th>
                        <th className="text-right font-bold px-2">Пар/день</th>
                        <th className="text-right font-bold px-2">Одиноч.</th>
                        <th className="text-right font-bold px-2">Окна</th>
                        <th className="text-right font-bold px-2">В 4-й</th>
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
                            {/* 4-я пара — не про компактность, поэтому в штраф не входит и
                                подсвечивается у всех, а не только у «компактных». */}
                            <td className={cn('text-right px-2 tabular-nums font-bold',
                              e.fourthPairs > 0 ? 'text-amber-600' : 'text-slate-300')}>
                              {e.fourthPairs}
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

          {/* Аудитории: загрузка (утилизация) — формат как у преподавателей */}
          {roomLoad && roomLoad.auditoriums.length > 0 && (
            <Card
              title="Аудитории: загрузка"
              bodyClassName="px-4 pb-4 pt-1"
              headerActions={
                <HelpTip text={
                  'Загрузка % = занятые пары / доступные пары периода × 100 (Вс и Сб-4 закрыты). '
                  + 'Знаменатель — открытые ячейки «дата×пара» за период, одинаков для всех комнат.\n\n'
                  + '«Занято/Свободно» — ячейки времени (двойное бронирование считается за одну; конфликты — '
                  + 'отдельный срез «здоровье аудиторий»). «4-я» — дней с занятой последней парой.\n\n'
                  + 'Самые загруженные сверху; простаивающие комнаты — 0 %.'
                } />
              }
            >
              <div className="space-y-2">
                <div className="flex flex-wrap gap-2 text-[11px]">
                  <Chip label="Задействовано" value={`${roomLoad.roomsUsed}/${roomLoad.totalRooms}`} tone="emerald" />
                  <Chip label="Ср. загрузка" value={`${roomLoad.avgLoadPercent.toFixed(0)} %`} tone="slate" />
                  <Chip label="Простаивают" value={roomLoad.idleRooms} tone="amber" />
                  <Chip label="Доступно пар" value={roomLoad.availablePairs} tone="slate" />
                </div>
                <div className="max-h-[360px] overflow-auto custom-scrollbar -mx-1 px-1">
                  <table className="w-full text-xs">
                    <thead className="text-[10px] uppercase tracking-wide text-slate-400">
                      <tr className="border-b border-slate-100">
                        <th className="text-left font-bold py-1.5">Аудитория</th>
                        <th className="text-right font-bold px-2">Занято</th>
                        <th className="text-right font-bold px-2">Свободно</th>
                        <th className="text-right font-bold px-2">Загрузка</th>
                        <th className="text-right font-bold px-2">Пар/день</th>
                        <th className="text-right font-bold px-2">Дней</th>
                        <th className="text-right font-bold px-2">В 4-й</th>
                        <th className="text-right font-bold pl-2">Суббот</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-50">
                      {roomLoad.auditoriums.map((r) => {
                        const idle = r.occupiedPairs === 0;
                        const heavy = r.loadPercent >= 85;
                        return (
                          <tr key={r.auditoriumId} className={cn('transition-colors', heavy && 'bg-red-50/60')}>
                            <td className="py-1.5 font-bold text-slate-700 truncate max-w-[160px]"
                              title={`Вместимость ${r.capacity} мест`}>
                              {r.name ?? `#${r.auditoriumId}`}
                            </td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{r.occupiedPairs}</td>
                            <td className="text-right px-2 tabular-nums text-slate-400">{r.freePairs}</td>
                            <td className={cn('text-right px-2 tabular-nums font-black',
                              idle ? 'text-slate-300' : heavy ? 'text-red-600' : 'text-slate-700')}>
                              {r.loadPercent.toFixed(0)} %
                            </td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{r.avgPairsPerDay.toFixed(1)}</td>
                            <td className="text-right px-2 tabular-nums text-slate-500">{r.daysUsed}</td>
                            <td className={cn('text-right px-2 tabular-nums font-bold',
                              r.fourthPairs > 0 ? 'text-amber-600' : 'text-slate-300')}>
                              {r.fourthPairs}
                            </td>
                            <td className="text-right pl-2 tabular-nums text-slate-500">{r.saturdayPairs}</td>
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
