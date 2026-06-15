import React, { useMemo, useState } from 'react';
import { ScheduledLessonDto, TimeSlotPair } from '../../../types/api';
import { format, addDays, eachWeekOfInterval, isWithinInterval, parseISO } from 'date-fns';
import { ru } from 'date-fns/locale';
import { cn } from '../../../utils/cn';
import { ZoomIn, ZoomOut, Maximize2, Minimize2, Calendar, ShieldAlert } from 'lucide-react';
import { MoveLessonDialog } from './MoveLessonDialog';

interface AcademicGridScheduleProps {
  lessons: ScheduledLessonDto[];
  grid: Record<string, ScheduledLessonDto[]>;
  filterType: 'group' | 'educator' | 'auditorium';
  selectedValue: string;
  startDate: Date;
  endDate: Date;
  constraints?: any[];
  isEditMode?: boolean;
  sessionId?: string;
  currentVersion?: number;
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
                                                                            onMoveLesson
                                                                          }) => {
  const [zoom, setZoom] = useState(0);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [selectedLesson, setSelectedLesson] = useState<ScheduledLessonDto | null>(null);

  const handleLessonClick = (lesson: ScheduledLessonDto) => {
    if (isEditMode && sessionId && onMoveLesson) {
      setSelectedLesson(lesson);
    }
  };

  const handleMoveSuccessful = () => {
    setSelectedLesson(null);
    if (selectedLesson && onMoveLesson) {
      onMoveLesson(String(selectedLesson.id));
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
    fontSizeSub: zoom === 0 ? 'text-[6px]' : zoom === 1 ? 'text-[8px]' : 'text-[10px]',
    containerMaxHeight: isFullscreen ? 'h-[90vh]' : 'max-h-[700px]'
  };

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

                            return (
                                <td
                                    key={weekIdx}
                                    onClick={() => lesson && handleLessonClick(lesson)}
                                    className={cn(
                                        'border-r p-0.5 transition-all relative overflow-hidden',
                                        borderClass,
                                        !lesson && 'bg-white hover:bg-slate-50/30',
                                        lesson && isEditMode && 'bg-blue-100 text-blue-900 hover:bg-blue-200 cursor-pointer',
                                        lesson && !isEditMode && 'bg-slate-200 text-slate-900 hover:bg-slate-300 cursor-help',
                                        !lesson && activeConstraint && 'bg-rose-50/50'
                                    )}
                                    title={lesson && isEditMode ? 'Нажмите, чтобы перенести занятие' : tooltipContent}
                                >
                                  {lesson ? (
                                      <div className={cn("flex flex-col h-full leading-[1] justify-between p-0.5 relative", zoomClasses.fontSizeMain)}>
                                        {isEditMode && (
                                            <div className="absolute top-0.5 right-0.5 w-1.5 h-1.5 bg-blue-600 rounded-full animate-pulse" />
                                        )}
                                        <div className="font-bold border-b border-slate-300/50 pb-0.5 mb-0.5 whitespace-nowrap overflow-hidden opacity-60">
                                          {lesson.kindOfStudyAbbr}/Т.{lesson.themeNumber || '—'}
                                        </div>
                                        <div className="font-black truncate w-full tracking-tighter flex-1 flex items-center justify-center">
                                          {lesson.disciplineAbbreviation}
                                        </div>
                                        <div className={cn("font-mono font-black mt-0.5 text-right opacity-80", zoomClasses.fontSizeSub)}>
                                          {lesson.auditoriumNames[0]}
                                        </div>
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

        {selectedLesson && sessionId && (
            <MoveLessonDialog
                placement={selectedLesson}
                sessionId={sessionId}
                currentVersion={currentVersion}
                onMoveSuccessful={handleMoveSuccessful}
                onCancel={() => setSelectedLesson(null)}
            />
        )}
      </div>
  );
};
