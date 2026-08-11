import React, { useEffect, useId, useMemo, useState } from 'react';
import { eachDayOfInterval, format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { ZoomIn, ZoomOut, Maximize2, Minimize2, Calendar } from 'lucide-react';
import { cn } from '../../utils/cn';
import { ZoomLevel } from './AcademicGridShell';

/**
 * Переиспользуемый каркас «тайм-лайна» (строки = сущности × столбцы = дни).
 *
 * Топология отличается от {@link AcademicGridShell} (там «пара × день × неделя» для
 * ОДНОЙ сущности): здесь много сущностей сразу, по одному дню на столбец, без пар —
 * формат «диаграммы Ганта». Отвечает за скелет + общие взаимодействия каркаса:
 * sticky-колонка сущностей, шапка месяцев + дни, зум, fullscreen, **cross-hair**
 * подсветка строки/столбца при наведении и **drag-выделение диапазона** в строке.
 * Содержимое ячейки задаёт потребитель через render-prop `renderCell` (Strategy),
 * возвращая дескриптор {@link TimelineCell} (контент + класс), а `<td>` со всеми
 * обработчиками владеет сам каркас — поэтому cross-hair/drag живут здесь и
 * переиспользуются будущими потребителями (обзор расписания) без их участия (OCP).
 *
 * Подсветка реализована инъекцией одного `<style>` с `nth-child` (по индексам строки
 * и столбца), а не пропами в каждую ячейку — это исключает ре-рендер тысяч ячеек при
 * движении мыши. Воскресенья в столбцы не выводятся (учебная неделя Пн–Сб).
 */

export interface TimelineEntity {
  id: number;
  label: string;
  /** Необязательная вторая строка под названием (напр. список групп потока). */
  sublabel?: string;
}

/** Контекст дневной ячейки, передаваемый в renderCell. */
export interface TimelineCellContext {
  entity: TimelineEntity;
  date: Date;
  /** Дата в формате yyyy-MM-dd (ключ). */
  dateStr: string;
  zoom: ZoomLevel;
}

/** Дескриптор содержимого ячейки. Сам `<td>` (обработчики, подсветка) рисует каркас. */
export interface TimelineCell {
  content?: React.ReactNode;
  /** Доп. классы ячейки (напр. фон ограничения). */
  className?: string;
  title?: string;
}

export interface EntityTimelineShellProps {
  entities: TimelineEntity[];
  startDate: Date;
  endDate: Date;
  /** Заголовок в левой части тулбара. */
  title?: string;
  /** Доп. элементы тулбара (легенда, выбор вида ограничения, тумблер кисти и т.п.). */
  toolbarExtras?: React.ReactNode;
  /** Плавающий оверлей поверх каркаса. */
  overlay?: React.ReactNode;
  /** Рендерит содержимое одной дневной ячейки. */
  renderCell: (ctx: TimelineCellContext) => TimelineCell;
  /** Клик по заголовку дня-столбца (напр. массовое действие «по всем»). */
  onColumnHeaderClick?: (dateStr: string, date: Date) => void;
  /**
   * Если true — левый drag по строке выделяет диапазон дней (с превью), а на отпускании
   * вызывается {@link onRangeSelect}. Если false — каркас только просматривается.
   */
  paintMode?: boolean;
  /**
   * Цвет превью протяжки: `create` — синий (что-то появится), `erase` — красный (что-то
   * исчезнет). Разные цвета нужны, потому что жест один и тот же, а последствия обратные:
   * протянуть красным по занятым дням — это удаление, и увидеть это надо ДО отпускания кнопки.
   */
  paintTone?: 'create' | 'erase';
  /** Выбран диапазон дней одной сущности (start ≤ end). Одиночный клик: start === end. */
  onRangeSelect?: (entity: TimelineEntity, startDateStr: string, endDateStr: string) => void;
}

/** Однобуквенные дни недели Пн..Сб для шапки (индекс getDay(): 1=Пн … 6=Сб). */
const WEEKDAY_LETTER: Record<number, string> = { 1: 'П', 2: 'В', 3: 'С', 4: 'Ч', 5: 'П', 6: 'С' };

interface DragState { row: number; start: number; end: number; }

export const EntityTimelineShell: React.FC<EntityTimelineShellProps> = ({
  entities,
  startDate,
  endDate,
  title = 'Тайм-лайн',
  toolbarExtras,
  overlay,
  renderCell,
  onColumnHeaderClick,
  paintMode = false,
  paintTone = 'create',
  onRangeSelect,
}) => {
  const [zoom, setZoom] = useState<ZoomLevel>(0);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [hover, setHover] = useState<{ row: number; col: number } | null>(null);
  const [drag, setDrag] = useState<DragState | null>(null);

  const rawId = useId();
  const cls = `tl-${rawId.replace(/:/g, '')}`;

  // Столбцы — учебные дни периода (без воскресений).
  const days = useMemo(
    () => eachDayOfInterval({ start: startDate, end: endDate }).filter((d) => d.getDay() !== 0),
    [startDate, endDate]
  );

  const monthHeaders = useMemo(() => {
    const headers: { name: string; count: number }[] = [];
    days.forEach((d) => {
      const name = format(d, 'LLLL', { locale: ru });
      const last = headers[headers.length - 1];
      if (last && last.name === name) last.count++;
      else headers.push({ name, count: 1 });
    });
    return headers;
  }, [days]);

  // Завершение drag по отпусканию кнопки где угодно (в т.ч. вне таблицы).
  useEffect(() => {
    if (!drag) return;
    const onUp = () => {
      const a = Math.min(drag.start, drag.end);
      const b = Math.max(drag.start, drag.end);
      const entity = entities[drag.row];
      if (entity && days[a] && days[b]) {
        onRangeSelect?.(entity, format(days[a], 'yyyy-MM-dd'), format(days[b], 'yyyy-MM-dd'));
      }
      setDrag(null);
    };
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, [drag, entities, days, onRangeSelect]);

  // Подсветка одним <style> (cross-hair + превью drag) — без ре-рендера ячеек.
  const styleText = useMemo(() => {
    const wash = 'box-shadow: inset 0 0 0 9999px rgba(59,130,246,.07);';
    const dragWash = paintTone === 'erase'
        ? 'box-shadow: inset 0 0 0 9999px rgba(239,68,68,.25);'
        : 'box-shadow: inset 0 0 0 9999px rgba(37,99,235,.22);';
    const rules: string[] = [];
    if (hover) {
      rules.push(`.${cls} tbody td:nth-child(${hover.col + 2}){${wash}}`);
      rules.push(`.${cls} thead tr:last-child th:nth-child(${hover.col + 1}){background:#dbeafe;}`);
      rules.push(`.${cls} tbody tr[data-row="${hover.row}"] td{${wash}}`);
      rules.push(`.${cls} tbody tr[data-row="${hover.row}"] td:first-child{background:#dbeafe;}`);
    }
    if (drag) {
      const a = Math.min(drag.start, drag.end) + 2;
      const b = Math.max(drag.start, drag.end) + 2;
      rules.push(`.${cls} tbody tr[data-row="${drag.row}"] td:nth-child(n+${a}):nth-child(-n+${b}){${dragWash}}`);
    }
    return rules.join('\n');
  }, [hover, drag, cls, paintTone]);

  const borderClass = 'border-slate-300';
  const headerBorderClass = 'border-slate-400';

  const rowHeight = zoom === 0 ? 'h-7' : zoom === 1 ? 'h-10' : 'h-14';
  const dayColWidth = zoom === 0 ? 'w-5' : zoom === 1 ? 'w-8' : 'w-12';
  const entityColWidth = zoom === 0 ? 'w-28' : zoom === 1 ? 'w-40' : 'w-52';
  const containerMaxHeight = isFullscreen ? 'h-[90vh]' : 'max-h-[700px]';

  // Тело таблицы НЕ зависит от hover/drag (подсветка — отдельным <style>), поэтому
  // мемоизируем его: движение мыши не перезапускает renderCell на тысячах ячеек.
  const bodyRows = useMemo(
    () => entities.map((entity, rowIdx) => (
      <tr key={entity.id} data-row={rowIdx} className={rowHeight}>
        <td className={cn('border-r px-2 sticky left-0 bg-white z-10', entityColWidth, borderClass)}>
          <div className="font-black text-[10px] text-slate-800 truncate">{entity.label}</div>
          {entity.sublabel && (
            <div className="text-[7px] text-slate-400 truncate leading-tight">{entity.sublabel}</div>
          )}
        </td>
        {days.map((d, colIdx) => {
          const dateStr = format(d, 'yyyy-MM-dd');
          const cell = renderCell({ entity, date: d, dateStr, zoom });
          return (
            <td
              key={dateStr}
              title={cell.title}
              className={cn('border-r border-slate-300 p-0 text-center align-middle', paintMode && 'cursor-crosshair', cell.className)}
              onMouseEnter={() => {
                setHover({ row: rowIdx, col: colIdx });
                setDrag((prev) => (prev ? { ...prev, end: colIdx } : prev));
              }}
              onMouseDown={paintMode ? (e) => {
                if (e.button !== 0) return;
                e.preventDefault();
                setDrag({ row: rowIdx, start: colIdx, end: colIdx });
              } : undefined}
            >
              {cell.content}
            </td>
          );
        })}
      </tr>
    )),
    [entities, days, renderCell, zoom, paintMode, rowHeight, entityColWidth]
  );

  return (
    <div
      className={cn(
        'space-y-2 animate-fade-in transition-all duration-500',
        isFullscreen && 'fixed inset-0 z-[100] bg-slate-50 p-4 overflow-hidden flex flex-col'
      )}
    >
      <style>{styleText}</style>

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
        <div className="overflow-auto custom-scrollbar flex-1" onMouseLeave={() => setHover(null)}>
          <table className={cn('border-collapse select-none', cls)}>
            <thead>
              <tr className="bg-slate-900 text-white">
                <th
                  className={cn('border-r p-1 text-[8px] font-black uppercase sticky left-0 bg-slate-900 z-30 text-left', entityColWidth, headerBorderClass)}
                  rowSpan={2}
                >
                  Сущность
                </th>
                {monthHeaders.map((month, idx) => (
                  <th
                    key={idx}
                    colSpan={month.count}
                    className={cn('border-r p-0.5 text-center text-[8px] font-black uppercase tracking-widest text-slate-300 bg-slate-800', headerBorderClass)}
                  >
                    {month.name}
                  </th>
                ))}
              </tr>
              <tr className="bg-white border-b-2 border-slate-400">
                {days.map((d) => {
                  const dateStr = format(d, 'yyyy-MM-dd');
                  const isSat = d.getDay() === 6;
                  return (
                    <th
                      key={dateStr}
                      title={format(d, 'EEEE, dd.MM.yyyy', { locale: ru })}
                      onClick={onColumnHeaderClick ? () => onColumnHeaderClick(dateStr, d) : undefined}
                      className={cn(
                        'border-r p-0.5 text-center align-middle leading-none', dayColWidth, borderClass,
                        isSat ? 'bg-slate-100' : 'bg-white',
                        onColumnHeaderClick && 'cursor-pointer hover:bg-blue-50'
                      )}
                    >
                      <div className="text-[6px] font-bold text-slate-400">{WEEKDAY_LETTER[d.getDay()]}</div>
                      <div className="text-[8px] font-black text-slate-700">{format(d, 'd')}</div>
                    </th>
                  );
                })}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-200">
              {bodyRows}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
