import React, { useCallback, useEffect, useRef, useState } from 'react';
import { format, parseISO } from 'date-fns';
import { ru } from 'date-fns/locale';
import { Eye, Brush, Eraser } from 'lucide-react';
import { ConstraintDto, KindOfConstraints, TimeSlotPair } from '../../../types/api';
import { AcademicGridShell } from '../../../components/grid/AcademicGridShell';
import { cn } from '../../../utils/cn';
import { useEnums } from '../../../context/EnumContext';
import { useConstraintLookup } from '../hooks/useConstraintLookup';
import { CONSTRAINT_STYLES, FALLBACK_CONSTRAINT_STYLE } from '../constraintStyles';

type Mode = 'view' | 'brush' | 'erase';

interface ConstraintsGridScheduleProps {
  constraints: ConstraintDto[];
  startDate: Date;
  endDate: Date;
  /** Подпись сущности для заголовка тулбара (напр. «Иванов И.И.»). */
  entityLabel?: string;
  /**
   * Если задан — в режиме «Просмотр» клик по пустой ячейке отдаёт дату + пару
   * для создания ограничения через модалку (детальный ввод).
   */
  onCellSelect?: (dateStr: string, slot: TimeSlotPair) => void;
  /**
   * Если задан — доступен режим «Кисть»: прямоугольная протяжка по ячейкам (день, пара)
   * → массовое создание пер-парных ограничений. День недели = строка на все недели,
   * поэтому «все пары всех понедельников» = один прямоугольник по 4 строкам Пн.
   */
  onPaintCreate?: (cells: { dateStr: string; slot: TimeSlotPair }[], kind: KindOfConstraints) => Promise<void>;
  /**
   * Если задан — доступен режим «Ластик»: протяжка по занятым ячейкам → удаление
   * покрывающих их ограничений (по id).
   */
  onErase?: (constraintIds: number[]) => Promise<void>;
}

/** Формат периода ограничения для тултипа. */
const formatRange = (start: string, end: string) =>
  `${format(parseISO(start), 'dd.MM.yyyy', { locale: ru })} – ${format(parseISO(end), 'dd.MM.yyyy', { locale: ru })}`;

/** Тултип ячейки: все ограничения дня (полное имя + период + описание). */
const buildTooltip = (dayConstraints: ConstraintDto[]): string =>
  dayConstraints
    .map((c) => {
      const head = `${c.fullName} (${c.abbreviation})`;
      const range = formatRange(c.startDate, c.endDate);
      return c.description ? `${head}\n${range}\n${c.description}` : `${head}\n${range}`;
    })
    .join('\n\n');

const cellKey = (dateStr: string, slot: TimeSlotPair) => `${dateStr}|${slot}`;

/**
 * Сетка ограничений одной сущности — день × пара × неделя, как у расписания.
 *
 * Три режима ввода (аналогично Ганту):
 * - **Просмотр** — клик по пустой ячейке → {@link onCellSelect} (модалка с деталями);
 * - **Кисть** — прямоугольная протяжка по пустым ячейкам → {@link onPaintCreate}
 *   (массовое пер-парное создание выбранного вида);
 * - **Ластик** — прямоугольная протяжка по занятым ячейкам → {@link onErase}
 *   (удаление покрывающих ограничений по id).
 *
 * Выбор ПРЯМОУГОЛЬНИКОМ (якорь при нажатии → текущий угол при движении), а не по следу
 * курсора, поэтому блок «4 пары × N недель» выделяется одним движением. Позиция ячейки в
 * прямоугольнике — (rowIndex = (деньНедели-1)*4 + индексПары, colIndex = индексНедели);
 * маппинг координат в дату/пару берём из ref-карты, заполняемой при рендере ячеек.
 */
