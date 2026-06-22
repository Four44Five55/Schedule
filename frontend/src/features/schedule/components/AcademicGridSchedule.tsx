import React, { useMemo, useState, useEffect } from 'react';
import { ScheduledLessonDto, TimeSlotPair, ConstraintDto } from '../../../types/api';
import { format, addDays, eachWeekOfInterval, isWithinInterval, parseISO } from 'date-fns';
import { ru } from 'date-fns/locale';
import { cn } from '../../../utils/cn';
import { ZoomIn, ZoomOut, Maximize2, Minimize2, Calendar, ShieldAlert, X } from 'lucide-react';
import { CQRSService } from '../../../services/cqrsApiService';

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
}

const DAYS = [
  { id: 1, label: 'Пн' },
  { id: 2, label: 'Вт' },
  { id: 3, label: 'Ср' },
  { id: 4, label: 'Чт' },
  { id: 5, label: 'Пт' },
  { id: 6, label: 'Сб' },
];

const SLOTS: { id: TimeSlotPair; label: string; time: string }[] = [
  { id: 'FIRST', label: '1', time: '9:00' },
  { id: 'SECOND', label: '2', time: '10:55' },
  { id: 'THIRD', label: '3', time: '12:50' },
  { id: 'FOURTH', label: '4', time: '16:20' },
];

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
                                                                            onMoveLesson
                                                                          }) => {
  const [zoom, setZoom] = useState(0);
  const [isFullscreen, setIsFullscreen] = useState(false);

  // Перенос «по сетке»: выбираем занятие → подсвечиваем зелёным доступные ячейки →
  // клик по зелёной ячейке переносит занятие туда. Без модального окна.
  const [selectedLesson, setSelectedLesson] = useState<ScheduledLessonDto | null>(null);
  const [moveTargets, setMoveTargets] = useState<Set<string>>(new Set());
  const [loadingTargets, setLoadingTargets] = useState(false);
  const [moving, setMoving] = useState(false);
  const [hintVisible, setHintVisible] = useState(true);
  // Дисциплина, подсвеченная наведением (когда занятие ещё не выбрано).
  const [hoveredDiscipline, setHoveredDiscipline] = useState<string | null>(null);

  const clearSelection = () => {
    setSelectedLesson(null);
    setMoveTargets(new Set());
  };

  const isSelectedLesson = (l: ScheduledLessonDto) =>
      !!selectedLesson &&
      selectedLesson.placementId === l.placementId &&
      selectedLesson.date === l.date &&
      selectedLesson.timeSlotPair === l.timeSlotPair;

  const handleLessonClick = (lesson: ScheduledLessonDto) => {
    if (!isEditMode || !sessionId || !onMoveLesson) return;
    // повторный клик по тому же занятию — снять выбор
    if (isSelectedLesson(lesson)) {
      clearSelection();
      return;
    }
    setSelectedLesson(lesson);
  };

  // Подбор доступных ячеек для выбранного занятия (то, что подсветится зелёным).
  useEffect(() => {
    if (!selectedLesson || !sessionId || !selectedLesson.placementId) {
      setMoveTargets(new Set());
      return;
    }
    let cancelled = false;
    setLoadingTargets(true);
    const rootType = rootEntityType ?? 'EDUCATOR';
    const rootId = rootEntityId ?? selectedLesson.educatorIds[0] ?? 1;
    CQRSService.findMoveOptions({
      sessionId,
      placementId: selectedLesson.placementId,
      rootEntityId: rootId,
      rootEntityType: rootType,
    })
        .then((options) => {
          if (!cancelled) setMoveTargets(new Set(options.map((o) => `${o.date}_${o.timeSlot}`)));
        })
        .catch(() => { if (!cancelled) setMoveTargets(new Set()); })
        .finally(() => { if (!cancelled) setLoadingTargets(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedLesson, sessionId, rootEntityType, rootEntityId]);

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

  // Активная дисциплина: закреплённая выбором имеет приоритет над наведением.
  const activeDiscipline = selectedLesson?.disciplineName ?? hoveredDiscipline;

  const handleCellMove = async (dateStr: string, slotId: TimeSlotPair) => {
    if (!selectedLesson || !sessionId || moving) return;
    if (!moveTargets.has(`${dateStr}_${slotId}`) || !selectedLesson.placementId) return;

    const movedId = String(selectedLesson.id);
    setMoving(true);
    try {
      const result = await CQRSService.moveLesson(sessionId, {
        placementId: selectedLesson.placementId,
        newDate: dateStr,
        newSlot: slotId,
        // аудитории оставляем за занятием
        newAuditoriumIds: selectedLesson.auditoriumIds,
        version: currentVersion,
      });
      clearSelection();
      if (result.success) {
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

  const mondays = useMemo(() => {
    return eachWeekOfInterval({ start: startDate, end: endDate }, { weekStartsOn: 1 });
  }, [startDate, endDate]);

  const monthHeaders = useMemo(() => {
    const headers: { name: string; count: number }[] = [];
    mondays.forEach((monday) => {
      const monthName = format(monday, 'LLLL', { locale: ru });
      if (headers.length > 0 && headers[headers.length - 1].name === monthName) {
        headers[headers.length - 1].count++;
      } else {
        headers.push({ name: monthName, count: 1 });
      }
    });
    return headers;
  }, [mondays]);

  const borderClass = "border-slate-300";
  const headerBorderClass = "border-slate-400";

  const zoomClasses = {
    cellHeight: zoom === 0 ? 'h-9' : zoom === 1 ? 'h-14' : 'h-24',
    fontSizeMain: zoom === 0 ? 'text-[7px]' : zoom === 1 ? 'text-[10px]' : 'text-[12px]',
    // Аббревиатура дисциплины — на 2pt крупнее основного текста ячейки.
    fontSizeAbbr: zoom === 0 ? 'text-[9px]' : zoom === 1 ? 'text-[12px]' : 'text-[14px]',
    fontSizeSub: zoom === 0 ? 'text-[6px]' : zoom === 1 ? 'text-[8px]' : 'text-[10px]',
    containerMaxHeight: isFullscreen ? 'h-[90vh]' : 'max-h-[700px]'
  };

  // У преподавателя в ячейке важны группы (он ведёт разные), поэтому контент
  // ячейки перестраиваем именно для его расписания.
  const isEducatorView = filterType === 'educator';

  return (
      <div className={cn(
          "space-y-2 animate-fade-in transition-all duration-500",
          isFullscreen && "fixed inset-0 z-[100] bg-slate-50 p-4 overflow-hidden flex flex-col"
      )}>
        <div className="flex items-center justify-between bg-slate-900 text-white p-1 px-3 rounded-xl shadow-lg shrink-0">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 border-r border-slate-700 pr-4 py-1">
              <Calendar size={14} className="text-blue-400" />
              <span className="text-[10px] font-black uppercase tracking-widest">Академическая сетка</span>
            </div>

            <div className="flex items-center gap-1 bg-slate-800 rounded-lg p-0.5">
              <button
                  onClick={() => setZoom(Math.max(0, zoom - 1))}
                  className="p-1 hover:bg-slate-700 rounded transition-all text-slate-400 hover:text-white"
                  disabled={zoom === 0}
              >
                <ZoomOut size={14} />
              </button>
              <span className="text-[9px] font-black w-16 text-center text-slate-300">
              {zoom === 0 ? 'MIN' : zoom === 1 ? 'MID' : 'MAX'}
            </span>
              <button
                  onClick={() => setZoom(Math.min(2, zoom + 1))}
                  className="p-1 hover:bg-slate-700 rounded transition-all text-slate-400 hover:text-white"
                  disabled={zoom === 2}
              >
                <ZoomIn size={14} />
              </button>
            </div>
          </div>

          <button
              onClick={() => setIsFullscreen(!isFullscreen)}
              className="flex items-center gap-2 px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-all text-[9px] font-black uppercase tracking-tighter"
          >
            {isFullscreen ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
            {isFullscreen ? 'Свернуть' : 'Развернуть'}
          </button>
        </div>

        {selectedLesson && (
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
                        ? 'Переносим занятие…'
                        : moveTargets.size > 0
                            ? <>Зелёные — куда можно перенести, <span className="text-amber-300">жёлтые</span> — где занят преподаватель</>
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

        <div className={cn(
            "bg-white shadow-2xl rounded-xl border-2 border-slate-300 overflow-hidden flex flex-col transition-all duration-500",
            zoomClasses.containerMaxHeight
        )}>
          <div className="overflow-auto custom-scrollbar flex-1">
            <table className={cn("w-full border-collapse select-none", zoom === 0 ? "table-fixed" : "table-auto")}>
              <thead>
              <tr className="bg-slate-900 text-white">
                <th className={cn("w-6 border-r p-0.5 text-[8px] font-black uppercase sticky left-0 bg-slate-900 z-30", headerBorderClass)} rowSpan={3}>Дн</th>
                <th className={cn("w-10 border-r p-0.5 text-[8px] font-black uppercase sticky left-6 bg-slate-900 z-30", headerBorderClass)} rowSpan={3}>П</th>
                {mondays.map((_, idx) => (
                    <th key={idx} className={cn("border-r p-0.5 text-[7px] font-black bg-slate-800 text-slate-400", headerBorderClass)}>
                      {idx + 1}
                    </th>
                ))}
              </tr>
              <tr className="bg-slate-100">
                {monthHeaders.map((month, idx) => (
                    <th key={idx} colSpan={month.count} className={cn("border-r p-0.5 text-center text-[8px] font-black uppercase tracking-widest text-slate-500", headerBorderClass)}>
                      {month.name}
                    </th>
                ))}
              </tr>
              <tr className="bg-white border-b-2 border-slate-400">
                {mondays.map((monday, idx) => (
                    <th key={idx} className={cn("border-r p-0.5 text-[7px] font-bold text-slate-400", borderClass)}>
                      {format(monday, 'dd.MM')}
                    </th>
                ))}
              </tr>
              </thead>

              <tbody className="divide-y divide-slate-300">
              {DAYS.map((day) => (
                  <React.Fragment key={day.id}>
                    <tr className="bg-slate-50">
                      <td className={cn("border-r text-center font-black text-[9px] text-slate-900 sticky left-0 bg-slate-100 z-20 w-6", headerBorderClass)} rowSpan={5}>
                        <div className="rotate-90 whitespace-nowrap uppercase">
                          {day.label}
                        </div>
                      </td>
                      <td className={cn("border-r text-[7px] font-black text-slate-400 text-center sticky left-6 bg-slate-50 z-10 uppercase tracking-tighter h-5", borderClass)}>
                        D
                      </td>
                      {mondays.map((monday, idx) => (
                          <td key={idx} className={cn("border-r text-center text-[8px] font-black text-slate-700 bg-slate-50/50", borderClass)}>
                            {format(addDays(monday, day.id - 1), 'd')}
                          </td>
                      ))}
                    </tr>

                    {SLOTS.map((slot) => (
                        <tr key={slot.id} className={cn("group transition-all duration-300", zoomClasses.cellHeight)}>
                          <td className={cn("border-r p-0.5 text-center sticky left-6 bg-white z-10 w-10 group-hover:bg-slate-50 transition-colors", borderClass)}>
                            <div className="font-black text-slate-800 text-[9px]">{slot.label}</div>
                            <div className="text-[6px] text-slate-400 font-mono leading-none">{slot.time}</div>
                          </td>

                          {mondays.map((monday, weekIdx) => {
                            const targetDate = addDays(monday, day.id - 1);

                            const dateStr = format(targetDate, 'yyyy-MM-dd');
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
                              return isWithinInterval(targetDate, { start, end });
                            });

                            const tooltipContent = lesson ? [
                              `Дисциплина: ${lesson.disciplineName}`,
                              `Тип: ${lesson.kindOfStudyName}`,
                              `Тема: Т.${lesson.themeNumber || '—'}`,
                              `Аудитория: ${lesson.auditoriumNames.join(', ')}`,
                              `Группы: ${lesson.groupNames.join(', ')}`
                            ].join('\n') : activeConstraint ? `ОГРАНИЧЕНИЕ: ${activeConstraint.kindOfConstraint}` : '';

                            // Доступная для переноса ячейка (подсвечивается зелёным) —
                            // только пустая для выбранного ресурса и из списка вариантов.
                            const isMoveTarget = !!selectedLesson && !lesson && moveTargets.has(gridKey);
                            const isTeacherBusy = !!selectedLesson && !lesson && !isMoveTarget && teacherBusyCells.has(gridKey);
                            const isSourceCell = !!lesson && isSelectedLesson(lesson);
                            const isExamOrCredit = !!lesson &&
                                (lesson.kindOfStudy === 'EXAM' ||
                                    lesson.kindOfStudy === 'CREDIT_WITH_GRADE' ||
                                    lesson.kindOfStudy === 'CREDIT_WITHOUT_GRADE');

                            // Занятая ячейка, где препод выбранного занятия занят ДРУГИМ
                            // занятием (скрытая занятость, не видимая в этом виде) — жёлтая.
                            const isTeacherBusyHidden = !!selectedLesson && !!lesson && !isSourceCell &&
                                teacherBusyCells.has(gridKey) &&
                                !lesson.educatorIds.some((id) => selectedEducatorIds.has(id));
                            // Принадлежит ли занятие активной дисциплине (подсветка по виду).
                            const cellDiscipline = lesson?.disciplineName ?? null;
                            const isDisciplineMatch = !!cellDiscipline && cellDiscipline === activeDiscipline;

                            // Фон занятой ячейки: жёлтый (скрытая занятость) → цвет по виду
                            // для активной дисциплины → нейтральный серый в покое.
                            const disciplineBg = isExamOrCredit
                                ? 'bg-violet-150 text-slate-900 hover:bg-violet-200'
                                : lesson?.kindOfStudy === 'LECTURE'
                                    ? 'bg-rose-150 text-slate-900 hover:bg-rose-200'
                                    : 'bg-sky-150 text-slate-900 hover:bg-sky-200';
                            const restingBg = isExamOrCredit
                                ? 'bg-slate-300 text-slate-600 hover:bg-slate-400'
                                : 'bg-slate-100 text-slate-900 hover:bg-slate-200';
                            const occupiedBg = isTeacherBusyHidden
                                ? 'bg-amber-150 text-slate-900 hover:bg-amber-200'
                                : isDisciplineMatch ? disciplineBg : restingBg;

                            return (
                                <td
                                    key={weekIdx}
                                    onClick={() => {
                                      if (isMoveTarget) { handleCellMove(dateStr, slot.id); return; }
                                      if (lesson) handleLessonClick(lesson);
                                    }}
                                    onMouseEnter={cellDiscipline && !selectedLesson ? () => setHoveredDiscipline(cellDiscipline) : undefined}
                                    onMouseLeave={cellDiscipline && !selectedLesson ? () => setHoveredDiscipline(null) : undefined}
                                    className={cn(
                                        'border-r p-0.5 transition-all relative overflow-hidden',
                                        borderClass,
                                        !lesson && !isMoveTarget && !isTeacherBusy && 'bg-white hover:bg-slate-50/30',
                                        !lesson && !isMoveTarget && !isTeacherBusy && activeConstraint && 'bg-rose-50/50',
                                        isMoveTarget && 'bg-emerald-150 hover:bg-emerald-200 cursor-pointer',
                                        isTeacherBusy && 'bg-amber-150',
                                        lesson && occupiedBg,
                                        lesson && isEditMode && 'cursor-pointer',
                                        lesson && !isEditMode && 'cursor-help',
                                        isSourceCell && 'ring-2 ring-inset ring-blue-600'
                                    )}
                                    title={
                                      isMoveTarget ? 'Нажмите, чтобы перенести занятие сюда'
                                          : isTeacherBusy ? 'Преподаватель занят в это время'
                                              : lesson && isEditMode ? 'Нажмите, чтобы выбрать занятие для переноса'
                                                  : tooltipContent
                                    }
                                >
                                  {lesson ? (
                                      <div className={cn("flex flex-col h-full leading-[1] justify-between p-0.5 relative", zoomClasses.fontSizeMain)}>
                                        {isEditMode && (
                                            <div className="absolute top-0.5 right-0.5 w-1.5 h-1.5 bg-slate-400 rounded-full animate-pulse" />
                                        )}
                                        {isEducatorView ? (
                                            <>
                                              {/* Преподаватель: дисциплина+вид+тема / группы / аудитория */}
                                              <div className="flex items-baseline gap-1 whitespace-nowrap overflow-hidden border-b border-slate-300/50 pb-0.5 mb-0.5">
                                                <span className={cn("font-black tracking-tighter", zoomClasses.fontSizeAbbr)}>
                                                  {lesson.disciplineAbbreviation}
                                                </span>
                                                <span className={cn("font-bold opacity-60", zoomClasses.fontSizeSub)}>
                                                  {lesson.kindOfStudyAbbr}/Т.{lesson.themeNumber || '—'}
                                                </span>
                                              </div>
                                              <div className="font-bold truncate flex-1 flex items-center">
                                                {lesson.groupNames.join(', ') || '—'}
                                              </div>
                                              <div className={cn("font-mono font-black mt-0.5 text-right opacity-80", zoomClasses.fontSizeSub)}>
                                                {lesson.auditoriumNames.join(', ')}
                                              </div>
                                            </>
                                        ) : (
                                            <>
                                              <div className="font-bold border-b border-slate-300/50 pb-0.5 mb-0.5 whitespace-nowrap overflow-hidden opacity-60">
                                                {lesson.kindOfStudyAbbr}/Т.{lesson.themeNumber || '—'}
                                              </div>
                                              <div className={cn("font-black truncate w-full tracking-tighter flex-1 flex items-center justify-center", zoomClasses.fontSizeAbbr)}>
                                                {lesson.disciplineAbbreviation}
                                              </div>
                                              <div className={cn("font-mono font-black mt-0.5 text-right opacity-80", zoomClasses.fontSizeSub)}>
                                                {lesson.auditoriumNames[0]}
                                              </div>
                                            </>
                                        )}
                                      </div>
                                  ) : activeConstraint ? (
                                      <div className="flex items-center justify-center h-full opacity-30">
                                        <ShieldAlert size={zoom === 0 ? 10 : 14} className="text-rose-400" />
                                      </div>
                                  ) : null}
                                </td>
                            );
                          })}
                        </tr>
                    ))}
                  </React.Fragment>
              ))}
              </tbody>
            </table>
          </div>
        </div>

      </div>
  );
};
