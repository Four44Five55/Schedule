import React, { useState, useEffect, useMemo } from 'react';
import { parseISO, format } from 'date-fns';
import { ConstraintsService, ResourceService } from '../../../services/apiServices';
import {
  ConstraintDto, GroupDto, EducatorDto, AuditoriumDto, KindOfConstraints, TimeSlotPair,
  EducatorConstraintDto, GroupConstraintDto, AuditoriumConstraintDto,
} from '../../../types/api';
import { Users, UserSquare2, School, Search, Loader2, ShieldAlert, Plus, Trash2, LayoutGrid, GanttChartSquare, ChevronRight, ChevronDown, Settings2 } from 'lucide-react';
import { ConstraintsGridSchedule } from './ConstraintsGridSchedule';
import { ConstraintsGanttEditor } from './ConstraintsGanttEditor';
import { ConstraintFormModal } from './ConstraintFormModal';
import { TimelineEntity } from '../../../components/grid/EntityTimelineShell';
import { cn } from '../../../utils/cn';
import { useEnums } from '../../../context/EnumContext';
import { ConstraintKindsModal } from './ConstraintKindsModal';
import { useToast } from '../../../context/ToastContext';
import { errorMessage } from '../../../services/apiError';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';

type FilterType = 'group' | 'educator' | 'auditorium';
type ViewMode = 'grid' | 'gantt';

interface NamedEntity { id: number; name: string; }

/** Наречие дня недели для подписи однодневных групп (getDay(): 0=Вс..6=Сб). */
const WEEKDAY_ADVERB = ['по воскресеньям', 'по понедельникам', 'по вторникам', 'по средам', 'по четвергам', 'по пятницам', 'по субботам'];

interface ConstraintLists {
  educator: EducatorConstraintDto[];
  group: GroupConstraintDto[];
  auditorium: AuditoriumConstraintDto[];
}

/**
 * Ограничение круга сущностей по типам. Поле задано → показываем только эти id; поле
 * undefined → все сущности этого типа. Так планировщик показывает только участников
 * (преподаватели/группы из назначений), а аудитории пока не скоупит (придёт позже —
 * достаточно передать auditoriumIds, рабочее место менять не нужно).
 */
export interface ConstraintScope {
  educatorIds?: number[];
  groupIds?: number[];
  auditoriumIds?: number[];
}

interface ConstraintsWorkspaceProps {
  /** Период, над которым редактируем ограничения (источник дат сетки/Ганта). */
  startDate: Date;
  endDate: Date;
  /** Если задан — ограничивает круг сущностей (напр. участники выбранных курсов). */
  scope?: ConstraintScope;
}

/**
 * Переиспользуемое «рабочее место» ограничений: тип сущности + формат (Сетка/Гант) +
 * редакторы + список + модалка + счётчик «учтено за период». Период приходит от хоста,
 * поэтому компонент одинаково работает и в разделе «Ограничения» (свой выбор периода),
 * и во вкладке планировщика (период планировщика) — DRY/SRP.
 *
 * Все ограничения трёх типов грузятся целиком (это master-data, данных немного); из них
 * выводятся и сетка (одна сущность), и Гант (все сущности типа), и счётчик пересечений
 * с периодом — без отдельных endpoint'ов на каждый случай.
 */