export const ConstraintsGridSchedule: React.FC<ConstraintsGridScheduleProps> = ({
  constraints,
  startDate,
  endDate,
  entityLabel,
  onCellSelect,
  onPaintCreate,
  onErase,
}) => {
  const lookup = useConstraintLookup(constraints);
  const { kindOfConstraints } = useEnums();

  const [mode, setMode] = useState<Mode>('view');
  const [selectedKind, setSelectedKind] = useState<KindOfConstraints>(
    (kindOfConstraints[0]?.value as KindOfConstraints) ?? 'OTHER'
  );
  const [busy, setBusy] = useState(false);
  // Выделенные (за текущую протяжку) ячейки — в state для подсветки и в ref для чтения
  // в обработчиках. Побочные эффекты (создание/удаление) вызываем ВНЕ state-updater,
  // иначе StrictMode дважды выполнит updater и задублирует запросы.
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const selectedRef = useRef<Set<string>>(new Set());
  const paintingRef = useRef(false);
  const anchorRef = useRef<{ row: number; col: number } | null>(null);
  // Карта координат (rowIndex_colIndex → ячейка), заполняется при рендере (все ячейки видимы).
  const coordToCell = useRef<Map<string, { dateStr: string; slot: TimeSlotPair }>>(new Map());

  const setSelectedBoth = useCallback((next: Set<string>) => {
    selectedRef.current = next;
    setSelected(next);
  }, []);

  // Занята ли ячейка (целодневным или пер-парным ограничением этой пары).
  const cellOccupied = useCallback((dateStr: string, slot: TimeSlotPair) => {
    const dc = lookup.get(dateStr);
    return !!dc?.some((c) => !c.timeSlot || c.timeSlot === slot);
  }, [lookup]);

  // Прямоугольник между двумя углами → ключи «действенных» для режима ячеек
  // (Кисть — только пустые; Ластик — только занятые).
  const buildRect = useCallback((a: { row: number; col: number }, b: { row: number; col: number }): Set<string> => {
    const r0 = Math.min(a.row, b.row), r1 = Math.max(a.row, b.row);
    const c0 = Math.min(a.col, b.col), c1 = Math.max(a.col, b.col);
    const keys = new Set<string>();
    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        const cell = coordToCell.current.get(`${r}_${c}`);
        if (!cell) continue;
        const occ = cellOccupied(cell.dateStr, cell.slot);
        if (mode === 'brush' && occ) continue;
        if (mode === 'erase' && !occ) continue;
        keys.add(cellKey(cell.dateStr, cell.slot));
      }
    }
    return keys;
  }, [mode, cellOccupied]);

  const startSelect = useCallback((row: number, col: number) => {
    paintingRef.current = true;
    anchorRef.current = { row, col };
    setSelectedBoth(buildRect({ row, col }, { row, col }));
  }, [buildRect, setSelectedBoth]);

  const extendSelect = useCallback((row: number, col: number) => {
    if (!paintingRef.current || !anchorRef.current) return;
    setSelectedBoth(buildRect(anchorRef.current, { row, col }));
  }, [buildRect, setSelectedBoth]);

  // Завершение протяжки: применить действие режима к выделенным ячейкам (читаем из ref).
  useEffect(() => {
    if (mode === 'view') return;
    const onUp = () => {
      if (!paintingRef.current) return;
      paintingRef.current = false;
      anchorRef.current = null;
      const keys = selectedRef.current;
      if (keys.size > 0) {
        if (mode === 'brush' && onPaintCreate) {
          const cells = Array.from(keys).map((k) => {
            const [dateStr, slot] = k.split('|');
            return { dateStr, slot: slot as TimeSlotPair };
          });
          setBusy(true);
          onPaintCreate(cells, selectedKind).finally(() => setBusy(false));
        } else if (mode === 'erase' && onErase) {
          const ids = new Set<number>();
          for (const k of keys) {
            const [dateStr, slot] = k.split('|');
            const dc = lookup.get(dateStr);
            dc?.filter((c) => !c.timeSlot || c.timeSlot === slot).forEach((c) => ids.add(c.id));
          }
          if (ids.size > 0) {
            setBusy(true);
            onErase(Array.from(ids)).finally(() => setBusy(false));
          }
        }
      }
      setSelectedBoth(new Set());
    };
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, [mode, onPaintCreate, onErase, selectedKind, lookup, setSelectedBoth]);

  const canBrush = !!onPaintCreate;
  const canErase = !!onErase;
  const editable = !!onCellSelect && mode === 'view';
  const painting = mode !== 'view';

  const switchMode = (m: Mode) => { setMode(m); setSelectedBoth(new Set()); };

  const toolbar = (canBrush || canErase) ? (
    <div className="flex items-center gap-2">
      <div className="flex bg-slate-800 rounded-lg p-0.5">
        <ModeBtn active={mode === 'view'} onClick={() => switchMode('view')} icon={Eye} label="Просмотр" />
        {canBrush && <ModeBtn active={mode === 'brush'} onClick={() => switchMode('brush')} icon={Brush} label="Кисть" />}
        {canErase && <ModeBtn active={mode === 'erase'} onClick={() => switchMode('erase')} icon={Eraser} label="Ластик" />}
      </div>
      {canBrush && (
        <select
          value={selectedKind}
          onChange={(e) => setSelectedKind(e.target.value as KindOfConstraints)}
          disabled={mode !== 'brush'}
          className="bg-slate-800 text-white text-[10px] font-bold rounded px-2 py-1 outline-none border border-slate-700 cursor-pointer disabled:opacity-40"
          title="Вид ограничения для постановки кистью"
        >
          {kindOfConstraints.map((k) => (
            <option key={k.value} value={k.value}>{k.abbreviation} — {k.label}</option>
          ))}
        </select>
      )}
      {painting && (
        <span className="text-[9px] text-slate-400 hidden lg:inline">
          {mode === 'brush' ? 'тяни прямоугольник · строка дня = все недели' : 'тяни по занятым ячейкам — удалить'}
        </span>
      )}
    </div>
  ) : undefined;

  return (
    <AcademicGridShell
      startDate={startDate}
      endDate={endDate}
      title={entityLabel ? `Ограничения · ${entityLabel}` : 'Ограничения'}
      toolbarExtras={toolbar}
      renderCell={({ date, dateStr, slot, slotIdx, weekIdx, zoom }) => {
        // Координаты ячейки для прямоугольного выделения. День недели: Пн=1..Сб=6 (getDay()).
        const dayId = date.getDay();
        const row = (dayId - 1) * 4 + slotIdx;
        const col = weekIdx;
        // Первая ячейка рендера (Пн, 1-я пара, 1-я неделя) — сбрасываем карту координат,
        // чтобы она не накапливала устаревшие записи при смене периода.
        if (row === 0 && col === 0) coordToCell.current = new Map();
        coordToCell.current.set(`${row}_${col}`, { dateStr, slot: slot.id });

        const dayConstraints = lookup.get(dateStr);
        const cellConstraints = dayConstraints?.filter((c) => !c.timeSlot || c.timeSlot === slot.id);
        const primary = cellConstraints?.[0];
        const style = primary ? CONSTRAINT_STYLES[primary.kindOfConstraint] ?? FALLBACK_CONSTRAINT_STYLE : null;
        const abbrSize = zoom === 0 ? 'text-[10px]' : zoom === 1 ? 'text-[13px]' : 'text-[15px]';
        const extraCount = cellConstraints ? cellConstraints.length - 1 : 0;
        const isSelected = painting && selected.has(cellKey(dateStr, slot.id));

        return (
          <td
            className={cn(
              'border-r border-slate-300 p-0.5 text-center align-middle transition-colors',
              primary ? cn(style!.cell, 'cursor-help') : 'bg-white hover:bg-slate-50/30',
              editable && !primary && 'cursor-pointer hover:bg-blue-50/60',
              painting && 'cursor-crosshair',
              isSelected && mode === 'brush' && 'bg-blue-200 ring-1 ring-inset ring-blue-500',
              isSelected && mode === 'erase' && 'bg-red-200 ring-1 ring-inset ring-red-500'
            )}
            title={primary ? buildTooltip(cellConstraints!) : undefined}
            onClick={editable ? () => onCellSelect!(dateStr, slot.id) : undefined}
            onMouseDown={painting && !busy ? (e) => { e.preventDefault(); startSelect(row, col); } : undefined}
            onMouseEnter={painting ? () => extendSelect(row, col) : undefined}
          >
            {primary && (
              <span className={cn('font-black tracking-tighter leading-none', abbrSize, style!.text)}>
                {primary.abbreviation}
                {extraCount > 0 && <span className="ml-0.5 opacity-60">+{extraCount}</span>}
              </span>
            )}
          </td>
        );
      }}
    />
  );
};

const ModeBtn = ({ active, onClick, icon: Icon, label }: { active: boolean; onClick: () => void; icon: React.ElementType; label: string }) => (
  <button
    onClick={onClick}
    className={cn(
      'flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-black transition-colors',
      active ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
    )}
  >
    <Icon size={12} /> {label}
  </button>
);
