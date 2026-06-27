import React, { useMemo, useState } from 'react';
import { format, addDays, eachWeekOfInterval } from 'date-fns';
import { ru } from 'date-fns/locale';
import { ZoomIn, ZoomOut, Maximize2, Minimize2, Calendar } from 'lucide-react';
import { TimeSlotPair } from '../../types/api';
import { cn } from '../../utils/cn';

/**
 * Переиспользуемый каркас «академической сетки» (день × пара × неделя).
 *
 * Отвечает ТОЛЬКО за скелет таблицы: шапки месяцев/недель, sticky-колонки дней и
 * пар, зум и полноэкранный режим. Содержимое каждой ячейки задаётся снаружи через
 * render-prop `renderCell` (паттерн Strategy) — поэтому каркас открыт для новых
 * видов сетки (расписание, ограничения, …) без собственных изменений (OCP).
 */

export type ZoomLevel = 0 | 1 | 2;

export interface SlotDef {
  id: TimeSlotPair;
  label: string;
  time: string;
}

export interface DayDef {
  id: number;
  label: string;
}

/** Дни недели сетки (Пн–Сб). Единый источник для всех потребителей каркаса. */
export const DAYS: DayDef[] = [
  { id: 1, label: 'Пн' },
  { id: 2, label: 'Вт' },
  { id: 3, label: 'Ср' },
  { id: 4, label: 'Чт' },
  { id: 5, label: 'Пт' },
  { id: 6, label: 'Сб' },
];

/** Учебные пары. Единый источник для всех потребителей каркаса. */
export const SLOTS: SlotDef[] = [
  { id: 'FIRST', label: '1', time: '9:00' },
  { id: 'SECOND', label: '2', time: '10:55' },
  { id: 'THIRD', label: '3', time: '12:50' },
  { id: 'FOURTH', label: '4', time: '16:20' },
];

/** Размеры шрифта контента ячейки по уровню зума (общий источник для всех сеток). */
export interface ZoomFontClasses {
  /** Основной текст ячейки. */
  main: string;
  /** Аббревиатура (на 2pt крупнее основного). */
  abbr: string;
  /** Вспомогательный мелкий текст. */
  sub: string;
}

export const zoomFontClasses = (zoom: ZoomLevel): ZoomFontClasses => ({
  main: zoom === 0 ? 'text-[7px]' : zoom === 1 ? 'text-[10px]' : 'text-[12px]',
  abbr: zoom === 0 ? 'text-[9px]' : zoom === 1 ? 'text-[12px]' : 'text-[14px]',
  sub: zoom === 0 ? 'text-[6px]' : zoom === 1 ? 'text-[8px]' : 'text-[10px]',
});

/** Контекст ячейки, передаваемый в renderCell. */
export interface GridCellContext {
  /** Дата ячейки. */
  date: Date;
  /** Дата в формате yyyy-MM-dd (ключ сетки). */
  dateStr: string;
  /** Понедельник недели, к которой относится ячейка. */
  monday: Date;
  /** Индекс недели (колонки) от начала периода. */
  weekIdx: number;
  /** Описание пары. */
  slot: SlotDef;
  /** Индекс пары в дне (0..3). */
  slotIdx: number;
  /** Текущий уровень зума — для адаптивного размера контента ячейки. */
  zoom: ZoomLevel;
}

export interface AcademicGridShellProps {
  startDate: Date;
  endDate: Date;
  /** Заголовок в левой части тулбара. */
  title?: string;
  /** Доп. элементы тулбара справа от заголовка (легенда, переключатели и т.п.). */
  toolbarExtras?: React.ReactNode;
  /** Плавающий оверлей поверх сетки (подсказки, тосты). */
  overlay?: React.ReactNode;
  /**
   * Рендерит содержимое одной ячейки расписания.
   * ДОЛЖЕН вернуть один элемент `<td>` — каркас оборачивает его ключом сам.
   */
  renderCell: (ctx: GridCellContext) => React.ReactNode;
}

