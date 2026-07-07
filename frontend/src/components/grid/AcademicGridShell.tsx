import React, { useEffect, useMemo, useRef, useState } from 'react';
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
 *
 * ЗУМ (Excel-подобный, равномерный, непрерывный): единый множитель `factor`
 * масштабирует ВСЁ — высоту строк, ширины «замороженных» колонок, кегли шапок и
 * подписей, а содержимое ячейки — снаружи через `ctx.factor` (те же базовые px × factor).
 * Диапазон {@link FACTOR_MIN}..{@link FACTOR_MAX} шагом {@link FACTOR_STEP}; Ctrl+колесо
 * или кнопки ±; в тулбаре — проценты. Никаких `transform: scale` — sticky-колонки и
 * попадание кликов/перетаскивания остаются корректными.
 */

/**
 * Дискретные уровни зума — ОСТАВЛЕНЫ только для {@link EntityTimelineShell} (гант
 * ограничений со своим зумом). Академическая сетка использует непрерывный `factor`.
 */
export type ZoomLevel = 0 | 1 | 2;

/** Границы и шаг непрерывного зума (100% = базовый масштаб: ячейка 60px). */
export const FACTOR_MIN = 0.4;
export const FACTOR_MAX = 1.6;
export const FACTOR_STEP = 0.1;

const clampFactor = (f: number) =>
  Math.min(FACTOR_MAX, Math.max(FACTOR_MIN, Math.round(f * 100) / 100));

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
  /** Множитель зума (Excel-подобный): базовые px контента множьте на него. */
  factor: number;
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
  /**
   * Tailwind-класс потолка высоты контейнера сетки в обычном (не fullscreen) режиме.
   * По умолчанию `max-h-[700px]`. Хост может передать вьюпорт-зависимую высоту
   * (напр. `max-h-[calc(100vh_-_200px)]` — подчёркивания = пробелы в Tailwind,
   * иначе calc невалиден), чтобы сетка тянулась до низа экрана.
   */
  maxHeightClass?: string;
  /**
   * Убирает тёмный тулбар (заголовок + кнопки зума + «Развернуть»). Зум остаётся
   * доступен колесом (Ctrl+колесо), а «Развернуть» — плавающей иконкой в углу
   * сетки. Используется там, где заголовок избыточен (раздел «Расписание»).
   * ВНИМАНИЕ: скрывает и `toolbarExtras` — не включать для сеток с легендой.
   */
  chromeless?: boolean;
  /**
   * Базовая высота строки в px при 100%; масштабируется множителем зума.
   * По умолчанию 60 (ячейка расписания 60×60 при 100%). Сетка ограничений
   * (одна строка текста) передаёт меньше, чтобы не раздуваться.
   */
  rowHeightBase?: number;
  /** Начальный масштаб (по умолчанию 0.8 = 80%). */
  initialFactor?: number;
}

