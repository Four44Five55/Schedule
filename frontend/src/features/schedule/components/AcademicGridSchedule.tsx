import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {ConstraintDto, ScheduledLessonDto, TimeSlotPair} from '../../../types/api';
import {isWithinInterval, parseISO} from 'date-fns';
import {cn} from '../../../utils/cn';
import {AlertTriangle, Link2, Lock, LockOpen, Unlink, X} from 'lucide-react';
import {CQRSService} from '../../../services/cqrsApiService';
import {CurriculumService} from '../../../services/apiServices';
import {AcademicGridShell, GridCellContext, SLOTS} from '../../../components/grid/AcademicGridShell';

interface AcademicGridScheduleProps {
  lessons: ScheduledLessonDto[];
  grid: Record<string, ScheduledLessonDto[]>;
  filterType: 'group' | 'educator' | 'auditorium';
  selectedValue: string;
  startDate: Date;
  endDate: Date;
  constraints?: ConstraintDto[];
  isEditMode?: boolean;
  sessionId?: string;
  currentVersion?: number;
  rootEntityType?: 'GROUP' | 'EDUCATOR' | 'AUDITORIUM';
  rootEntityId?: number;
  onMoveLesson?: (placementId: string) => void;
  // Закрепить/открепить занятие (пин, Фича 2). Передаётся текущее занятие + id размещений
  // эффективной цепочки (с учётом временного разрыва detachedBoundaries на этой сетке) —
  // хост дёргает API с этим подмножеством и перезагружает расписание.
  onToggleLock?: (lesson: ScheduledLessonDto, chainPlacementIds?: string[]) => void;
  // Установка не размещённого занятия из палитры (Фича 2, Фаза B): третья стратегия
  // выбора поверх того же механизма подсветки/клика, что и перенос. Host передаёт
  // выбранное назначение — сетка подсвечивает доступные ячейки и на клике зовёт onPlace.
  placementCandidate?: {
    assignmentId: number;
    rootEntityType: 'GROUP' | 'EDUCATOR' | 'AUDITORIUM';
    rootEntityId?: number;
  } | null;
  studyPeriodId?: number;
  onPlace?: (assignmentId: number, date: string, slot: TimeSlotPair) => void | Promise<void>;
  // Выход из режима установки из палитры: хост сбрасывает выбранное занятие очереди,
  // когда пользователь кликает существующее занятие для переноса (иначе клик заблокирован
  // активным placementCandidate). Позволяет двигать раскладку, не удаляя занятие.
  onExitPlacementCandidate?: () => void;
  // Потолок высоты сетки (Tailwind-класс) — пробрасывается в AcademicGridShell.
  // Позволяет хосту растянуть сетку до низа экрана вместо дефолтных 700px.
  maxHeightClass?: string;
  // Убрать тёмный тулбар шелла (заголовок/зум/развернуть). Зум остаётся на Ctrl+колесе,
  // «Развернуть» — плавающей иконкой в углу сетки.
  chromeless?: boolean;
}

