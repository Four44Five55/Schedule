import React, { useMemo, useState } from 'react';
import { Eye, Brush } from 'lucide-react';
import { ConstraintDto, KindOfConstraints } from '../../../types/api';
import { EntityTimelineShell, TimelineEntity, TimelineCell } from '../../../components/grid/EntityTimelineShell';
import { buildConstraintDateLookup } from '../hooks/useConstraintLookup';
import { CONSTRAINT_STYLES, FALLBACK_CONSTRAINT_STYLE } from '../constraintStyles';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';

interface ConstraintsGanttEditorProps {
  entities: TimelineEntity[];
  /** Ограничения ВСЕХ сущностей выбранного типа (плоско). */
  constraints: ConstraintDto[];
  /** Достаёт id сущности из ограничения (educatorId/groupId/auditoriumId). */
  entityIdOf: (c: ConstraintDto) => number;
  startDate: Date;
  endDate: Date;
  /** Создать целодневное ограничение на диапазон дат [start, end]. */
  onCreate: (entityId: number, startDateStr: string, endDateStr: string, kind: KindOfConstraints) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  /** Сообщить хосту перечитать ограничения после изменений. */
  onChanged: () => void;
}

/**
 * Гант-редактор ограничений: строки = сущности типа, столбцы = дни. Работает только с
 * ЦЕЛОДНЕВНЫМИ ограничениями (timeSlot пуст) — пары здесь не рисуются (для пар есть
 * «Сетка»). Презентацию и жесты (cross-hair, drag-диапазон) даёт {@link EntityTimelineShell};
 * здесь — режим кисти, вид ограничения и вызовы onCreate/onDelete.
 *
 * Взаимодействие (в режиме «Кисть»):
 * - drag по дням строки → одно ограничение выбранного вида на диапазон [start, end];
 * - одиночный клик по пустому дню → ограничение на один день;
 * - клик по дню с целодневным ограничением → удалить покрывающие его «полосы»;
 * - клик по заголовку дня → выбранный вид всем сущностям на этот день («по всем»);
 * - пер-парные ограничения здесь не трогаются (управляются в «Сетке»).
 * В режиме «Просмотр» диаграмма только листается/изучается (cross-hair, тултипы).
 */
export const ConstraintsGanttEditor: React.FC<ConstraintsGanttEditorProps> = ({
  entities,
  constraints,
  entityIdOf,
  startDate,
  endDate,
  onCreate,
  onDelete,
  onChanged,
}) => {
  const { kindOfConstraints } = useEnums();
  const [selectedKind, setSelectedKind] = useState<KindOfConstraints>(
    (kindOfConstraints[0]?.value as KindOfConstraints) ?? 'OTHER'
  );
  const [brush, setBrush] = useState(false);
  const [busy, setBusy] = useState(false);

  // Группируем по сущности и разворачиваем диапазоны в карту дат (общий разворот — DRY).
  const byEntity = useMemo(() => {
    const grouped = new Map<number, ConstraintDto[]>();
    for (const c of constraints) {
      const eid = entityIdOf(c);
      const list = grouped.get(eid);
      if (list) list.push(c);
      else grouped.set(eid, [c]);
    }
    const result = new Map<number, Map<string, ConstraintDto[]>>();
    for (const [eid, list] of grouped) result.set(eid, buildConstraintDateLookup(list));
    return result;
  }, [constraints, entityIdOf]);

  const run = async (fn: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    try {
      await fn();
      onChanged();
    } finally {
      setBusy(false);
    }
  };

  // Выбран диапазон (или одиночный клик при start === end) по одной сущности.
  const handleRange = (entity: TimelineEntity, startStr: string, endStr: string) => {
    if (startStr === endStr) {
      // Одиночный клик: на занятом дне — снять полосы, на пустом — поставить один день.
      const wholeDay = byEntity.get(entity.id)?.get(startStr)?.filter((c) => !c.timeSlot) ?? [];
      if (wholeDay.length > 0) {
        run(() => Promise.all(wholeDay.map((c) => onDelete(c.id))).then(() => {}));
        return;
      }
    }
    run(() => onCreate(entity.id, startStr, endStr, selectedKind));
  };

  // «По всем»: ставим выбранный вид на этот день каждой сущности без целодневного ограничения.
  const handleColumnHeader = (dateStr: string) => {
    const targets = entities.filter((e) => !byEntity.get(e.id)?.get(dateStr)?.some((c) => !c.timeSlot));
    if (targets.length === 0) return;
    run(() => Promise.all(targets.map((e) => onCreate(e.id, dateStr, dateStr, selectedKind))).then(() => {}));
  };

  const toolbar = (
    <div className="flex items-center gap-2">
      <div className="flex bg-slate-800 rounded-lg p-0.5">
        <ModeBtn active={!brush} onClick={() => setBrush(false)} icon={Eye} label="Просмотр" />
        <ModeBtn active={brush} onClick={() => setBrush(true)} icon={Brush} label="Кисть" />
      </div>
      <select
        value={selectedKind}
        onChange={(e) => setSelectedKind(e.target.value as KindOfConstraints)}
        disabled={!brush}
        className="bg-slate-800 text-white text-[10px] font-bold rounded px-2 py-1 outline-none border border-slate-700 cursor-pointer disabled:opacity-40"
        title="Вид ограничения для постановки кистью"
      >
        {kindOfConstraints.map((k) => (
          <option key={k.value} value={k.value}>{k.abbreviation} — {k.label}</option>
        ))}
      </select>
      {brush && (
        <span className="text-[9px] text-slate-400 hidden lg:inline">
          drag — период · клик — день/снять · клик по дате — по всем
        </span>
      )}
    </div>
  );

  return (
    <EntityTimelineShell
      entities={entities}
      startDate={startDate}
      endDate={endDate}
      title="Ограничения · Гант"
      toolbarExtras={toolbar}
      paintMode={brush}
      onRangeSelect={handleRange}
      onColumnHeaderClick={brush ? (dateStr) => handleColumnHeader(dateStr) : undefined}
      renderCell={({ entity, dateStr }): TimelineCell => {
        const dayConstraints = byEntity.get(entity.id)?.get(dateStr);
        const wholeDay = dayConstraints?.filter((c) => !c.timeSlot);
        const primary = wholeDay?.[0];
        const style = primary ? CONSTRAINT_STYLES[primary.kindOfConstraint] ?? FALLBACK_CONSTRAINT_STYLE : null;
        const perPairOnly = !primary && (dayConstraints?.length ?? 0) > 0;

        return {
          className: primary ? style!.cell : undefined,
          title: primary ? `${primary.fullName} (${primary.abbreviation})` : undefined,
          content: primary ? (
            <span className={cn('font-black text-[8px] leading-none', style!.text)}>{primary.abbreviation}</span>
          ) : perPairOnly ? (
            <span className="text-slate-300 text-[10px] leading-none">·</span>
          ) : null,
        };
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