export const ConstraintsWorkspace: React.FC<ConstraintsWorkspaceProps> = ({ startDate, endDate, scope }) => {
  // Виды ограничений — пользовательский справочник: цвет и подписи берём из него, а не из карты
  // кодов на фронте (кодов новых видов фронт знать не может).
  const { getConstraintStyle } = useEnums();
  const toast = useToast();
  const [kindsOpen, setKindsOpen] = useState(false);
  const [filterType, setFilterType] = useState<FilterType>(() => {
    const saved = localStorage.getItem('unischedule.constraints.filterType');
    return saved === 'group' || saved === 'educator' || saved === 'auditorium' ? saved : 'educator';
  });
  const [selectedId, setSelectedId] = useState<number | ''>(() => {
    const saved = localStorage.getItem('unischedule.constraints.selectedId');
    return saved ? Number(saved) : '';
  });
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    const saved = localStorage.getItem('unischedule.constraints.viewMode');
    return saved === 'gantt' ? 'gantt' : 'grid';
  });

  const [resources, setResources] = useState<{ groups: GroupDto[]; educators: EducatorDto[]; auditoriums: AuditoriumDto[] }>(
    { groups: [], educators: [], auditoriums: [] }
  );
  const [lists, setLists] = useState<ConstraintLists>({ educator: [], group: [], auditorium: [] });
  const [loadingResources, setLoadingResources] = useState(true);
  const [loadingConstraints, setLoadingConstraints] = useState(false);
  /** Текст об отказе загрузки — молчать нельзя: пустой раздел неотличим от «ограничений нет». */
  const [loadError, setLoadError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [modalPreset, setModalPreset] = useState<{ startDate: string; endDate: string; timeSlot?: TimeSlotPair } | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const toggleGroup = (k: string) => setExpandedGroups((prev) => {
    const next = new Set(prev);
    if (next.has(k)) next.delete(k); else next.add(k);
    return next;
  });

  const startStr = format(startDate, 'yyyy-MM-dd');
  const endStr = format(endDate, 'yyyy-MM-dd');

  const openManualCreate = () => { setModalPreset(null); setShowModal(true); };
  const handleCellSelect = (dateStr: string, slot: TimeSlotPair) => {
    setModalPreset({ startDate: dateStr, endDate: dateStr, timeSlot: slot });
    setShowModal(true);
  };
  const closeModal = () => { setShowModal(false); setModalPreset(null); };
  const refresh = () => setRefreshTick((t) => t + 1);

  // Справочники — один раз.
  useEffect(() => {
    Promise.all([
      ResourceService.getGroups(),
      ResourceService.getEducators(),
      ResourceService.getAuditoriums(),
    ]).then(([groups, educators, auditoriums]) => {
      setResources({ groups, educators, auditoriums });
    })
      // Без справочников выбирать объект не из чего — раздел выглядит так, будто ни групп,
      // ни преподавателей не заведено.
      .catch((e) => setLoadError(errorMessage(e, 'Не удалось загрузить группы, преподавателей и аудитории.')))
      .finally(() => setLoadingResources(false));
  }, []);

  // Все ограничения трёх типов (master-data) — на монтировании и после правок.
  //
  // allSettled, а не all: при `all` отказ ОДНОГО запроса не давал выполниться `.then`, и все три
  // списка молча оставались пустыми — раздел выглядел как «ограничений нет», хотя они есть
  // (в расписании те же ограничения продолжали показываться: там свой запрос по сущности).
  // Пустой экран — худший вид ошибки: он неотличим от правды. Теперь уцелевшие типы
  // показываются, а про упавший говорится вслух.
  useEffect(() => {
    setLoadingConstraints(true);
    let cancelled = false;
    Promise.allSettled([
      ConstraintsService.getEducatorConstraints(),
      ConstraintsService.getGroupConstraints(),
      ConstraintsService.getAuditoriumConstraints(),
    ]).then((results) => {
      if (cancelled) return;
      const [educator, group, auditorium] = results;
      const failed: string[] = [];
      results.forEach((r, i) => {
        if (r.status === 'rejected') {
          failed.push(['преподавателей', 'групп', 'аудиторий'][i]);
          console.error('Не удалось загрузить ограничения:', r.reason);
        }
      });
      setLists({
        educator: educator.status === 'fulfilled' ? educator.value : [],
        group: group.status === 'fulfilled' ? group.value : [],
        auditorium: auditorium.status === 'fulfilled' ? auditorium.value : [],
      });
      setLoadError(failed.length === 0 ? null
        : `Не загрузились ограничения ${failed.join(', ')} — показано неполно. Подробности в консоли браузера.`);
    }).finally(() => { if (!cancelled) setLoadingConstraints(false); });
    return () => { cancelled = true; };
  }, [refreshTick]);

  useEffect(() => { localStorage.setItem('unischedule.constraints.filterType', filterType); }, [filterType]);
  useEffect(() => { localStorage.setItem('unischedule.constraints.selectedId', String(selectedId)); }, [selectedId]);
  useEffect(() => { localStorage.setItem('unischedule.constraints.viewMode', viewMode); }, [viewMode]);

  // id сущности из ограничения — поле зависит от типа.
  const entityIdOf = (c: ConstraintDto): number =>
    filterType === 'group' ? (c as GroupConstraintDto).groupId
      : filterType === 'educator' ? (c as EducatorConstraintDto).educatorId
        : (c as AuditoriumConstraintDto).auditoriumId;

  const createWholeDay = (entityId: number, sStr: string, eStr: string, kind: KindOfConstraints): Promise<void> => {
    const base = { kindOfConstraint: kind, startDate: sStr, endDate: eStr };
    if (filterType === 'educator') return ConstraintsService.createEducatorConstraint({ educatorId: entityId, ...base }).then(() => {});
    if (filterType === 'group') return ConstraintsService.createGroupConstraint({ groupId: entityId, ...base }).then(() => {});
    return ConstraintsService.createAuditoriumConstraint({ auditoriumId: entityId, ...base }).then(() => {});
  };
  // Пер-парное создание (одна ячейка сетки): один день + конкретная пара (timeSlot).
  const createForCell = (entityId: number, dateStr: string, slot: TimeSlotPair, kind: KindOfConstraints): Promise<void> => {
    const base = { kindOfConstraint: kind, startDate: dateStr, endDate: dateStr, timeSlot: slot };
    if (filterType === 'educator') return ConstraintsService.createEducatorConstraint({ educatorId: entityId, ...base }).then(() => {});
    if (filterType === 'group') return ConstraintsService.createGroupConstraint({ groupId: entityId, ...base }).then(() => {});
    return ConstraintsService.createAuditoriumConstraint({ auditoriumId: entityId, ...base }).then(() => {});
  };
  // Кисть в сетке: массовое пер-парное создание по закрашенным ячейкам (напр. «все понедельники»).
  // Отдельного bulk-эндпоинта нет — N запросов (как «по всем» в Ганте); для текущих объёмов ок.
  const handlePaint = async (cells: { dateStr: string; slot: TimeSlotPair }[], kind: KindOfConstraints) => {
    if (selectedId === '' || cells.length === 0) return;
    try {
      await Promise.all(cells.map((c) => createForCell(selectedId as number, c.dateStr, c.slot, kind)));
    } catch (e) {
      // Кисть — N запросов без bulk-эндпоинта: часть ячеек могла не завестись, и разметка
      // на экране после refresh окажется дырявой. Молча это выглядит как промах мышью.
      toast.failure(e, 'Не удалось разметить часть ячеек — проверьте разметку после обновления.');
    }
    refresh();
  };
  // Ластик в сетке: массовое удаление ограничений по id (покрывающих закрашенные ячейки).
  const handleErase = async (ids: number[]) => {
    if (ids.length === 0) return;
    try {
      await Promise.all(ids.map((id) => deleteById(id)));
    } catch (e) {
      // Ластик снимает пачку: отказ на середине оставляет часть полос на месте, и без
      // сообщения это выглядит как «протянул не до конца». Раньше отказ вообще не ловился —
      // обещание падало в никуда.
      toast.failure(e, 'Не удалось снять часть ограничений — обновите и проверьте разметку.');
    }
    refresh();
  };
  const deleteById = (id: number): Promise<void> => {
    if (filterType === 'educator') return ConstraintsService.deleteEducatorConstraint(id);
    if (filterType === 'group') return ConstraintsService.deleteGroupConstraint(id);
    return ConstraintsService.deleteAuditoriumConstraint(id);
  };
  const handleDelete = async (id: number) => {
    try { await deleteById(id); refresh(); }
    catch (e) { toast.failure(e, 'Не удалось удалить ограничение.'); }
  };

  // Допустимые id текущего типа из scope (null = без ограничения, показываем всех).
  const allowedIds = useMemo((): Set<number> | null => {
    const ids = filterType === 'group' ? scope?.groupIds
      : filterType === 'educator' ? scope?.educatorIds
        : scope?.auditoriumIds;
    return ids ? new Set(ids) : null;
  }, [filterType, scope]);

  const entities: NamedEntity[] = useMemo(() => {
    const list = filterType === 'group' ? resources.groups
      : filterType === 'educator' ? resources.educators
        : resources.auditoriums;
    const sorted = [...list].sort((a, b) => a.name.localeCompare(b.name, 'ru'));
    return allowedIds ? sorted.filter((e) => allowedIds.has(e.id)) : sorted;
  }, [filterType, resources, allowedIds]);

  const selectedEntity = useMemo(
    () => (selectedId === '' ? undefined : entities.find((e) => e.id === selectedId)),
    [entities, selectedId]
  );

  const timelineEntities: TimelineEntity[] = useMemo(
    () => entities.map((e) => ({ id: e.id, label: e.name })),
    [entities]
  );

  // Список ограничений текущего типа и текущей сущности (для Сетки/списка).
  const typeList: ConstraintDto[] = lists[filterType];
  const entityConstraints = useMemo(
    () => (selectedId === '' ? [] : typeList.filter((c) => entityIdOf(c) === selectedId)),
    [typeList, selectedId, filterType]
  );

  // Группировка списка «в логическую суть»: одинаковые (вид + пара + описание) — одна строка
  // со счётчиком, диапазоном и удалением всей группы. Кисть по понедельникам (N однодневных
  // пер-парных записей) сворачивается в компактные строки вместо десятков одиночных.
  // Если все записи группы однодневные и на один день недели — подписываем «по понедельникам».
  const constraintGroups = useMemo(() => {
    const map = new Map<string, ConstraintDto[]>();
    for (const c of entityConstraints) {
      const key = `${c.kindOfConstraint}|${c.timeSlot ?? ''}|${c.description ?? ''}`;
      const list = map.get(key);
      if (list) list.push(c); else map.set(key, [c]);
    }
    const groups = Array.from(map.entries()).map(([key, items]) => {
      const sorted = [...items].sort((a, b) => a.startDate.localeCompare(b.startDate));
      const minStart = sorted[0].startDate;
      const maxEnd = sorted.reduce((m, c) => (c.endDate > m ? c.endDate : m), sorted[0].endDate);
      const allSingleDay = sorted.every((c) => c.startDate === c.endDate);
      let weekday: number | null = null;
      if (allSingleDay) {
        const days = new Set(sorted.map((c) => parseISO(c.startDate).getDay()));
        weekday = days.size === 1 ? [...days][0] : null;
      }
      return { key, items: sorted, sample: sorted[0], minStart, maxEnd, count: items.length, weekday };
    });
    return groups.sort((a, b) =>
      a.sample.abbreviation.localeCompare(b.sample.abbreviation) ||
      (a.sample.timeSlot ?? '').localeCompare(b.sample.timeSlot ?? '') ||
      a.minStart.localeCompare(b.minStart)
    );
  }, [entityConstraints]);

  // Удалить всю группу (bulk по id) — переиспользует пакетное удаление ластика.
  const handleDeleteGroup = (ids: number[], label: string) => {
    if (ids.length === 0) return;
    if (ids.length === 1) { handleDelete(ids[0]); return; }
    if (window.confirm(`Удалить ограничение «${label}» — все ${ids.length} записей?`)) {
      handleErase(ids);
    }
  };

  // Счётчик «учтено за период» — ограничения всех типов, пересекающие [start, end];
  // при scope считаем только сущности в круге участников.
  const periodCount = useMemo(() => {
    const overlaps = (c: ConstraintDto) => c.startDate <= endStr && c.endDate >= startStr;
    const eSet = scope?.educatorIds ? new Set(scope.educatorIds) : null;
    const gSet = scope?.groupIds ? new Set(scope.groupIds) : null;
    const aSet = scope?.auditoriumIds ? new Set(scope.auditoriumIds) : null;
    let n = 0;
    for (const c of lists.educator) if (overlaps(c) && (!eSet || eSet.has(c.educatorId))) n++;
    for (const c of lists.group) if (overlaps(c) && (!gSet || gSet.has(c.groupId))) n++;
    for (const c of lists.auditorium) if (overlaps(c) && (!aSet || aSet.has(c.auditoriumId))) n++;
    return n;
  }, [lists, scope, startStr, endStr]);

  // Легенда из самих данных (источник зависит от режима).
  const legendItems = useMemo(() => {
    const source = viewMode === 'gantt' ? typeList : entityConstraints;
    const seen = new Map<KindOfConstraints, { abbreviation: string; fullName: string }>();
    for (const c of source) {
      if (!seen.has(c.kindOfConstraint)) seen.set(c.kindOfConstraint, { abbreviation: c.abbreviation, fullName: c.fullName });
    }
    return [...seen.entries()].map(([kind, v]) => ({ kind, ...v }));
  }, [viewMode, typeList, entityConstraints]);

  if (loadingResources) {
    return <div className="p-8 text-center animate-pulse text-slate-400">Загрузка справочников...</div>;
  }

  return (
    <div className="space-y-4">
      {loadError && (
        <ErrorBanner message={loadError} className="rounded-xl" />
      )}

      {/* Панель выбора: тип сущности + формат + объект */}
      <div className="bg-white border border-slate-100 rounded-xl p-4 shadow-sm space-y-3">
        <div className="flex flex-col lg:flex-row items-center gap-3">
          <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0">
            <FilterBtn active={filterType === 'group'} onClick={() => { setFilterType('group'); setSelectedId(''); }} icon={Users} label="Группы" />
            <FilterBtn active={filterType === 'educator'} onClick={() => { setFilterType('educator'); setSelectedId(''); }} icon={UserSquare2} label="Преподы" />
            <FilterBtn active={filterType === 'auditorium'} onClick={() => { setFilterType('auditorium'); setSelectedId(''); }} icon={School} label="Ауд." />
          </div>

          {/* Формат ввода: Сетка (одна сущность, до пары) / Гант (все сущности, целый день) */}
          <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0">
            <FilterBtn active={viewMode === 'grid'} onClick={() => setViewMode('grid')} icon={LayoutGrid} label="Сетка" />
            <FilterBtn active={viewMode === 'gantt'} onClick={() => setViewMode('gantt')} icon={GanttChartSquare} label="Гант" />
          </div>

          {viewMode === 'grid' && (
            <div className="relative flex-1 w-full">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
              <select
                value={selectedId}
                onChange={(e) => setSelectedId(e.target.value ? Number(e.target.value) : '')}
                className="w-full pl-9 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-bold text-slate-700 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
              >
                <option value="">Выберите объект...</option>
                {entities.map((e) => (
                  <option key={e.id} value={e.id}>{e.name}</option>
                ))}
              </select>
            </div>
          )}

          <div className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-bold text-slate-600 shrink-0">
            Учтено за период: <span className="text-blue-600 font-black">{periodCount}</span>
          </div>

          {loadingConstraints && <Loader2 size={14} className="animate-spin text-blue-600 shrink-0" />}

          {/* Справочник видов: перечень ведёт пользователь, поэтому редактор — рядом с вводом,
              а не в отдельном разделе настроек. */}
          <button
            onClick={() => setKindsOpen(true)}
            title="Виды ограничений: добавить, переименовать, перекрасить"
            className="flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-bold text-slate-600 hover:bg-slate-50 transition-colors shrink-0"
          >
            <Settings2 size={14} /> Виды
          </button>

          {viewMode === 'grid' && (
            <button
              onClick={openManualCreate}
              disabled={selectedId === ''}
              title={selectedId === '' ? 'Сначала выберите объект' : 'Добавить ограничение'}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-black hover:bg-blue-700 transition-colors disabled:opacity-50 shrink-0"
            >
              <Plus size={14} /> Добавить
            </button>
          )}
        </div>

        {/* Легенда видов ограничений (только встретившиеся) */}
        {legendItems.length > 0 && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-1 border-t border-slate-100">
            {legendItems.map((item) => (
              <div key={item.kind} className="flex items-center gap-1.5">
                <span className={cn('w-2.5 h-2.5 rounded-sm', getConstraintStyle(item.kind).dot)} />
                <span className="text-[11px] text-slate-600">
                  <span className="font-black">{item.abbreviation}</span> — {item.fullName}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {entities.length === 0 ? (
        <EmptyState label={scope ? 'Нет участников этого типа — добавьте назначения для выбранных курсов' : 'Нет доступных сущностей'} />
      ) : viewMode === 'gantt' ? (
        <ConstraintsGanttEditor
          entities={timelineEntities}
          constraints={typeList}
          entityIdOf={entityIdOf}
          startDate={startDate}
          endDate={endDate}
          onCreate={createWholeDay}
          onDelete={deleteById}
          onChanged={refresh}
        />
      ) : selectedEntity ? (
        <ConstraintsGridSchedule
          constraints={entityConstraints}
          startDate={startDate}
          endDate={endDate}
          entityLabel={selectedEntity.name}
          onCellSelect={handleCellSelect}
          onPaintCreate={handlePaint}
          onErase={handleErase}
        />
      ) : (
        <EmptyState label="Выберите объект для просмотра ограничений" />
      )}

      {/* Список ограничений выбранной сущности с удалением (только в режиме «Сетка») */}
      {viewMode === 'grid' && selectedEntity && entityConstraints.length > 0 && (
        <div className="bg-white border border-slate-100 rounded-xl p-4 shadow-sm">
          <h3 className="text-xs font-black uppercase tracking-wider text-slate-500 mb-3">
            Ограничения · {selectedEntity.name}
          </h3>
          <ul className="space-y-1.5">
            {constraintGroups.map((g) => {
              const c = g.sample;
              const dot = getConstraintStyle(c.kindOfConstraint).dot;
              const rangeText = `${format(parseISO(g.minStart), 'dd.MM.yyyy')} – ${format(parseISO(g.maxEnd), 'dd.MM.yyyy')}`;

              // Одиночная запись (в т.ч. диапазон-«отпуск») — как раньше, без сворачивания.
              if (g.count === 1) {
                return (
                  <li key={g.key} className="flex items-center gap-3 text-xs bg-slate-50 rounded-lg px-3 py-2">
                    <span className={cn('w-2.5 h-2.5 rounded-sm shrink-0', dot)} />
                    <span className="font-black text-slate-800 shrink-0">{c.abbreviation}</span>
                    <span className="text-slate-600 shrink-0">{c.fullName}</span>
                    {c.timeSlot && <span className="text-slate-400 shrink-0">· {c.timeSlot}</span>}
                    <span className="text-slate-500 font-mono shrink-0">{rangeText}</span>
                    {c.description && <span className="text-slate-400 truncate flex-1">{c.description}</span>}
                    <button
                      onClick={() => handleDelete(c.id)}
                      title="Удалить ограничение"
                      className="ml-auto p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors shrink-0"
                    >
                      <Trash2 size={14} />
                    </button>
                  </li>
                );
              }

              // Группа одинаковых (вид+пара+описание) — заголовок со счётчиком + разворот к датам.
              const open = expandedGroups.has(g.key);
              const groupLabel = `${c.abbreviation} · ${c.timeSlot ?? 'весь день'} · ${g.weekday != null ? WEEKDAY_ADVERB[g.weekday] : rangeText}`;
              return (
                <li key={g.key} className="bg-slate-50 rounded-lg overflow-hidden">
                  <div className="flex items-center gap-3 text-xs px-3 py-2">
                    <button onClick={() => toggleGroup(g.key)} className="shrink-0 text-slate-400 hover:text-slate-600">
                      {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                    </button>
                    <span className={cn('w-2.5 h-2.5 rounded-sm shrink-0', dot)} />
                    <span className="font-black text-slate-800 shrink-0">{c.abbreviation}</span>
                    <span className="text-slate-600 shrink-0">{c.fullName}</span>
                    {c.timeSlot && <span className="text-slate-400 shrink-0">· {c.timeSlot}</span>}
                    <span className="text-slate-500 shrink-0">
                      {g.weekday != null ? WEEKDAY_ADVERB[g.weekday] : rangeText}
                      <span className="text-slate-400"> · {g.count}</span>
                    </span>
                    {g.weekday != null && <span className="text-slate-400 font-mono shrink-0 hidden md:inline">{rangeText}</span>}
                    {c.description && <span className="text-slate-400 truncate flex-1">{c.description}</span>}
                    <button
                      onClick={() => handleDeleteGroup(g.items.map((x) => x.id), groupLabel)}
                      title="Удалить все записи группы"
                      className="ml-auto p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors shrink-0"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                  {open && (
                    <ul className="pb-1.5 pl-9 pr-3 space-y-1">
                      {g.items.map((it) => (
                        <li key={it.id} className="flex items-center gap-3 text-xs text-slate-500">
                          <span className="font-mono shrink-0">
                            {format(parseISO(it.startDate), 'dd.MM.yyyy')}
                            {it.startDate !== it.endDate && ` – ${format(parseISO(it.endDate), 'dd.MM.yyyy')}`}
                          </span>
                          <button
                            onClick={() => handleDelete(it.id)}
                            title="Удалить эту запись"
                            className="ml-auto p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors shrink-0"
                          >
                            <Trash2 size={12} />
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {kindsOpen && <ConstraintKindsModal onClose={() => setKindsOpen(false)} />}

      {showModal && selectedEntity && (
        <ConstraintFormModal
          entityType={filterType}
          entityId={selectedEntity.id}
          entityLabel={selectedEntity.name}
          defaultStartDate={modalPreset?.startDate ?? startStr}
          defaultEndDate={modalPreset?.endDate ?? startStr}
          defaultTimeSlot={modalPreset?.timeSlot}
          onClose={closeModal}
          onSaved={() => { closeModal(); refresh(); }}
        />
      )}
    </div>
  );
};

const FilterBtn = ({ active, onClick, icon: Icon, label }: { active: boolean; onClick: () => void; icon: React.ElementType; label: string }) => (
  <button
    onClick={onClick}
    className={cn(
      'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-black transition-colors',
      active ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
    )}
  >
    <Icon size={14} /> {label}
  </button>
);

const EmptyState = ({ label }: { label: string }) => (
  <div className="bg-white border border-slate-100 rounded-xl p-8 shadow-sm text-center">
    <div className="flex flex-col items-center gap-3">
      <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
        <ShieldAlert className="text-slate-400" size={24} />
      </div>
      <p className="text-sm font-bold text-slate-900">{label}</p>
    </div>
  </div>
);
