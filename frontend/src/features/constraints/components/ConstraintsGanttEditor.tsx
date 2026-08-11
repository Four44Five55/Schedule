import React, { useEffect, useMemo, useState } from 'react';
import { Eye, Brush, Eraser } from 'lucide-react';
import { ConstraintDto, KindOfConstraints } from '../../../types/api';
import { EntityTimelineShell, TimelineEntity, TimelineCell } from '../../../components/grid/EntityTimelineShell';
import { buildConstraintDateLookup } from '../hooks/useConstraintLookup';
import { useEnums } from '../../../context/EnumContext';
import { cn } from '../../../utils/cn';

/** Режимы работы — те же три, что в «Сетке» (ConstraintsGridSchedule), чтобы жесты не отличались. */
type Mode = 'view' | 'brush' | 'erase';

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
 *
 * В режиме «Ластик» (тот же жест, обратное действие — как в «Сетке»):
 * - drag по дням строки → снять все целодневные ограничения, ПЕРЕСЕКАЮЩИЕ диапазон;
 * - клик по заголовку дня → снять целодневные ограничения этого дня у всех сущностей.
 * Полоса удаляется целиком, даже если выделение задело лишь её край: ограничение — одна
 * запись с датами [start, end], «отрезать кусок» значило бы её править, а не удалять.
 * Превью протяжки здесь красное (см. paintTone у каркаса) — жест тот же, последствия обратные.
 *
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
  const { constraintKinds, getConstraintStyle } = useEnums();
  // В выборе — только действующие виды; погашенные остаются только на уже размеченном.
  const selectableKinds = useMemo(() => constraintKinds.filter((k) => k.active), [constraintKinds]);
  const [selectedKind, setSelectedKind] = useState<KindOfConstraints>(
    (constraintKinds.find((k) => k.active)?.code as KindOfConstraints) ?? ''
  );
  const [mode, setMode] = useState<Mode>('view');
  // Справочник грузится асинхронно: на первом рендере он пуст, и выбор остался бы пустым.
  useEffect(() => {
    if (!selectedKind) setSelectedKind((prev) => prev || (selectableKinds[0]?.code ?? ''));
  }, [selectableKinds, selectedKind]);
  const [busy, setBusy] = useState(false);

  // Ограничения по сущности «как есть» (диапазонами) — нужны ластику: снимая полосу, мы
  // работаем с самой записью, а не с днями, на которые она развёрнута.
  const listByEntity = useMemo(() => {
    const grouped = new Map<number, ConstraintDto[]>();
    for (const c of constraints) {
      const eid = entityIdOf(c);
      const list = grouped.get(eid);
      if (list) list.push(c);
      else grouped.set(eid, [c]);
    }
    return grouped;
  }, [constraints, entityIdOf]);

  // Те же ограничения, развёрнутые в карту дат — для отрисовки ячеек (общий разворот — DRY).
  const byEntity = useMemo(() => {
    const result = new Map<number, Map<string, ConstraintDto[]>>();
    for (const [eid, list] of listByEntity) result.set(eid, buildConstraintDateLookup(list));
    return result;
  }, [listByEntity]);

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

  /** Целодневные ограничения сущности, пересекающие [startStr, endStr]. */
  const wholeDayOverlapping = (entityId: number, startStr: string, endStr: string) =>
    (listByEntity.get(entityId) ?? [])
      .filter((c) => !c.timeSlot && c.startDate <= endStr && c.endDate >= startStr);

  // Выбран диапазон (или одиночный клик при start === end) по одной сущности.
  const handleRange = (entity: TimelineEntity, startStr: string, endStr: string) => {
    if (mode === 'erase') {
      const doomed = wholeDayOverlapping(entity.id, startStr, endStr);
      if (doomed.length === 0) return;
      run(() => Promise.all(doomed.map((c) => onDelete(c.id))).then(() => {}));
      return;
    }
    if (startStr === endStr) {
      // Одиночный клик: на занятом дне — снять полосы, на пустом — поставить один день.
      const wholeDay = byEntity.get(entity.id)?.get(startStr)?.filter((c) => !c.timeSlot) ?? [];
      if (wholeDay.length > 0) {
        run(() => Promise.all(wholeDay.map((c) => onDelete(c.id))).then(() => {}));
        return;
      }
    }
    // Вид обязателен: справочник мог не догрузиться или все виды погашены — тогда кисть
    // молча отправила бы пустой код и получила 400.
    if (!selectedKind) return;
    run(() => onCreate(entity.id, startStr, endStr, selectedKind));
  };

  // «По всем»: кисть ставит выбранный вид на этот день каждой сущности без целодневного
  // ограничения; ластик, наоборот, снимает целодневные ограничения этого дня у всех.
  const handleColumnHeader = (dateStr: string) => {
    if (mode === 'erase') {
      const doomed = entities.flatMap((e) => wholeDayOverlapping(e.id, dateStr, dateStr));
      if (doomed.length === 0) return;
      // Спрашиваем, потому что цена клика не видна глазом: задели один день — уходят ПОЛОСЫ
      // целиком, у всех сущностей сразу. Протяжку по одной строке не подтверждаем: там
      // выделение видно до отпускания кнопки. Симметричное «поставить по всем» — обратимо.
      if (!window.confirm(`Снять ${doomed.length} огранич. (целиком, вместе с их периодами) у всех сущностей?`)) return;
      run(() => Promise.all(doomed.map((c) => onDelete(c.id))).then(() => {}));
      return;
    }
    if (!selectedKind) return;
    const targets = entities.filter((e) => !byEntity.get(e.id)?.get(dateStr)?.some((c) => !c.timeSlot));
    if (targets.length === 0) return;
    run(() => Promise.all(targets.map((e) => onCreate(e.id, dateStr, dateStr, selectedKind))).then(() => {}));
  };

  const toolbar = (
    <div className="flex items-center gap-2">
      <div className="flex bg-slate-800 rounded-lg p-0.5">
        <ModeBtn active={mode === 'view'} onClick={() => setMode('view')} icon={Eye} label="Просмотр" />
        <ModeBtn active={mode === 'brush'} onClick={() => selectedKind && setMode('brush')}
                 icon={Brush} label="Кисть" disabled={!selectedKind} />
        <ModeBtn active={mode === 'erase'} onClick={() => setMode('erase')} icon={Eraser} label="Ластик" />
      </div>
      <select
        value={selectedKind}
        onChange={(e) => setSelectedKind(e.target.value as KindOfConstraints)}
        disabled={mode !== 'brush'}
        className="bg-slate-800 text-white text-[10px] font-bold rounded px-2 py-1 outline-none border border-slate-700 cursor-pointer disabled:opacity-40"
        title="Вид ограничения для постановки кистью"
      >
        {selectableKinds.map((k) => (
          <option key={k.code} value={k.code}>{k.shortName} — {k.name}</option>
        ))}
      </select>
      {mode !== 'view' && (
        <span className="text-[9px] text-slate-400 hidden lg:inline">
          {mode === 'brush'
            ? 'drag — период · клик — день/снять · клик по дате — по всем'
            : 'drag — снять период · клик по дате — снять у всех'}
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
      paintMode={mode !== 'view'}
      paintTone={mode === 'erase' ? 'erase' : 'create'}
      onRangeSelect={handleRange}
      onColumnHeaderClick={mode !== 'view' ? (dateStr) => handleColumnHeader(dateStr) : undefined}
      renderCell={({ entity, dateStr }): TimelineCell => {
        const dayConstraints = byEntity.get(entity.id)?.get(dateStr);
        const wholeDay = dayConstraints?.filter((c) => !c.timeSlot);
        const primary = wholeDay?.[0];
        const style = primary ? getConstraintStyle(primary.kindOfConstraint) : null;
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

const ModeBtn = ({ active, onClick, icon: Icon, label, disabled }: { active: boolean; onClick: () => void; icon: React.ElementType; label: string; disabled?: boolean }) => (
  <button
    onClick={onClick}
    disabled={disabled}
    title={disabled ? 'Нет доступных видов ограничений — заведите вид' : undefined}
    className={cn(
      'flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-black transition-colors',
      active ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
    )}
  >
    <Icon size={12} /> {label}
  </button>
);
