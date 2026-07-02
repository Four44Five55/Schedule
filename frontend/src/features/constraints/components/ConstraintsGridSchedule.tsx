import React from 'react';
import { format, parseISO } from 'date-fns';
import { ru } from 'date-fns/locale';
import { ConstraintDto, TimeSlotPair } from '../../../types/api';
import { AcademicGridShell } from '../../../components/grid/AcademicGridShell';
import { cn } from '../../../utils/cn';
import { useConstraintLookup } from '../hooks/useConstraintLookup';
import { CONSTRAINT_STYLES, FALLBACK_CONSTRAINT_STYLE } from '../constraintStyles';

interface ConstraintsGridScheduleProps {
  constraints: ConstraintDto[];
  startDate: Date;
  endDate: Date;
  /** Подпись сущности для заголовка тулбара (напр. «Иванов И.И.»). */
  entityLabel?: string;
  /**
   * Если задан — ячейки кликабельны: клик отдаёт дату + пару для создания ограничения.
   * Без него сетка остаётся read-only (как в прежних потребителях).
   */
  onCellSelect?: (dateStr: string, slot: TimeSlotPair) => void;
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

/**
 * Сетка ограничений одной сущности — день × пара × неделя, как у расписания.
 *
 * Переиспользует презентационный каркас {@link AcademicGridShell} (содержимое
 * ячейки задаётся через render-prop) и чистый хук {@link useConstraintLookup}
 * для маппинга диапазонов дат в дни. Целодневное ограничение (timeSlot пуст)
 * выводится во всех парах дня; пер-парное — только в своей паре. Если передан
 * onCellSelect, клик по ячейке создаёт ограничение на конкретную (день, пара).
 */
export const ConstraintsGridSchedule: React.FC<ConstraintsGridScheduleProps> = ({
  constraints,
  startDate,
  endDate,
  entityLabel,
  onCellSelect,
}) => {
  const lookup = useConstraintLookup(constraints);
  const editable = !!onCellSelect;

  return (
    <AcademicGridShell
      startDate={startDate}
      endDate={endDate}
      title={entityLabel ? `Ограничения · ${entityLabel}` : 'Ограничения'}
      renderCell={({ dateStr, slot, zoom }) => {
        // Ячейку (день+пара) покрывают целодневные ограничения (timeSlot пуст)
        // и точечные, выставленные ровно на эту пару.
        const dayConstraints = lookup.get(dateStr);
        const cellConstraints = dayConstraints?.filter((c) => !c.timeSlot || c.timeSlot === slot.id);
        const primary = cellConstraints?.[0];
        const style = primary ? CONSTRAINT_STYLES[primary.kindOfConstraint] ?? FALLBACK_CONSTRAINT_STYLE : null;
        const abbrSize = zoom === 0 ? 'text-[10px]' : zoom === 1 ? 'text-[13px]' : 'text-[15px]';
        const extraCount = cellConstraints ? cellConstraints.length - 1 : 0;

        return (
          <td
            className={cn(
              'border-r border-slate-300 p-0.5 text-center align-middle transition-colors',
              primary ? cn(style!.cell, 'cursor-help') : 'bg-white hover:bg-slate-50/30',
              editable && !primary && 'cursor-pointer hover:bg-blue-50/60'
            )}
            title={primary ? buildTooltip(cellConstraints!) : undefined}
            onClick={editable ? () => onCellSelect!(dateStr, slot.id) : undefined}
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