export const AcademicGridSchedule: React.FC<AcademicGridScheduleProps> = ({
                                                                            lessons,
                                                                            grid,
                                                                            filterType,
                                                                            selectedValue,
                                                                            startDate,
                                                                            endDate,
                                                                            constraints = [],
                                                                            isEditMode = false,
                                                                            sessionId,
                                                                            currentVersion = 0,
                                                                            rootEntityType,
                                                                            rootEntityId,
                                                                            onMoveLesson,
                                                                            onToggleLock,
                                                                            placementCandidate,
                                                                            studyPeriodId,
                                                                            onPlace,
                                                                            onExitPlacementCandidate,
                                                                            maxHeightClass,
                                                                            chromeless
                                                                          }) => {

  // Пины (Фича 2) активны только если хост передал обработчик закрепления —
  // в разделе «Расписание» он не передаётся, и сетка выглядит как раньше.
  const pinningEnabled = !!onToggleLock;

  // Перенос «по сетке»: выбираем занятие → подсвечиваем зелёным доступные ячейки →
  // клик по зелёной ячейке переносит занятие туда. Без модального окна.
  const [selectedLesson, setSelectedLesson] = useState<ScheduledLessonDto | null>(null);
  // Подсвечиваемые зелёным ячейки → стартовый слот головы для переноса в эту цель.
  // Для цепочки одна цель раскрывается в весь «след» (голова + хвосты), и любой
  // его ячейке сопоставлен один и тот же старт головы — чтобы клик по хвосту
  // (напр. по 4-й паре в следе 3-4) переносил цепочку правильно.
  const [moveTargets, setMoveTargets] = useState<Map<string, TimeSlotPair>>(new Map());
  const [loadingTargets, setLoadingTargets] = useState(false);
  const [moving, setMoving] = useState(false);
  const [hintVisible, setHintVisible] = useState(true);
  // Дисциплина, подсвеченная наведением (когда занятие ещё не выбрано).
  // Наведённое занятие (а не только имя дисциплины) — нужно, чтобы знать его группы
  // для сужения подсветки у ресурса (преподаватель/аудитория) до общих групп.
  const [hoveredLesson, setHoveredLesson] = useState<ScheduledLessonDto | null>(null);
  // Временно разомкнутые стыки цепочек (ключ "date_topSlotId") — только для переноса,
  // план (SlotChain) не трогаем. Сбрасываются при снятии выбора.
  const [detachedBoundaries, setDetachedBoundaries] = useState<Set<string>>(new Set());
  // placementId звеньев цепочки, выбранной для переноса (в порядке по времени).
  // Длина > 1 → переносим цепочкой; иначе — одиночный перенос.
  const [selectedChainIds, setSelectedChainIds] = useState<string[]>([]);

  const clearSelection = () => {
    setSelectedLesson(null);
    setHoveredLesson(null);
    setMoveTargets(new Map());
    setSelectedChainIds([]);
    setDetachedBoundaries(new Set()); // временные размыкания живут только на время выбора
  };

  const isSelectedLesson = (l: ScheduledLessonDto) =>
      !!selectedLesson &&
      selectedLesson.placementId === l.placementId &&
      selectedLesson.date === l.date &&
      selectedLesson.timeSlotPair === l.timeSlotPair;

  const handleLessonClick = (lesson: ScheduledLessonDto) => {
    if (!isEditMode || !sessionId || !onMoveLesson) return;
    // Если активен режим установки из палитры — клик по существующему занятию
    // означает «хочу двигать вот это»: выходим из установки (хост сбрасывает выбор
    // очереди) и продолжаем как обычный перенос. Так раскладку можно переносить,
    // не удаляя занятие и не ища его заново в неразмещённых.
    if (placementCandidate) onExitPlacementCandidate?.();
    // повторный клик по тому же занятию — снять выбор
    if (isSelectedLesson(lesson)) {
      clearSelection();
      return;
    }
    setSelectedLesson(lesson);
  };

  // Установка нового занятия (палитра) и перенос существующего — взаимоисключающие
  // режимы выбора одной и той же сетки; хост включает placementCandidate, мы гасим
  // внутренний выбор для переноса, чтобы не путать два режима одновременно.
  useEffect(() => {
    if (placementCandidate) {
      setSelectedLesson(null);
    }
  }, [placementCandidate]);

  // Смена просматриваемой сущности (группа/преподаватель/аудитория) сбрасывает выбор —
  // иначе подсветка и цели переноса «тянутся» от занятия предыдущей сущности, хотя сетка
  // уже другая. Сбрасываем всё, что привязано к выбору (setter'ы стабильны, deps не нужны).
  useEffect(() => {
    setSelectedLesson(null);
    setHoveredLesson(null);
    setMoveTargets(new Map());
    setSelectedChainIds([]);
    setDetachedBoundaries(new Set());
  }, [filterType, selectedValue]);

  // Подбор доступных ячеек вынесен ниже — после объявления buildChain
  // (от которого зависит), чтобы не словить temporal dead zone в массиве зависимостей.

  // Esc — отмена выбора.
  useEffect(() => {
    if (!selectedLesson) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') clearSelection(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selectedLesson]);

  // Подсказка-тост сама угасает через несколько секунд (зелёные ячейки остаются).
  // Каждое значимое изменение состояния (выбор/загрузка/перенос) снова показывает её.
  useEffect(() => {
    if (!selectedLesson) return;
    setHintVisible(true);
    const t = setTimeout(() => setHintVisible(false), 3500);
    return () => clearTimeout(t);
  }, [selectedLesson, loadingTargets, moving]);

  // Преподаватели выбранного занятия — для условия «препод скрыто занят».
  const selectedEducatorIds = useMemo(
    () => new Set(selectedLesson?.educatorIds ?? []),
    [selectedLesson]
  );

  // Ячейки, где заняты преподаватель(и) выбранного занятия — чтобы располагать его
  // компактно к остальным парам преподавателя. Считается из уже загруженного
  // расписания (полное, по всем группам), без обращения к бэкенду.
  const teacherBusyCells = useMemo(() => {
    const set = new Set<string>();
    if (!selectedLesson) return set;
    for (const l of lessons) {
      if (l.educatorIds.some((id) => selectedEducatorIds.has(id))) {
        set.add(`${l.date}_${l.timeSlotPair}`);
      }
    }
    return set;
  }, [selectedLesson, lessons, selectedEducatorIds]);

  // Эталонное занятие для подсветки: закреплённое выбором приоритетнее наведённого.
  // От него берём и дисциплину, и набор групп.
  const referenceLesson = selectedLesson ?? hoveredLesson;
  const activeDiscipline = referenceLesson?.disciplineName ?? null;
  // Группы эталона: у преподавателя/аудитории подсвечиваем занятия той же дисциплины
  // ТОЛЬКО если в них участвует хотя бы одна из этих групп (а не все занятия подряд).
  const activeGroupNames = useMemo(
    () => new Set(referenceLesson?.groupNames ?? []),
    [referenceLesson]
  );

  // Сцепки слотов (SlotChain) — пары соседних слотов, идущих единой цепочкой.
  // Храним как множество канонических ключей "minId-maxId" для O(1)-проверки.
  const [chainPairs, setChainPairs] = useState<Set<string>>(new Set());
  useEffect(() => {
    let cancelled = false;
    CurriculumService.getSlotChains()
      .then((chains) => {
        if (cancelled) return;
        const set = new Set<string>();
        for (const c of chains) {
          const a = c.slotA.id, b = c.slotB.id;
          set.add(`${Math.min(a, b)}-${Math.max(a, b)}`);
        }
        setChainPairs(set);
      })
      .catch(() => { if (!cancelled) setChainPairs(new Set()); });
    return () => { cancelled = true; };
  }, []);

  // Сцеплены ли два слота напрямую (в любом порядке).
  const areSlotsChained = (a?: number, b?: number) =>
    a != null && b != null && chainPairs.has(`${Math.min(a, b)}-${Math.max(a, b)}`);

  // Быстрый индекс «занятие выбранного ресурса по ячейке» — для поиска соседей
  // (звено цепочки между соседними по времени парами одного дня).
  const resourceLessonByCell = useMemo(() => {
    const map = new Map<string, ScheduledLessonDto>();
    for (const l of lessons) {
      const matches =
        filterType === 'group' ? l.groupNames.includes(selectedValue)
          : filterType === 'educator' ? l.educatorNames.includes(selectedValue)
            : l.auditoriumNames.includes(selectedValue);
      if (matches) map.set(`${l.date}_${l.timeSlotPair}`, l);
    }
    return map;
  }, [lessons, filterType, selectedValue]);

  // Собирает цепочку занятия (звенья сверху вниз по времени), идя по сцепленным
  // соседним парам того же дня. Останавливается на временно разомкнутом стыке.
  const buildChain = useCallback((lesson: ScheduledLessonDto): ScheduledLessonDto[] => {
    const date = lesson.date;
    const idx = SLOTS.findIndex((s) => s.id === lesson.timeSlotPair);
    if (idx < 0) return [lesson];

    const chained = (a?: ScheduledLessonDto, b?: ScheduledLessonDto) => {
      const x = a?.curriculumSlotId, y = b?.curriculumSlotId;
      return x != null && y != null && chainPairs.has(`${Math.min(x, y)}-${Math.max(x, y)}`);
    };

    const members = [lesson];
    // вверх
    let top = lesson, i = idx;
    while (i > 0) {
      const above = resourceLessonByCell.get(`${date}_${SLOTS[i - 1].id}`);
      if (above && chained(top, above) && !detachedBoundaries.has(`${date}_${SLOTS[i - 1].id}`)) {
        members.unshift(above); top = above; i--;
      } else break;
    }
    // вниз
    let bottom = lesson, j = idx;
    while (j < SLOTS.length - 1) {
      const below = resourceLessonByCell.get(`${date}_${SLOTS[j + 1].id}`);
      if (below && chained(bottom, below) && !detachedBoundaries.has(`${date}_${SLOTS[j].id}`)) {
        members.push(below); bottom = below; j++;
      } else break;
    }
    return members;
  }, [resourceLessonByCell, chainPairs, detachedBoundaries]);

  // Подбор доступных ячеек для выбранного занятия/цепочки (то, что подсветится зелёным).
  useEffect(() => {
    // Установка не размещённого занятия из палитры — отдельная, более простая ветка:
    // один слот, без раскрытия в «след» цепочки (цепочка размещений появляется только
    // когда оба звена уже стоят в сетке).
    if (placementCandidate) {
      if (!sessionId || !studyPeriodId) {
        setMoveTargets(new Map());
        return;
      }
      let cancelled = false;
      setLoadingTargets(true);
      setSelectedChainIds([]);
      CQRSService.getPlacementOptions(sessionId, {
        assignmentId: placementCandidate.assignmentId,
        rootEntityType: placementCandidate.rootEntityType,
        rootEntityId: placementCandidate.rootEntityId,
        studyPeriodId,
      })
        .then((options) => {
          if (cancelled) return;
          const targets = new Map<string, TimeSlotPair>();
          for (const o of options) targets.set(`${o.date}_${o.timeSlot}`, o.timeSlot);
          setMoveTargets(targets);
        })
        .catch(() => { if (!cancelled) setMoveTargets(new Map()); })
        .finally(() => { if (!cancelled) setLoadingTargets(false); });
      return () => { cancelled = true; };
    }

    if (!selectedLesson || !sessionId || !selectedLesson.placementId) {
      setMoveTargets(new Map());
      setSelectedChainIds([]);
      return;
    }
    let cancelled = false;
    setLoadingTargets(true);

    // Если занятие — часть цепочки (и стык не разомкнут), двигаем цепочкой целиком.
    const chain = buildChain(selectedLesson);
    const chainIds = chain.map((l) => l.placementId).filter((x): x is string => !!x);
    const isChainMove = chainIds.length > 1 && chainIds.length === chain.length;
    setSelectedChainIds(isChainMove ? chainIds : []);

    const optionsPromise = isChainMove
        ? CQRSService.findChainMoveOptions({ placementIds: chainIds })
        : CQRSService.findMoveOptions({
            sessionId,
            placementId: selectedLesson.placementId,
            rootEntityId: rootEntityId ?? selectedLesson.educatorIds[0] ?? 1,
            rootEntityType: rootEntityType ?? 'EDUCATOR',
          });

    optionsPromise
        .then((options) => {
          if (cancelled) return;
          // Каждый вариант — это старт головы. Для цепочки раскрываем его в весь след
          // (n подряд идущих пар того же дня), сопоставляя каждой ячейке старт головы.
          // Для одиночного переноса след — сама ячейка (n = 1).
          const span = isChainMove ? chainIds.length : 1;
          const targets = new Map<string, TimeSlotPair>();
          for (const o of options) {
            const startIdx = SLOTS.findIndex((s) => s.id === o.timeSlot);
            if (startIdx < 0) continue;
            for (let k = 0; k < span && startIdx + k < SLOTS.length; k++) {
              targets.set(`${o.date}_${SLOTS[startIdx + k].id}`, o.timeSlot);
            }
          }
          setMoveTargets(targets);
        })
        .catch(() => { if (!cancelled) setMoveTargets(new Map()); })
        .finally(() => { if (!cancelled) setLoadingTargets(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedLesson, sessionId, rootEntityType, rootEntityId, buildChain, placementCandidate, studyPeriodId]);

  const handleCellMove = async (dateStr: string, slotId: TimeSlotPair) => {
    if (moving) return;
    const startSlot = moveTargets.get(`${dateStr}_${slotId}`);
    if (!startSlot) return;

    // Установка занятия из палитры — отдельное действие, не перенос.
    if (placementCandidate) {
      setMoving(true);
      try {
        await onPlace?.(placementCandidate.assignmentId, dateStr, startSlot);
      } finally {
        setMoving(false);
      }
      return;
    }

    if (!selectedLesson || !sessionId) return;
    // Клик мог прийтись на хвост следа цепочки — переносим по старту головы,
    // а не по кликнутой ячейке.
    if (!selectedLesson.placementId) return;

    const movedId = String(selectedLesson.id);
    const movedPlacementId = selectedLesson.placementId;
    setMoving(true);
    try {
      const result = selectedChainIds.length > 1
        ? await CQRSService.moveChain(sessionId, {
            placementIds: selectedChainIds,
            newStartDate: dateStr,
            newStartSlot: startSlot,
            version: currentVersion,
          })
        : await CQRSService.moveLesson(sessionId, {
            placementId: selectedLesson.placementId,
            newDate: dateStr,
            newSlot: startSlot,
            // аудитории оставляем за занятием
            newAuditoriumIds: selectedLesson.auditoriumIds,
            version: currentVersion,
          });
      clearSelection();
      if (result.success) {
        // После переноса — пересортировка класса в порядок плана («пузырёк»): перенесённое
        // встаёт на своё плановое место, соседи сдвигаются на ячейку (меняются их даты,
        // тема едет с занятием).
        try {
          const { problems } = await CQRSService.reorder(movedPlacementId);
          if (problems && problems.length > 0) {
            window.alert(`Готово. Сцепок распалось: ${problems.length} — пересоберите вручную.`);
          }
        } catch (err) {
          console.error('Ошибка пересортировки в план:', err);
        }
        // Query Side обновляется асинхронно — даём ему мгновение, затем перезагружаем.
        setTimeout(() => onMoveLesson?.(movedId), 1000);
      }
    } catch (e) {
      console.error('Ошибка переноса:', e);
      clearSelection();
    } finally {
      setMoving(false);
    }
  };

  const borderClass = "border-slate-300";

  // У преподавателя в ячейке важны группы (он ведёт разные), поэтому контент
  // ячейки перестраиваем именно для его расписания.
  const isEducatorView = filterType === 'educator';

  // Рендер одной ячейки расписания через контекст ячейки (как у AcademicGridShell).
  // Не зависит от способа обхода сетки — это шаг к переходу на общий каркас.
  const renderScheduleCell = ({ date, dateStr, weekIdx, slot, slotIdx, factor }: GridCellContext) => {
    // Пропорциональный зум (как в Excel): базовые кегли/иконки × factor.
    // База ×1: аббревиатура 15px, тело (вид/группы/аудитория) 11px.
    // Excel меряет шрифт в пунктах (pt), веб — в CSS-px: 1pt ≈ 1.333px.
    // База ×1 = Excel-эквивалент: аббревиатура 15pt→20px, вид/аудитория 10pt→13px.
    const abbrPx = Math.round(20 * factor);
    const bodyPx = Math.round(13 * factor);   // вид занятия и аудитория (10pt)
    const iconPx = Math.round(12 * factor);   // иконки сцепки (Link/Unlink) и предупреждение
    const warnPx = Math.round(13 * factor);
    const lockPx = Math.round(6 * factor);    // замок — вдвое меньше прочих иконок
    const cellPx = Math.round(60 * factor);   // база ячейки 60×60 px при ×1
    const gridKey = `${dateStr}_${slot.id}`;
    const lessonsInCell = grid[gridKey] || [];

    // 1. Сначала ищем в сетке
    let lesson = lessonsInCell.find(l => {
      if (filterType === 'group') return l.groupNames.includes(selectedValue);
      if (filterType === 'educator') return l.educatorNames.includes(selectedValue);
      if (filterType === 'auditorium') return l.auditoriumNames.includes(selectedValue);
      return false;
    });

    // 2. Если в сетке пусто (проблема ключа), ищем в плоском списке (fallback)
    if (!lesson && lessons) {
      lesson = lessons.find(l =>
          l.date === dateStr &&
          l.timeSlotPair === slot.id &&
          (filterType === 'group' ? l.groupNames.includes(selectedValue) :
              filterType === 'educator' ? l.educatorNames.includes(selectedValue) :
                  l.auditoriumNames.includes(selectedValue))
      );
    }

    const activeConstraint = constraints.find(c => {
      const start = parseISO(c.startDate);
      const end = parseISO(c.endDate);
      return isWithinInterval(date, { start, end });
    });

    // Конфликт: занятие стоит в день, на который у ресурса есть ограничение —
    // так быть не должно, помечаем ячейку как ошибку.
    const isConflict = !!lesson && !!activeConstraint;

    const tooltipContent = lesson ? [
      ...(isConflict ? [`⚠ КОНФЛИКТ: занятие в день ограничения «${activeConstraint?.fullName}»`] : []),
      `Дисциплина: ${lesson.disciplineName}`,
      `Тип: ${lesson.kindOfStudyName}`,
      `Тема: Т.${lesson.themeNumber || '—'}`,
      `Преподаватель: ${lesson.educatorNames.join(', ') || '—'}`,
      `Аудитория: ${lesson.auditoriumNames.join(', ')}`,
      `Группы: ${lesson.groupNames.join(', ')}`
    ].join('\n') : activeConstraint ? `ОГРАНИЧЕНИЕ: ${activeConstraint.fullName} (${activeConstraint.abbreviation})` : '';

    // Доступная для переноса ячейка (подсвечивается зелёным) —
    // только пустая для выбранного ресурса и из списка вариантов.
    const isMoveTarget = (!!selectedLesson || !!placementCandidate) && !lesson && moveTargets.has(gridKey);
    const isTeacherBusy = !!selectedLesson && !lesson && !isMoveTarget && teacherBusyCells.has(gridKey);
    const isSourceCell = !!lesson && isSelectedLesson(lesson);
    // Звено выбранной цепочки — обводим синим вместе с источником,
    // чтобы было видно, что переносится вся связка целиком.
    const isChainMember = !!lesson?.placementId
        && selectedChainIds.includes(lesson.placementId);
    const isExamOrCredit = !!lesson &&
        (lesson.kindOfStudy === 'EXAM' ||
            lesson.kindOfStudy === 'CREDIT_WITH_GRADE' ||
            lesson.kindOfStudy === 'CREDIT_WITHOUT_GRADE');
    const isQuiz = lesson?.kindOfStudy === 'QUIZ';

    // Занятая ячейка, где препод выбранного занятия занят ДРУГИМ
    // занятием (скрытая занятость, не видимая в этом виде) — жёлтая.
    const isTeacherBusyHidden = !!selectedLesson && !!lesson && !isSourceCell &&
        teacherBusyCells.has(gridKey) &&
        !lesson.educatorIds.some((id) => selectedEducatorIds.has(id));
    // Принадлежит ли занятие активной дисциплине (подсветка по виду) И делит ли группу
    // с эталоном. В виде группы условие по группам всегда истинно (все занятия несут
    // выбранную группу) → поведение прежнее; сужение работает у преподавателя/аудитории.
    const cellDiscipline = lesson?.disciplineName ?? null;
    const sharesGroup = activeGroupNames.size === 0
        || !!lesson?.groupNames.some((n) => activeGroupNames.has(n));
    const isDisciplineMatch = !!cellDiscipline && cellDiscipline === activeDiscipline && sharesGroup;

    // Фон занятой ячейки: жёлтый (скрытая занятость) → цвет по виду
    // для активной дисциплины → нейтральный серый в покое.
    const disciplineBg = isExamOrCredit
        ? 'bg-violet-150 text-slate-900 hover:bg-violet-200'
        : lesson?.kindOfStudy === 'LECTURE'
            ? 'bg-rose-150 text-slate-900 hover:bg-rose-200'
            : 'bg-sky-150 text-slate-900 hover:bg-sky-200';
    // Фон занятой ячейки в покое — по «весу» вида: тёмно-серый у экзаменов/зачётов,
    // светло-серый у контрольных, обычные занятия — как свободная ячейка (белый).
    const restingBg = isExamOrCredit
        ? 'bg-slate-300 text-slate-900 hover:bg-slate-400'
        : isQuiz
            ? 'bg-slate-100 text-slate-900 hover:bg-slate-200'
            : 'bg-white text-slate-900 hover:bg-slate-50';
    const occupiedBg = isTeacherBusyHidden
        ? 'bg-amber-150 text-slate-900 hover:bg-amber-200'
        : isDisciplineMatch ? disciplineBg : restingBg;

    // Сцепка: связано ли это занятие с соседними по времени парами
    // того же дня (slot выше / ниже). Цепочка — вертикально подряд.
    const lessonAbove = slotIdx > 0
        ? resourceLessonByCell.get(`${dateStr}_${SLOTS[slotIdx - 1].id}`) : undefined;
    const lessonBelow = slotIdx < SLOTS.length - 1
        ? resourceLessonByCell.get(`${dateStr}_${SLOTS[slotIdx + 1].id}`) : undefined;
    const chainedAbove = !!lesson && areSlotsChained(lesson.curriculumSlotId, lessonAbove?.curriculumSlotId);
    const chainedBelow = !!lesson && areSlotsChained(lesson.curriculumSlotId, lessonBelow?.curriculumSlotId);
    // Стык ниже этой ячейки = "date_slotId"; стык выше = по слоту сверху.
    const detachedBelow = chainedBelow && detachedBoundaries.has(`${dateStr}_${slot.id}`);
    const detachedAbove = chainedAbove && slotIdx > 0
        && detachedBoundaries.has(`${dateStr}_${SLOTS[slotIdx - 1].id}`);
    // «Скоба» рисуется только по неразомкнутым стыкам.
    const spineAbove = chainedAbove && !detachedAbove;
    const spineBelow = chainedBelow && !detachedBelow;
    const isChainedSpine = spineAbove || spineBelow;

    return (
        <td
            key={weekIdx}
            style={{ width: cellPx, minWidth: cellPx }}
            onClick={() => {
              if (isMoveTarget) { handleCellMove(dateStr, slot.id); return; }
              if (lesson) handleLessonClick(lesson);
            }}
            onMouseEnter={lesson && !selectedLesson ? () => setHoveredLesson(lesson) : undefined}
            onMouseLeave={lesson && !selectedLesson ? () => setHoveredLesson(null) : undefined}
            className={cn(
                'border-r p-0.5 transition-all relative overflow-hidden',
                borderClass,
                !lesson && !isMoveTarget && !isTeacherBusy && 'bg-white hover:bg-slate-50/30',
                !lesson && !isMoveTarget && !isTeacherBusy && activeConstraint && 'bg-rose-50/50',
                isMoveTarget && 'bg-emerald-150 hover:bg-emerald-200 cursor-pointer',
                isTeacherBusy && 'bg-amber-150',
                lesson && !isConflict && occupiedBg,
                isConflict && 'bg-red-100 text-red-900 hover:bg-red-200',
                lesson && isEditMode && 'cursor-pointer',
                lesson && !isEditMode && 'cursor-help',
                isConflict && 'ring-2 ring-inset ring-red-500',
                (isSourceCell || isChainMember) && !isConflict && 'ring-2 ring-inset ring-blue-600',
                pinningEnabled && lesson?.locked && !isConflict && !isSourceCell && !isChainMember && 'ring-2 ring-inset ring-amber-500'
            )}
            title={
              isMoveTarget ? 'Нажмите, чтобы перенести занятие сюда'
                  : isTeacherBusy ? 'Преподаватель занят в это время'
                      : lesson && isEditMode ? `${tooltipContent}\n\n(Нажмите, чтобы выбрать занятие для переноса)`
                          : tooltipContent
            }
        >
          {(isChainedSpine || chainedBelow) && (
              <>
                {/* Левая «скоба» вдоль неразомкнутых звеньев цепочки */}
                {isChainedSpine && (
                    <div className={cn(
                        'absolute left-0 w-[2px] bg-slate-500/80 z-20 pointer-events-none',
                        spineAbove ? 'top-0' : 'top-1',
                        spineBelow ? 'bottom-0' : 'bottom-1',
                        !spineAbove && 'rounded-t-full',
                        !spineBelow && 'rounded-b-full'
                    )} />
                )}
                {/* Звено на стыке: в редактировании — кнопка размыкания/соединения */}
                {chainedBelow && (
                    <button
                        type="button"
                        disabled={!isEditMode}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (!isEditMode) return;
                          const key = `${dateStr}_${slot.id}`;
                          setDetachedBoundaries((prev) => {
                            const next = new Set(prev);
                            if (next.has(key)) next.delete(key); else next.add(key);
                            return next;
                          });
                        }}
                        title={isEditMode
                            ? (detachedBelow ? 'Сцепка разомкнута — соединить' : 'Разомкнуть сцепку для отдельного переноса')
                            : 'Сцепка занятий'}
                        className={cn(
                            'absolute left-0 bottom-0 z-30 rounded-full ring-1 p-[1px] bg-white',
                            detachedBelow ? 'ring-slate-300' : 'ring-slate-400',
                            isEditMode ? 'pointer-events-auto cursor-pointer hover:ring-blue-500' : 'pointer-events-none'
                        )}
                    >
                      {detachedBelow
                          ? <Unlink size={iconPx} className="text-slate-400" />
                          : <Link2 size={iconPx} className="text-slate-600" />}
                    </button>
                )}
              </>
          )}
          {isConflict && (
              <div className="absolute top-0.5 left-0.5 z-30 text-red-600 pointer-events-none">
                <AlertTriangle size={warnPx} />
              </div>
          )}
          {lesson ? (
              <div className="flex flex-col h-full leading-[1] justify-between p-0.5 relative" style={{ fontSize: bodyPx }}>
                {/* Замок (Фича 2): в редактировании — тумблер закрепления, вне — индикатор пина.
                    Только когда пины включены хостом (планировщик). */}
                {pinningEnabled && isEditMode && lesson.placementId ? (
                    <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          // Эффективная цепочка с учётом временного разрыва (detachedBoundaries) —
                          // та же функция, что строит «след» для переноса цепочки.
                          const chainIds = buildChain(lesson)
                              .map((l) => l.placementId)
                              .filter((id): id is string => !!id);
                          onToggleLock?.(lesson, chainIds);
                        }}
                        title={lesson.locked
                            ? 'Открепить (распределитель снова сможет двигать)'
                            : 'Закрепить — распределитель не будет двигать это занятие'}
                        className={cn(
                            'absolute top-0 right-0 z-30 rounded-full ring-1 p-[1px] bg-white pointer-events-auto cursor-pointer transition-colors',
                            lesson.locked ? 'ring-amber-400 hover:ring-amber-600' : 'ring-slate-300 hover:ring-blue-500'
                        )}
                    >
                      {lesson.locked
                          ? <Lock size={lockPx} className="text-amber-600" />
                          : <LockOpen size={lockPx} className="text-slate-400" />}
                    </button>
                ) : pinningEnabled && lesson.locked ? (
                    <div className="absolute top-0 right-0 z-30 text-amber-600 pointer-events-none" title="Закреплено вручную">
                      <Lock size={lockPx} />
                    </div>
                ) : null}
                {isEducatorView ? (
                    <>
                      {/* Преподаватель: дисциплина+вид+тема / группы (переносятся) / аудитория.
                          Шрифт дисциплины = шрифту вида (bodyPx). Список групп переносится по
                          ширине ячейки, а высота строки растёт под число строк (автоподбор
                          по высоте — см. рост ячейки в каркасе). */}
                      <div className="flex items-baseline gap-1 whitespace-nowrap overflow-hidden">
                        <span className="font-black tracking-tighter" style={{ fontSize: bodyPx }}>
                          {lesson.disciplineAbbreviation}
                        </span>
                        <span className="font-bold opacity-60" style={{ fontSize: bodyPx }}>
                          {lesson.kindOfStudyAbbr}/Т.{lesson.themeNumber || '—'}
                        </span>
                      </div>
                      <div className="font-bold text-center leading-tight break-words flex-1 flex items-center justify-center">
                        {lesson.groupNames.join(', ') || '—'}
                      </div>
                      <div className="font-mono font-black text-right opacity-80" style={{ fontSize: bodyPx }}>
                        {lesson.auditoriumNames.join(', ')}
                      </div>
                    </>
                ) : (
                    <>
                      <div className="font-bold whitespace-nowrap overflow-hidden opacity-60">
                        {lesson.kindOfStudyAbbr}/Т.{lesson.themeNumber || '—'}
                      </div>
                      <div className="font-black truncate w-full tracking-tighter flex-1 flex items-center justify-center" style={{ fontSize: abbrPx }}>
                        {lesson.disciplineAbbreviation}
                      </div>
                      <div className="font-mono font-black text-right opacity-80" style={{ fontSize: bodyPx }}>
                        {lesson.auditoriumNames[0]}
                      </div>
                    </>
                )}
              </div>
          ) : activeConstraint ? (
              <div className="flex items-center justify-center h-full font-black text-slate-900 tracking-tighter" style={{ fontSize: abbrPx }}>
                {activeConstraint.abbreviation}
              </div>
          ) : null}
        </td>
    );
  };

  return (
      <AcademicGridShell
          startDate={startDate}
          endDate={endDate}
          maxHeightClass={maxHeightClass}
          chromeless={chromeless}
          renderCell={renderScheduleCell}
          overlay={selectedLesson && (
              <div
                  className={cn(
                      'fixed bottom-6 left-1/2 -translate-x-1/2 z-[110] flex items-center gap-3',
                      'bg-slate-900/95 text-white rounded-xl px-4 py-2 shadow-2xl backdrop-blur',
                      'transition-opacity duration-500',
                      hintVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'
                  )}
              >
                <span className="text-[11px] font-bold whitespace-nowrap">
                  {loadingTargets
                      ? 'Ищем доступные слоты…'
                      : moving
                          ? (selectedChainIds.length > 1 ? 'Переносим цепочку…' : 'Переносим занятие…')
                          : moveTargets.size > 0
                              ? (selectedChainIds.length > 1
                                  ? <>Зелёные — куда поставить цепочку из {selectedChainIds.length} пар (звено можно разомкнуть)</>
                                  : <>Зелёные — куда можно перенести, <span className="text-amber-300">жёлтые</span> — где занят преподаватель</>)
                              : `Нет доступных слотов для «${selectedLesson.disciplineAbbreviation}»`}
                </span>
                <button
                    onClick={clearSelection}
                    className="flex items-center gap-1 text-[10px] font-black uppercase tracking-tight px-2 py-1 rounded-lg bg-white/10 hover:bg-white/20 transition-colors shrink-0"
                >
                  <X size={12} /> Esc
                </button>
              </div>
          )}
      />
  );
};
