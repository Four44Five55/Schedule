import React, { useMemo } from 'react';
import { ScheduledLessonDto, TimeSlotPair } from '../../../types/api';
import { format, addDays, isSameDay, eachWeekOfInterval, isWithinInterval, parseISO } from 'date-fns';
import { ru } from 'date-fns/locale';
import { cn } from '../../../utils/cn';
import { ShieldAlert, Clock } from 'lucide-react';

interface CompactPaperScheduleProps {
  lessons: ScheduledLessonDto[];
  startDate: Date;
  endDate: Date;
  constraints?: any[];
}

const DAYS = [
  { id: 1, label: 'ПН' },
  { id: 2, label: 'ВТ' },
  { id: 3, label: 'СР' },
  { id: 4, label: 'ЧТ' },
  { id: 5, label: 'ПТ' },
  { id: 6, label: 'СБ' },
];

const SLOTS: { id: TimeSlotPair; label: string; time: string }[] = [
  { id: 'FIRST', label: '1', time: '09:00' },
  { id: 'SECOND', label: '2', time: '10:55' },
  { id: 'THIRD', label: '3', time: '12:50' },
  { id: 'FOURTH', label: '4', time: '16:20' },
];

export const CompactPaperSchedule: React.FC<CompactPaperScheduleProps> = ({ lessons, startDate, endDate, constraints = [] }) => {
  const weeks = useMemo(() => {
    return eachWeekOfInterval({ start: startDate, end: endDate }, { weekStartsOn: 1 });
  }, [startDate, endDate]);

  const monthHeaders = useMemo(() => {
    const headers: { name: string; count: number }[] = [];
    weeks.forEach((monday) => {
      const monthName = format(monday, 'LLLL', { locale: ru });
      if (headers.length > 0 && headers[headers.length - 1].name === monthName) {
        headers[headers.length - 1].count++;
      } else {
        headers.push({ name: monthName, count: 1 });
      }
    });
    return headers;
  }, [weeks]);

  return (
      <div className="bg-white shadow-2xl rounded-2xl border border-slate-200 overflow-hidden animate-fade-in">
        <div className="overflow-x-auto custom-scrollbar">
          <table className="w-full border-collapse table-fixed select-none min-w-[1200px]">
            <thead>
            <tr className="bg-slate-900 text-white">
              <th className="w-10 border-r border-slate-700 bg-slate-900 sticky left-0 z-30" rowSpan={3}></th>
              <th className="w-16 border-r border-slate-700 bg-slate-900 sticky left-10 z-30" rowSpan={3}></th>
              {monthHeaders.map((month, idx) => (
                  <th key={idx} colSpan={month.count} className="border-r border-slate-700 p-1.5 text-center text-[10px] font-black uppercase tracking-[0.3em]">
                    {month.name}
                  </th>
              ))}
            </tr>
            <tr className="bg-slate-100 text-slate-500 border-b border-slate-200">
              {weeks.map((_, idx) => (
                  <th key={idx} className="border-r border-slate-200 p-1 text-[9px] font-black uppercase tracking-tighter text-center">
                    {idx + 1}
                  </th>
              ))}
            </tr>
            <tr className="bg-white border-b-2 border-slate-300">
              {weeks.map((monday, idx) => (
                  <th key={idx} className="border-r border-slate-100 p-1 text-[9px] font-bold text-slate-400 text-center">
                    {format(monday, 'dd.MM')}
                  </th>
              ))}
            </tr>
            </thead>

            <tbody>
            {DAYS.map((day) => (
                <React.Fragment key={day.id}>
                  <tr className="bg-slate-50">
                    <td className="border-r border-slate-300 text-center font-black text-[11px] text-slate-900 sticky left-0 bg-slate-100 z-20 w-8" rowSpan={5}>
                      <div className="rotate-0 md:-rotate-90 whitespace-nowrap uppercase tracking-widest">
                        {day.label}
                      </div>
                    </td>
                    <td className="border-r border-slate-300 p-1 text-center sticky left-10 bg-slate-50 z-10 w-16 border-b border-slate-200 h-6">
                      <span className="text-[8px] font-black text-slate-400 uppercase tracking-tighter">Дата</span>
                    </td>
                    {weeks.map((monday, idx) => (
                        <td key={idx} className="border-r border-slate-100 text-center text-[9px] font-black text-slate-600 bg-slate-50/50">
                          {format(addDays(monday, day.id - 1), 'd')}
                        </td>
                    ))}
                  </tr>

                  {SLOTS.map((slot) => (
                      <tr key={slot.id} className="h-14">
                        <td className="border-r border-slate-300 p-1 text-center sticky left-10 bg-white z-10 w-16 border-b border-slate-100">
                          <div className="font-black text-slate-800 text-[11px]">{slot.label}</div>
                          <div className="text-[8px] text-slate-400 font-mono leading-none mt-1">{slot.time}</div>
                        </td>

                        {weeks.map((monday, weekIdx) => {
                          const targetDate = addDays(monday, day.id - 1);
                          const lesson = lessons.find((l) =>
                              isSameDay(new Date(l.date), targetDate) && l.timeSlotPair === slot.id
                          );

                          // Проверка на реальное ограничение
                          const activeConstraint = constraints.find(c => {
                            const start = parseISO(c.startDate);
                            const end = parseISO(c.endDate);
                            return isWithinInterval(targetDate, { start, end });
                          });

                          return (
                              <td
                                  key={weekIdx}
                                  className={cn(
                                      'border-r border-b border-slate-100 p-1 transition-all relative overflow-hidden group',
                                      !lesson && 'bg-white hover:bg-slate-50/50',
                                      lesson && 'bg-slate-200 text-slate-900 border-slate-300 hover:bg-slate-300 cursor-help',
                                      !lesson && activeConstraint && 'bg-rose-50/50'
                                  )}
                                  title={activeConstraint ? `ОГРАНИЧЕНИЕ: ${activeConstraint.kindOfConstraint}\n${activeConstraint.description || ''}` : ''}
                              >
                                <div className="absolute top-0.5 right-1 text-[7px] font-black text-slate-300 group-hover:text-slate-500">
                                  {targetDate.getDate()}
                                </div>

                                {lesson ? (
                                    <div className="flex flex-col h-full leading-[1.1] justify-between py-1">
                                      <div className="text-[8px] font-black opacity-60 uppercase border-b border-slate-300/50 pb-0.5 mb-1">
                                        {lesson.kindOfStudyAbbr}/Т.{lesson.themeNumber || '—'}
                                      </div>
                                      <div className="text-[10px] font-black truncate w-full text-center tracking-tighter">
                                        {lesson.disciplineAbbreviation}
                                      </div>
                                      <div className={cn("text-[8px] font-mono font-black text-right mt-1 opacity-80")}>
                                        {lesson.auditoriumNames[0]}
                                      </div>
                                    </div>
                                ) : activeConstraint ? (
                                    <div className="flex flex-col items-center justify-center h-full opacity-60">
                                      <ShieldAlert size={14} className="text-rose-400" />
                                      <span className="text-[6px] font-black uppercase text-rose-500 mt-0.5 truncate w-full text-center">
                                {activeConstraint.kindOfConstraint.slice(0, 5)}
                              </span>
                                    </div>
                                ) : null}
                              </td>
                          );
                        })}
                      </tr>
                  ))}
                  <tr className="h-1 bg-slate-300">
                    <td colSpan={weeks.length + 2} className="p-0"></td>
                  </tr>
                </React.Fragment>
            ))}
            </tbody>
          </table>
        </div>

        <div className="p-4 bg-slate-50 border-t border-slate-200 flex items-center justify-between">
          <div className="flex gap-6">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded bg-slate-200 border border-slate-400" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-tight">Занятие</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded bg-rose-50 border border-rose-200" />
              <span className="text-[10px] font-black text-rose-500 uppercase tracking-tight">Ограничение</span>
            </div>
          </div>
          <div className="flex items-center gap-2 text-slate-400">
            <Clock size={14} />
            <span className="text-[10px] font-bold uppercase tracking-widest text-nowrap">Бумажный формат • {weeks.length} недель</span>
          </div>
        </div>
      </div>
  );
};
