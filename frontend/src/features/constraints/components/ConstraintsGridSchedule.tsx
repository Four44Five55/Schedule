import React from 'react';
import { format, parseISO } from 'date-fns';
import { ru } from 'date-fns/locale';
import { ConstraintDto } from '../../../types/api';
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
 * для маппинга диапазонов дат в дни. Ограничение покрывает день целиком, поэтому
 * аббревиатура выводится в каждой паре этого дня.
 */
export const ConstraintsGridSchedule: React.FC<ConstraintsGridScheduleProps> = ({
  constraints,
  startDate,
  endDate,
  entityLabel,
}) => {
  const lookup = useConstraintLookup(constraints);

  return (
    <AcademicGridShell
      startDate={startDate}
      endDate={endDate}
      title={entityLabel ? `Ограничения · ${entityLabel}` : 'Ограничения'}
      renderCell={({ dateStr, zoom }) => {
        const dayConstraints = lookup.get(dateStr);
        const primary = dayConstraints?.[0];
        const style = primary ? CONSTRAINT_STYLES[primary.kindOfConstraint] ?? FALLBACK_CONSTRAINT_STYLE : null;
        const abbrSize = zoom === 0 ? 'text-[9px]' : zoom === 1 ? 'text-[12px]' : 'text-[14px]';
        const extraCount = dayConstraints ? dayConstraints.length - 1 : 0;

        return (
          <td
            className={cn(
              'border-r border-slate-300 p-0.5 text-center align-middle transition-colors',
              primary ? cn(style!.cell, 'cursor-help') : 'bg-white hover:bg-slate-50/30'
            )}
            title={primary ? buildTooltip(dayConstraints!) : undefined}
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
