import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ScheduledLessonDto, StudyPeriodDto } from '../../../types/api';
import { ScheduleSessionDto, PlacementBoardDto, BoardLessonDto, OrderFinding } from '../../../types/cqrs';
import { CQRSService, dateUtils } from '../../../services/cqrsApiService';
import { ScheduleService } from '../../../services/apiServices';
import { useEntityConstraints } from '../../constraints/useEntityConstraints';
import { useEducatorPriority } from '../../schedule/useEducatorPriority';
import { AcademicGridSchedule } from '../../schedule/components/AcademicGridSchedule';
import { kindStyleOf, KIND_STYLES } from '../../schedule/kindStyles';
import { Users, UserSquare2, ChevronRight, ChevronDown, Loader2, Lock, X, Trash2 } from 'lucide-react';
import { cn } from '../../../utils/cn';

type ViewMode = 'group' | 'educator';

interface Props {
  period: StudyPeriodDto;
  courseIds: number[];
}

/**
 * Рабочее место ручной раскладки (Фича 2, Фаза B).
 *
 * Палитра, счётчики и дерево строятся из «доски раскладки» (бэк, PlacementBoardDto):
 * сущность → дисциплина → занятие со счётчиками total/placed/unplaced. Показываются ВСЕ
 * сущности курсов (в т.ч. с полностью размещённым/сгенерированным расписанием), а не только
 * те, у кого остались неразмещённые. Сетка выбранной сущности рисуется из schedule_view.
 */