export const AcademicGridShell: React.FC<AcademicGridShellProps> = ({
  startDate,
  endDate,
  title = 'Академическая сетка',
  toolbarExtras,
  overlay,
  renderCell,
}) => {
  const [zoom, setZoom] = useState<ZoomLevel>(0);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const mondays = useMemo(
    () => eachWeekOfInterval({ start: startDate, end: endDate }, { weekStartsOn: 1 }),
    [startDate, endDate]
  );

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

  const borderClass = 'border-slate-300';
  const headerBorderClass = 'border-slate-400';

  const cellHeight = zoom === 0 ? 'h-9' : zoom === 1 ? 'h-14' : 'h-24';
  const containerMaxHeight = isFullscreen ? 'h-[90vh]' : 'max-h-[700px]';

  return (
    <div
      className={cn(
        'space-y-2 animate-fade-in transition-all duration-500',
        isFullscreen && 'fixed inset-0 z-[100] bg-slate-50 p-4 overflow-hidden flex flex-col'
      )}
    >
      <div className="flex items-center justify-between bg-slate-900 text-white p-1 px-3 rounded-xl shadow-lg shrink-0">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 border-r border-slate-700 pr-4 py-1">
            <Calendar size={14} className="text-blue-400" />
            <span className="text-[10px] font-black uppercase tracking-widest">{title}</span>
          </div>

          <div className="flex items-center gap-1 bg-slate-800 rounded-lg p-0.5">
            <button
              onClick={() => setZoom((z) => (Math.max(0, z - 1) as ZoomLevel))}
              className="p-1 hover:bg-slate-700 rounded transition-all text-slate-400 hover:text-white"
              disabled={zoom === 0}
            >
              <ZoomOut size={14} />
            </button>
            <span className="text-[9px] font-black w-16 text-center text-slate-300">
              {zoom === 0 ? 'MIN' : zoom === 1 ? 'MID' : 'MAX'}
            </span>
            <button
              onClick={() => setZoom((z) => (Math.min(2, z + 1) as ZoomLevel))}
              className="p-1 hover:bg-slate-700 rounded transition-all text-slate-400 hover:text-white"
              disabled={zoom === 2}
            >
              <ZoomIn size={14} />
            </button>
          </div>

          {toolbarExtras}
        </div>

        <button
          onClick={() => setIsFullscreen((v) => !v)}
          className="flex items-center gap-2 px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-all text-[9px] font-black uppercase tracking-tighter"
        >
          {isFullscreen ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
          {isFullscreen ? 'Свернуть' : 'Развернуть'}
        </button>
      </div>

      {overlay}

      <div
        className={cn(
          'bg-white shadow-2xl rounded-xl border-2 border-slate-300 overflow-hidden flex flex-col transition-all duration-500',
          containerMaxHeight
        )}
      >
        <div className="overflow-auto custom-scrollbar flex-1">
          <table className={cn('w-full border-collapse select-none', zoom === 0 ? 'table-fixed' : 'table-auto')}>
            <thead>
              <tr className="bg-slate-900 text-white">
                <th className={cn('w-6 border-r p-0.5 text-[8px] font-black uppercase sticky left-0 bg-slate-900 z-30', headerBorderClass)} rowSpan={3}>Дн</th>
                <th className={cn('w-10 border-r p-0.5 text-[8px] font-black uppercase sticky left-6 bg-slate-900 z-30', headerBorderClass)} rowSpan={3}>П</th>
                {mondays.map((_, idx) => (
                  <th key={idx} className={cn('border-r p-0.5 text-[7px] font-black bg-slate-800 text-slate-400', headerBorderClass)}>
                    {idx + 1}
                  </th>
                ))}
              </tr>
              <tr className="bg-slate-100">
                {monthHeaders.map((month, idx) => (
                  <th key={idx} colSpan={month.count} className={cn('border-r p-0.5 text-center text-[8px] font-black uppercase tracking-widest text-slate-500', headerBorderClass)}>
                    {month.name}
                  </th>
                ))}
              </tr>
              <tr className="bg-white border-b-2 border-slate-400">
                {mondays.map((monday, idx) => (
                  <th key={idx} className={cn('border-r p-0.5 text-[7px] font-bold text-slate-400', borderClass)}>
                    {format(monday, 'dd.MM')}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-300">
              {DAYS.map((day) => (
                <React.Fragment key={day.id}>
                  <tr className="bg-slate-50">
                    <td className={cn('border-r text-center font-black text-[9px] text-slate-900 sticky left-0 bg-slate-100 z-20 w-6', headerBorderClass)} rowSpan={5}>
                      <div className="rotate-90 whitespace-nowrap uppercase">{day.label}</div>
                    </td>
                    <td className={cn('border-r text-[7px] font-black text-slate-400 text-center sticky left-6 bg-slate-50 z-10 uppercase tracking-tighter h-5', borderClass)}>
                      D
                    </td>
                    {mondays.map((monday, idx) => (
                      <td key={idx} className={cn('border-r text-center text-[8px] font-black text-slate-700 bg-slate-50/50', borderClass)}>
                        {format(addDays(monday, day.id - 1), 'd')}
                      </td>
                    ))}
                  </tr>

                  {SLOTS.map((slot, slotIdx) => (
                    <tr key={slot.id} className={cn('group transition-all duration-300', cellHeight)}>
                      <td className={cn('border-r p-0.5 text-center sticky left-6 bg-white z-10 w-10 group-hover:bg-slate-50 transition-colors', borderClass)}>
                        <div className="font-black text-slate-800 text-[9px]">{slot.label}</div>
                        <div className="text-[6px] text-slate-400 font-mono leading-none">{slot.time}</div>
                      </td>

                      {mondays.map((monday, weekIdx) => {
                        const date = addDays(monday, day.id - 1);
                        const dateStr = format(date, 'yyyy-MM-dd');
                        return (
                          <React.Fragment key={weekIdx}>
                            {renderCell({ date, dateStr, monday, weekIdx, slot, slotIdx, zoom })}
                          </React.Fragment>
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