export const AcademicGridShell: React.FC<AcademicGridShellProps> = ({
  startDate,
  endDate,
  title = 'Академическая сетка',
  toolbarExtras,
  overlay,
  renderCell,
  maxHeightClass = 'max-h-[700px]',
  chromeless = false,
  rowHeightBase = 60,
  initialFactor = 0.8,
}) => {
  const [factor, setFactor] = useState<number>(clampFactor(initialFactor));
  const [isFullscreen, setIsFullscreen] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Зум колесом мыши: Ctrl+колесо меняет масштаб непрерывно (шаг FACTOR_STEP),
  // обычное колесо скроллит сетку. Слушатель non-passive — иначе preventDefault
  // (отмена зума страницы браузером) не сработает. Тач-пинч тоже шлёт ctrl+wheel.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      if (!e.ctrlKey) return;
      e.preventDefault();
      setFactor((f) => clampFactor(f + (e.deltaY < 0 ? FACTOR_STEP : -FACTOR_STEP)));
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, []);

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

  // Равномерный зум: единый множитель тянет ВСЕ размеры каркаса. `s(base)` = базовый
  // размер в px при 100% × factor (минимум 1px, чтобы элемент не схлопнулся в 0).
  const s = (base: number) => Math.max(1, Math.round(base * factor));
  const rowHeightPx = s(rowHeightBase);
  const dayColW = s(24);   // «замороженная» колонка дня (буква)
  const pairColW = s(40);  // «замороженная» колонка пары
  const pad = s(2);        // базовый паддинг ячеек шапки/подписей (p-0.5 = 2px при 100%)

  const containerMaxHeight = isFullscreen ? 'h-[90vh]' : maxHeightClass;

  return (
    <div
      className={cn(
        'space-y-2 animate-fade-in transition-all duration-500',
        isFullscreen && 'fixed inset-0 z-[100] bg-slate-50 p-4 overflow-hidden flex flex-col'
      )}
    >
      {!chromeless && (
      <div className="flex items-center justify-between bg-slate-900 text-white p-1 px-3 rounded-xl shadow-lg shrink-0">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 border-r border-slate-700 pr-4 py-1">
            <Calendar size={14} className="text-blue-400" />
            <span className="text-[10px] font-black uppercase tracking-widest">{title}</span>
          </div>

          <div className="flex items-center gap-1 bg-slate-800 rounded-lg p-0.5">
            <button
              onClick={() => setFactor((f) => clampFactor(f - FACTOR_STEP))}
              className="p-1 hover:bg-slate-700 rounded transition-all text-slate-400 hover:text-white disabled:opacity-40"
              disabled={factor <= FACTOR_MIN}
              title="Мельче (Ctrl+колесо вниз)"
            >
              <ZoomOut size={14} />
            </button>
            <span className="text-[9px] font-black w-12 text-center text-slate-300 tabular-nums">
              {Math.round(factor * 100)}%
            </span>
            <button
              onClick={() => setFactor((f) => clampFactor(f + FACTOR_STEP))}
              className="p-1 hover:bg-slate-700 rounded transition-all text-slate-400 hover:text-white disabled:opacity-40"
              disabled={factor >= FACTOR_MAX}
              title="Крупнее (Ctrl+колесо вверх)"
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
      )}

      {overlay}

      <div
        className={cn(
          'relative bg-white shadow-2xl rounded-xl border-2 border-slate-300 overflow-hidden flex flex-col transition-all duration-500',
          containerMaxHeight
        )}
      >
        {chromeless && (
          <button
            onClick={() => setIsFullscreen((v) => !v)}
            title={isFullscreen ? 'Свернуть' : 'Развернуть'}
            className="absolute top-1 right-1 z-40 p-1.5 bg-slate-900/80 hover:bg-slate-900 text-white rounded-lg shadow-lg backdrop-blur transition-colors"
          >
            {isFullscreen ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
          </button>
        )}
        <div ref={scrollRef} className="overflow-auto custom-scrollbar flex-1">
          <table className="w-full border-collapse select-none table-auto">
            <thead>
              <tr className="bg-slate-900 text-white">
                <th
                  className={cn('border-r font-black uppercase sticky bg-slate-900 z-30', headerBorderClass)}
                  rowSpan={3}
                  style={{ width: dayColW, minWidth: dayColW, left: 0, fontSize: s(8), padding: pad }}
                >Дн</th>
                <th
                  className={cn('border-r font-black uppercase sticky bg-slate-900 z-30', headerBorderClass)}
                  rowSpan={3}
                  style={{ width: pairColW, minWidth: pairColW, left: dayColW, fontSize: s(8), padding: pad }}
                >П</th>
                {mondays.map((_, idx) => (
                  <th
                    key={idx}
                    className={cn('border-r font-black bg-slate-800 text-slate-400', headerBorderClass)}
                    style={{ fontSize: s(7), padding: pad }}
                  >
                    {idx + 1}
                  </th>
                ))}
              </tr>
              <tr className="bg-slate-100">
                {monthHeaders.map((month, idx) => (
                  <th
                    key={idx}
                    colSpan={month.count}
                    className={cn('border-r text-center font-black uppercase tracking-widest text-slate-500', headerBorderClass)}
                    style={{ fontSize: s(8), padding: pad }}
                  >
                    {month.name}
                  </th>
                ))}
              </tr>
              <tr className="bg-white border-b-2 border-slate-400">
                {mondays.map((monday, idx) => (
                  <th
                    key={idx}
                    className={cn('border-r font-bold text-slate-400', borderClass)}
                    style={{ fontSize: s(7), padding: pad }}
                  >
                    {format(monday, 'dd.MM')}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-300">
              {DAYS.map((day) => (
                <React.Fragment key={day.id}>
                  <tr className="bg-slate-50">
                    <td
                      className={cn('border-r text-center font-black text-slate-900 sticky bg-slate-100 z-20', headerBorderClass)}
                      rowSpan={5}
                      style={{ left: 0, width: dayColW, minWidth: dayColW, fontSize: s(9) }}
                    >
                      <div className="rotate-90 whitespace-nowrap uppercase">{day.label}</div>
                    </td>
                    <td
                      className={cn('border-r font-black text-slate-400 text-center sticky bg-slate-50 z-10 uppercase tracking-tighter', borderClass)}
                      style={{ left: dayColW, width: pairColW, minWidth: pairColW, height: s(20), fontSize: s(7) }}
                    >
                      D
                    </td>
                    {mondays.map((monday, idx) => (
                      <td
                        key={idx}
                        className={cn('border-r text-center font-black text-slate-700 bg-slate-50/50', borderClass)}
                        style={{ fontSize: s(8) }}
                      >
                        {format(addDays(monday, day.id - 1), 'd')}
                      </td>
                    ))}
                  </tr>

                  {SLOTS.map((slot, slotIdx) => (
                    <tr key={slot.id} style={{ height: rowHeightPx }} className="group transition-all duration-300">
                      <td
                        className={cn('border-r text-center sticky bg-white z-10 group-hover:bg-slate-50 transition-colors', borderClass)}
                        style={{ left: dayColW, width: pairColW, minWidth: pairColW, padding: pad }}
                      >
                        <div className="font-black text-slate-800" style={{ fontSize: s(9) }}>{slot.label}</div>
                        <div className="text-slate-400 font-mono leading-none" style={{ fontSize: s(6) }}>{slot.time}</div>
                      </td>

                      {mondays.map((monday, weekIdx) => {
                        const date = addDays(monday, day.id - 1);
                        const dateStr = format(date, 'yyyy-MM-dd');
                        return (
                          <React.Fragment key={weekIdx}>
                            {renderCell({ date, dateStr, monday, weekIdx, slot, slotIdx, factor })}
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