export const ManualPlacementWorkspace: React.FC<Props> = ({ period, courseIds }) => {
  const [session, setSession] = useState<ScheduleSessionDto | null>(null);
  const [board, setBoard] = useState<PlacementBoardDto | null>(null);
  const [lessons, setLessons] = useState<ScheduledLessonDto[]>([]);
  const [grid, setGrid] = useState<Record<string, ScheduledLessonDto[]>>({});
  const [loading, setLoading] = useState(false);

  const [viewMode, setViewMode] = useState<ViewMode>('group');
  const [selectedEntity, setSelectedEntity] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  // Свёрнутые/развёрнутые группы-дисциплины внутри развёрнутой сущности (ключ "entKey_abbr") —
  // по умолчанию свёрнуты, как и сущности верхнего уровня, чтобы видеть только счётчики.
  const [expandedDisciplines, setExpandedDisciplines] = useState<Set<string>>(new Set());

  // Выбранное неразмещённое занятие из палитры (Фича 2, Фаза B — B5) и ошибка
  // последней попытки установки (409 конфликт ресурса).
  const [selectedUnplaced, setSelectedUnplaced] = useState<BoardLessonDto | null>(null);
  const [placeError, setPlaceError] = useState<string | null>(null);

  // Находки правила порядка изучения: placementId → что не так (раньше своей лекции /
  // слишком далеко после неё). Сетка штрихует занятие красным или салатовым.
  const [orderViolations, setOrderViolations] = useState<Map<string, OrderFinding>>(new Map());

  // Загрузка размещений периода для СЕТКИ (ручные пины проецируются в schedule_view).
  const reloadSchedule = useCallback(async () => {
    const result = await ScheduleService.loadExisting(period.startDate, period.endDate);
    if (result.status === 'loaded') {
      setLessons(result.lessons);
      setGrid(result.grid || {});
    } else {
      setLessons([]);
      setGrid({});
    }
  }, [period.startDate, period.endDate]);

  // Нарушения порядка изучения — ОДНИМ запросом на всё расписание (не на каждое наведение).
  // Занятие, стоящее раньше предшествующей ему по плану лекции. Подсказка, а не запрет:
  // сетка лишь штрихует нарушителя, перенос и установка не блокируются.
  const reloadOrderViolations = useCallback(async (sessionId: string) => {
    try {
      const violations = await CQRSService.getOrderViolations(sessionId);
      setOrderViolations(new Map(
        violations.map((v) => [v.placementId, { kind: v.kind, gapDays: v.gapDays }])
      ));
    } catch (e) {
      console.error('Не удалось загрузить находки порядка изучения:', e);
      setOrderViolations(new Map());
    }
  }, []);

  // Загрузка доски (палитра + счётчики). Command Side читается напрямую — в отличие от
  // schedule_view, тут нет асинхронной проекции: свежие данные сразу после commit.
  // Ось зависит от режима (группы/преподаватели).
  const reloadBoard = useCallback(async (sessionId: string): Promise<PlacementBoardDto> => {
    const axis = viewMode === 'group' ? 'GROUP' : 'EDUCATOR';
    const data = await CQRSService.getPlacementBoard(sessionId, courseIds, axis);
    setBoard(data);
    return data;
  }, [courseIds, viewMode]);

  const entities = board?.entities ?? [];

  const selectedEntityData = useMemo(
    () => (selectedEntity ? entities.find((e) => e.name === selectedEntity) ?? null : null),
    [entities, selectedEntity]
  );
  const entityId = selectedEntityData?.id;

  const rootEntityType: 'GROUP' | 'EDUCATOR' = viewMode === 'group' ? 'GROUP' : 'EDUCATOR';

  // Кандидат на установку — со СТАБИЛЬНОЙ ссылкой. `AcademicGridSchedule` держит его в
  // зависимостях эффекта подсветки, поэтому литерал объекта прямо в JSX (новая ссылка на
  // каждый рендер) заставлял перезапрашивать те же ячейки после любого обновления состояния
  // — на одну установку уходило два одинаковых запроса placement-options по ~140 мс.
  const placementCandidate = useMemo(
    () => (selectedUnplaced
      ? { assignmentId: selectedUnplaced.assignmentId, rootEntityType, rootEntityId: entityId }
      : null),
    [selectedUnplaced, rootEntityType, entityId]
  );

  // Следующее занятие в очереди той же сущности после успешной установки: сперва пробуем
  // продолжить ту же дисциплину (следующая позиция), иначе — первое из оставшихся.
  const pickNextUnplaced = useCallback((
    entityName: string, justPlaced: BoardLessonDto, freshBoard: PlacementBoardDto
  ): BoardLessonDto | null => {
    const ent = freshBoard.entities.find((e) => e.name === entityName);
    if (!ent) return null;
    const unplaced = ent.disciplines
      .flatMap((d) => d.lessons)
      .filter((l) => !l.placementId);
    if (unplaced.length === 0) return null;
    const sameDiscipline = unplaced
      .filter((l) => l.courseId === justPlaced.courseId)
      .sort((a, b) => a.position - b.position);
    if (sameDiscipline.length > 0) return sameDiscipline[0];
    return [...unplaced].sort((a, b) => a.courseId - b.courseId || a.position - b.position)[0];
  }, []);

  // Установка занятия из палитры в кликнутую (зелёную) ячейку сетки (B5).
  const handlePlace = useCallback(async (assignmentId: number, date: string, slot: string) => {
    if (!session) return;
    setPlaceError(null);
    const justPlaced = selectedUnplaced;
    try {
      await CQRSService.createPlacement(session.id, {
        assignmentId, date, slot, studyPeriodId: period.id,
      });
      const freshBoard = await reloadBoard(session.id);
      // Продолжаем очередь: сразу выбираем следующее занятие, не заставляя лишний раз кликать в палитру.
      const next = (selectedEntity && justPlaced) ? pickNextUnplaced(selectedEntity, justPlaced, freshBoard) : null;
      setSelectedUnplaced(next);
      // Query Side (schedule_view) синхронизируется асинхронно — как и у переноса
      // (см. AcademicGridSchedule.handleCellMove), даём ему секунду перед перезагрузкой сетки,
      // иначе только что размещённое занятие не появится в сетке до следующего действия.
      setTimeout(() => { reloadSchedule(); }, 1000);
    } catch (e: any) {
      // Конфликт слота (409) — не сбрасываем выбор, чтобы можно было попробовать другую ячейку.
      setPlaceError(e?.response?.data?.message || 'Не удалось разместить занятие: слот занят.');
    }
  }, [session, period.id, reloadBoard, reloadSchedule, selectedEntity, selectedUnplaced, pickNextUnplaced]);

  // Закрепить/открепить занятие (пин). chainPlacementIds — эффективная цепочка с учётом
  // временного разрыва (detachedBoundaries) на сетке; см. AcademicGridSchedule.buildChain.
  const handleToggleLock = useCallback(async (lesson: ScheduledLessonDto, chainPlacementIds?: string[]) => {
    if (!lesson.placementId) return;
    const newLocked = !lesson.locked;
    // Множество затрагиваемых размещений (вся цепочка или только якорь).
    const ids = new Set(chainPlacementIds && chainPlacementIds.length ? chainPlacementIds : [lesson.placementId]);
    // ОПТИМИСТИЧНО обновляем локально: проекция Query Side асинхронна, а фикс. пауза в 1с
    // ненадёжна — иначе иконка «откатывалась» бы к старому состоянию, и клик выглядел как no-op.
    const flip = (l: ScheduledLessonDto) => (l.placementId && ids.has(l.placementId) ? { ...l, locked: newLocked } : l);
    setLessons((prev) => prev.map(flip));
    setGrid((prev) => {
      const next: Record<string, ScheduledLessonDto[]> = {};
      for (const [k, arr] of Object.entries(prev)) next[k] = arr.map(flip);
      return next;
    });
    try {
      await CQRSService.setLock(lesson.placementId, newLocked, chainPlacementIds);
      // НЕ перезагружаем сетку: setLock уже персистит (ответ 200), а проекция в schedule_view
      // асинхронна (@Async, AFTER_COMMIT) и НЕ успевает за фикс. паузу → перезагрузка затёрла бы
      // оптимистичный флип устаревшим значением (эффект «сработало на секунду и откатилось»).
    } catch (e) {
      console.error('Не удалось изменить закрепление:', e);
      reloadSchedule(); // откат к состоянию сервера
    }
  }, [reloadSchedule]);

  // Снять размещение (вернуть занятие в палитру).
  const handleRemove = useCallback(async (placementId: string) => {
    if (!session) return;
    await CQRSService.deletePlacement(placementId);
    await reloadBoard(session.id);
    // Та же асинхронная задержка Query Side, что и при установке/переносе.
    setTimeout(() => { reloadSchedule(); }, 1000);
  }, [session, reloadBoard, reloadSchedule]);

  // Массовое снятие: убрать все размещения выбранной дисциплины (вернуть в очередь).
  // Bulk-версия крестика — отдельного эндпоинта нет, снимаем через тот же deletePlacement
  // по набору placementId (объёмы малы). Перезагрузка палитры/сетки — один раз в конце.
  const handleRemoveMany = useCallback(async (placementIds: string[]) => {
    if (!session || placementIds.length === 0) return;
    const unique = Array.from(new Set(placementIds));
    await Promise.all(unique.map((id) => CQRSService.deletePlacement(id)));
    await reloadBoard(session.id);
    setTimeout(() => { reloadSchedule(); }, 1000);
  }, [session, reloadBoard, reloadSchedule]);

  // Бутстрап: сессия периода → сетка (доску грузит отдельный эффект по session/оси).
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    CQRSService.getSessionForPeriod(period.id)
      .then(async (s) => {
        if (cancelled) return;
        setSession(s);
        await reloadSchedule();
      })
      .catch((e) => console.error('Не удалось открыть сессию периода:', e))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [period.id, reloadSchedule]);

  // Доска: перезагружается при появлении сессии и при смене оси (viewMode) — reloadBoard
  // зависит от viewMode/courseIds.
  //
  // Зависимость — id сессии, а НЕ объект: после переноса хост переспрашивает сессию
  // (`getSession().then(setSession)`), и подписка на объект давала вторую, лишнюю загрузку
  // доски. Доска весит ~2.3 МБ, так что дубль стоил ~350 мс на каждый перенос.
  const sessionId = session?.id;
  useEffect(() => {
    if (!sessionId) return;
    reloadBoard(sessionId).catch((e) => console.error('Не удалось загрузить доску раскладки:', e));
  }, [sessionId, reloadBoard]);

  // Нарушения порядка — вслед за сеткой: `lessons` меняется после КАЖДОЙ мутации
  // (установка, снятие, перенос — все зовут reloadSchedule), поэтому одна подписка
  // покрывает все пути, и не нужно дёргать перезагрузку в каждом обработчике.
  useEffect(() => {
    if (!sessionId) return;
    reloadOrderViolations(sessionId);
  }, [sessionId, lessons, reloadOrderViolations]);

  const toggleExpand = (key: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  const toggleDiscipline = (key: string) => {
    setExpandedDisciplines((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  // Ограничения выбранной сущности — тот же хук, что и в разделе «Расписание».
  const { constraints } = useEntityConstraints(viewMode, entityId);
  // Приоритеты преподавателя (дни/пары) — подсветка «замороженных» колонок в виде «преподаватель».
  const educatorPriority = useEducatorPriority(entityId, viewMode === 'educator');

  // Находки порядка, которые реально показываем в сетке.
  //
  // Когда из палитры выбрано конкретное занятие, диспетчер работает с ОДНОЙ дисциплиной —
  // штриховка нарушений по всем остальным только мешает читать сетку (её там может быть много,
  // и к текущему решению она отношения не имеет). Поэтому на время выбора оставляем находки
  // только по дисциплине выбранного занятия; сняли выбор — снова видно всё расписание.
  // Это чисто presentational-фильтр: сами находки бэк считает по всей сессии.
  const visibleOrderViolations = useMemo(() => {
    if (!selectedUnplaced) return orderViolations;
    // Ключ — ПОЛНОЕ имя дисциплины (уникально), а не аббревиатура: у разных дисциплин
    // аббревиатуры могут совпасть, и подсветка перескочила бы на чужую.
    const name = selectedEntityData?.disciplines
      .find((d) => d.courseId === selectedUnplaced.courseId)?.name;
    if (!name) return orderViolations;
    const sameDiscipline = new Set(
      lessons.filter((l) => l.disciplineName === name && l.placementId)
        .map((l) => l.placementId as string)
    );
    return new Map([...orderViolations].filter(([placementId]) => sameDiscipline.has(placementId)));
  }, [orderViolations, selectedUnplaced, selectedEntityData, lessons]);

  // «Размещено» выбранной сущности по дисциплинам — из доски (только занятия с placementId).
  const placedByDiscipline = useMemo(() => {
    if (!selectedEntityData) return [];
    return selectedEntityData.disciplines
      .map((d) => ({ abbr: d.abbreviation, list: d.lessons.filter((l) => l.placementId) }))
      .filter((d) => d.list.length > 0);
  }, [selectedEntityData]);

  const switchMode = (mode: ViewMode) => {
    setViewMode(mode);
    setSelectedEntity(null);
    setSelectedUnplaced(null);
  };

  return (
    <div className="space-y-3">
      {/* Шапка: режим + счётчики (всего / размещено / не размещено) */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5">
          <ModeBtn active={viewMode === 'group'} onClick={() => switchMode('group')} icon={Users} label="Группы" />
          <ModeBtn active={viewMode === 'educator'} onClick={() => switchMode('educator')} icon={UserSquare2} label="Преподаватели" />
        </div>
        {/* Легенда видов занятий — из единого источника подсветки (kindStyles), тот же, что у сетки. */}
        <div className="flex items-center gap-2 text-[10px] text-slate-400">
          {Object.entries(KIND_STYLES).map(([group, s]) => (
            <span key={group} className="flex items-center gap-1">
              <span className={cn('w-2 h-2 rounded-full', s.dot)} />
              {s.label}
            </span>
          ))}
        </div>
        <div className="text-xs text-slate-500 flex items-center gap-2">
          <span>Всего: <span className="font-bold text-slate-700">{board?.total ?? 0}</span></span>
          <span className="text-slate-300">·</span>
          <span>Размещено: <span className="font-bold text-emerald-600">{board?.placed ?? 0}</span></span>
          <span className="text-slate-300">·</span>
          <span>Не размещено: <span className="font-bold text-blue-600">{board?.unplaced ?? 0}</span></span>
        </div>
      </div>

      {loading ? (
        <div className="py-16 text-center text-slate-400 text-sm flex items-center justify-center gap-2">
          <Loader2 size={16} className="animate-spin" /> Загрузка рабочего места...
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-[220px_1fr] gap-3">
          {/* Палитра-дерево (все сущности со счётчиками) */}
          <div className="bg-white border border-slate-200 rounded-xl p-2 max-h-[70vh] overflow-auto">
            <div className="text-[10px] font-black uppercase tracking-tight text-slate-400 px-2 py-1">
              {viewMode === 'group' ? 'Группы' : 'Преподаватели'} · дисциплины
            </div>
            {board === null ? (
              <div className="px-2 py-6 text-center text-xs text-slate-400 flex items-center justify-center gap-2">
                <Loader2 size={13} className="animate-spin" /> Загрузка…
              </div>
            ) : entities.length === 0 ? (
              <div className="px-2 py-6 text-center text-xs text-slate-400">
                Нет назначений для выбранных курсов
              </div>
            ) : (
              entities.map((ent) => {
                const entKey = `ent_${ent.name}`;
                const isOpen = expanded.has(entKey);
                const isSelected = selectedEntity === ent.name;
                return (
                  <div key={entKey} className="mb-0.5">
                    <button
                      onClick={() => { setSelectedEntity(ent.name); setSelectedUnplaced(null); toggleExpand(entKey); }}
                      className={cn(
                        'w-full flex items-center gap-1 px-2 py-1.5 rounded-lg text-left text-xs font-bold transition-colors',
                        isSelected ? 'bg-blue-50 text-blue-800' : 'hover:bg-slate-50 text-slate-700'
                      )}
                    >
                      {isOpen ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                      <span className="flex-1 truncate">{ent.name}</span>
                      <CountBadge placed={ent.placed} total={ent.total} />
                    </button>
                    {isOpen && (
                      <div className="pl-4 py-0.5">
                        {ent.disciplines.map((disc) => {
                          const discKey = `${entKey}_${disc.abbreviation}`;
                          const discOpen = expandedDisciplines.has(discKey);
                          const unplacedItems = disc.lessons.filter((l) => !l.placementId);
                          return (
                            <div key={disc.abbreviation} className="mb-1">
                              <button
                                type="button"
                                onClick={() => toggleDiscipline(discKey)}
                                title={disc.name}
                                className="w-full flex items-center gap-1 px-2 py-0.5 rounded text-left hover:bg-slate-50"
                              >
                                {discOpen ? <ChevronDown size={11} className="text-slate-400" /> : <ChevronRight size={11} className="text-slate-400" />}
                                <span className="flex-1 text-[10px] font-black text-slate-500 truncate">{disc.abbreviation}</span>
                                <CountBadge placed={disc.placed} total={disc.total} small />
                              </button>
                              {discOpen && (
                                unplacedItems.length === 0 ? (
                                  <div className="pl-4 pr-2 py-1 text-[10px] text-emerald-600">✓ всё размещено</div>
                                ) : (
                                  unplacedItems.map((u) => {
                                    const isPicked = selectedUnplaced?.assignmentId === u.assignmentId;
                                    // Подсветка вида занятия — из того же источника, что и сетка
                                    // (`kindStyles.ts`): лекцию видно в очереди так же, как в расписании.
                                    const ks = kindStyleOf(u.kindOfStudy);
                                    return (
                                      <button
                                        key={u.assignmentId}
                                        type="button"
                                        title={`${disc.name} · ${u.kindOfStudyAbbr} · Т.${u.themeNumber || '—'} · Группы: ${u.groupNames.join(', ') || '—'}`}
                                        onClick={() => {
                                          // Установка привязана к сетке выбранной сущности — выбор
                                          // занятия из другой (только развёрнутой) сущности переключает грид на неё.
                                          setSelectedEntity(ent.name);
                                          setPlaceError(null);
                                          setSelectedUnplaced(isPicked ? null : u);
                                        }}
                                        className={cn(
                                          'w-full flex flex-col items-start gap-0.5 px-2 py-1 rounded-md text-[11px] text-left transition-colors',
                                          isPicked
                                            ? 'bg-emerald-100 text-emerald-800 ring-1 ring-inset ring-emerald-400'
                                            : 'text-slate-600 hover:bg-amber-50'
                                        )}
                                      >
                                        <div className="flex items-center gap-1.5 w-full">
                                          <span className={cn(
                                            'shrink-0 px-1 py-px rounded font-bold',
                                            isPicked ? 'bg-white/70 text-emerald-800' : ks.chip
                                          )}>
                                            {u.kindOfStudyAbbr}/{u.position}
                                          </span>
                                          <span className="truncate">Т.{u.themeNumber || '—'}</span>
                                        </div>
                                        {/* Группы-участники занятия (важно в виде преподавателя и для потоков). */}
                                        <div className="text-[9px] leading-tight text-slate-400 truncate w-full">
                                          {u.groupNames.join(', ') || '—'}
                                        </div>
                                      </button>
                                    );
                                  })
                                )
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })
            )}

            {/* Размещено (для выбранной сущности) */}
            {placedByDiscipline.length > 0 && (
              <div className="mt-2 pt-2 border-t border-slate-100">
                <div className="text-[10px] font-black uppercase tracking-tight text-amber-500 px-2 py-1 flex items-center gap-1">
                  <Lock size={11} /> Размещено · {selectedEntity}
                </div>
                {placedByDiscipline.map(({ abbr, list }) => {
                  const ids = list.map((l) => l.placementId).filter(Boolean) as string[];
                  const placedKey = `placed_${abbr}`;
                  const placedOpen = expandedDisciplines.has(placedKey);
                  return (
                    <div key={abbr} className="mb-1">
                      {/* Заголовок дисциплины (сворачивает список) + массовое снятие.
                          Свёртка и «снять всё» — соседние кнопки, чтобы не вкладывать button в button. */}
                      <div className="flex items-center gap-1.5 px-2 py-0.5">
                        <button
                          type="button"
                          onClick={() => toggleDiscipline(placedKey)}
                          className="flex-1 flex items-center gap-1 text-left min-w-0 rounded hover:bg-slate-50"
                        >
                          {placedOpen ? <ChevronDown size={11} className="text-slate-400" /> : <ChevronRight size={11} className="text-slate-400" />}
                          <span className="flex-1 text-[10px] font-black text-slate-500 truncate">{abbr}</span>
                          <span className="text-[10px] text-slate-400">{list.length}</span>
                        </button>
                        <button
                          type="button"
                          title={`Снять все занятия дисциплины «${abbr}» (вернуть в очередь)`}
                          onClick={() => {
                            if (ids.length === 0) return;
                            if (window.confirm(
                              `Снять с расписания все занятия дисциплины «${abbr}» (${ids.length})?\nЗанятия вернутся в очередь на размещение; учебный план не изменится.`
                            )) {
                              handleRemoveMany(ids);
                            }
                          }}
                          className="text-slate-300 hover:text-red-500 transition-colors shrink-0"
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                      {/* Занятия дисциплины (аббревиатура — в заголовке выше) */}
                      {placedOpen && list.map((l) => (
                        <div key={l.placementId} className="flex items-center gap-1.5 pl-4 pr-2 py-1 text-[11px] text-slate-500">
                          <span className={cn('shrink-0 px-1 py-px rounded font-bold', kindStyleOf(l.kindOfStudy).chip)}>
                            {l.kindOfStudyAbbr}
                          </span>
                          <span className="truncate">Т.{l.themeNumber || '—'}</span>
                          <span className="ml-auto text-[10px] text-amber-600">{l.date}</span>
                          <button
                            type="button"
                            title="Снять размещение (вернуть в палитру)"
                            onClick={() => l.placementId && handleRemove(l.placementId)}
                            className="text-slate-300 hover:text-red-500 transition-colors shrink-0"
                          >
                            <X size={12} />
                          </button>
                        </div>
                      ))}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Сетка выбранной сущности */}
          <div className="space-y-2">
            {placeError && (
              <div className="px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex items-center justify-between gap-2">
                <span>{placeError}</span>
                <button onClick={() => setPlaceError(null)} className="text-red-400 hover:text-red-600 shrink-0">
                  <X size={13} />
                </button>
              </div>
            )}
            {selectedEntity ? (
              <AcademicGridSchedule
                lessons={lessons}
                grid={grid}
                constraints={constraints}
                filterType={viewMode}
                selectedValue={selectedEntity}
                startDate={dateUtils.parseDate(period.startDate)}
                endDate={dateUtils.parseDate(period.endDate)}
                isEditMode
                sessionId={session?.id}
                currentVersion={session?.version || 0}
                rootEntityType={rootEntityType}
                rootEntityId={entityId}
                educatorPriority={educatorPriority}
                onMoveLesson={() => {
                  reloadSchedule();
                  if (session) reloadBoard(session.id).catch(() => {});
                  // Версия сессии освежается на случай, если она изменилась (например,
                  // генерацией в соседней вкладке). Эффект загрузки доски подписан на id
                  // сессии, а не на объект, поэтому повторной загрузки доски это не вызовет.
                  if (session) CQRSService.getSession(session.id).then(setSession).catch(() => {});
                }}
                onToggleLock={handleToggleLock}
                orderViolations={visibleOrderViolations}
                placementCandidate={placementCandidate}
                studyPeriodId={period.id}
                onPlace={handlePlace}
                onExitPlacementCandidate={() => setSelectedUnplaced(null)}
              />
            ) : (
              <div className="bg-white border border-slate-200 rounded-xl p-10 text-center text-sm text-slate-400">
                Выберите {viewMode === 'group' ? 'группу' : 'преподавателя'} в палитре слева
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

const ModeBtn = ({ active, onClick, icon: Icon, label }: any) => (
  <button
    onClick={onClick}
    className={cn(
      'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[10px] font-black uppercase tracking-tight transition-all',
      active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:bg-white/50'
    )}
  >
    <Icon size={12} />
    {label}
  </button>
);

/** Бейдж «размещено/всего»: зелёный при полном размещении, синий пока есть очередь. */
const CountBadge = ({ placed, total, small }: { placed: number; total: number; small?: boolean }) => {
  const done = total > 0 && placed >= total;
  return (
    <span
      className={cn(
        'shrink-0 tabular-nums font-bold',
        small ? 'text-[9px]' : 'text-[10px]',
        done ? 'text-emerald-600' : 'text-blue-600'
      )}
    >
      {placed}/{total}
    </span>
  );
};
