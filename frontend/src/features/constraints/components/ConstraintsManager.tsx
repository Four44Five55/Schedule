import React, { useState, useEffect, useMemo } from 'react';
import { parseISO } from 'date-fns';
import { ConstraintsService, ResourceService } from '../../../services/apiServices';
import {
  ConstraintDto, GroupDto, EducatorDto, AuditoriumDto, StudyPeriodDto, KindOfConstraints,
} from '../../../types/api';
import { Users, UserSquare2, School, Search, Loader2, ShieldAlert } from 'lucide-react';
import { ConstraintsGridSchedule } from './ConstraintsGridSchedule';
import { CONSTRAINT_STYLES, FALLBACK_CONSTRAINT_STYLE } from '../constraintStyles';
import { cn } from '../../../utils/cn';

type FilterType = 'group' | 'educator' | 'auditorium';

interface NamedEntity { id: number; name: string; }

export const ConstraintsManager: React.FC = () => {
  const [filterType, setFilterType] = useState<FilterType>(() => {
    const saved = localStorage.getItem('unischedule.constraints.filterType');
    return saved === 'group' || saved === 'educator' || saved === 'auditorium' ? saved : 'educator';
  });
  const [selectedId, setSelectedId] = useState<number | ''>(() => {
    const saved = localStorage.getItem('unischedule.constraints.selectedId');
    return saved ? Number(saved) : '';
  });

  const [resources, setResources] = useState<{ groups: GroupDto[]; educators: EducatorDto[]; auditoriums: AuditoriumDto[] }>(
    { groups: [], educators: [], auditoriums: [] }
  );
  const [studyPeriods, setStudyPeriods] = useState<StudyPeriodDto[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<StudyPeriodDto | null>(null);

  const [constraints, setConstraints] = useState<ConstraintDto[]>([]);
  const [loadingConstraints, setLoadingConstraints] = useState(false);
  const [loadingResources, setLoadingResources] = useState(true);

  // Справочники и периоды — один раз при монтировании.
  useEffect(() => {
    Promise.all([
      ResourceService.getGroups(),
      ResourceService.getEducators(),
      ResourceService.getAuditoriums(),
      ResourceService.getStudyPeriods(),
      ResourceService.getActiveStudyPeriod(),
    ]).then(([groups, educators, auditoriums, periods, active]) => {
      setResources({ groups, educators, auditoriums });
      setStudyPeriods(periods);
      setSelectedPeriod(active);
    }).finally(() => setLoadingResources(false));
  }, []);

  useEffect(() => { localStorage.setItem('unischedule.constraints.filterType', filterType); }, [filterType]);
  useEffect(() => { localStorage.setItem('unischedule.constraints.selectedId', String(selectedId)); }, [selectedId]);

  // Ограничения выбранной сущности.
  useEffect(() => {
    if (selectedId === '') { setConstraints([]); return; }
    setLoadingConstraints(true);
    const id = selectedId;
    const promise =
      filterType === 'group' ? ConstraintsService.getGroupConstraintsByGroup(id)
        : filterType === 'educator' ? ConstraintsService.getEducatorConstraintsByEducator(id)
          : ConstraintsService.getAuditoriumConstraintsByAuditorium(id);
    let cancelled = false;
    promise
      .then((data) => { if (!cancelled) setConstraints(data); })
      .catch(() => { if (!cancelled) setConstraints([]); })
      .finally(() => { if (!cancelled) setLoadingConstraints(false); });
    return () => { cancelled = true; };
  }, [selectedId, filterType]);

  const entities: NamedEntity[] = useMemo(() => {
    const list = filterType === 'group' ? resources.groups
      : filterType === 'educator' ? resources.educators
        : resources.auditoriums;
    return [...list].sort((a, b) => a.name.localeCompare(b.name, 'ru'));
  }, [filterType, resources]);

  const selectedEntity = useMemo(
    () => (selectedId === '' ? undefined : entities.find((e) => e.id === selectedId)),
    [entities, selectedId]
  );

  // Легенда строится из самих данных (только встретившиеся виды) — без хардкода
  // подписей: аббревиатура и полное имя приходят с бэка.
  const legendItems = useMemo(() => {
    const seen = new Map<KindOfConstraints, { abbreviation: string; fullName: string }>();
    for (const c of constraints) {
      if (!seen.has(c.kindOfConstraint)) seen.set(c.kindOfConstraint, { abbreviation: c.abbreviation, fullName: c.fullName });
    }
    return [...seen.entries()].map(([kind, v]) => ({ kind, ...v }));
  }, [constraints]);

  const periodDates = useMemo(() => {
    if (!selectedPeriod) return null;
    return { start: parseISO(selectedPeriod.startDate), end: parseISO(selectedPeriod.endDate) };
  }, [selectedPeriod]);

  if (loadingResources) {
    return <div className="p-8 text-center animate-pulse text-slate-400">Загрузка справочников...</div>;
  }

  return (
    <div className="space-y-4">
      {/* Панель выбора: тип сущности + объект + период */}
      <div className="bg-white border border-slate-100 rounded-xl p-4 shadow-sm space-y-3">
        <div className="flex flex-col lg:flex-row items-center gap-3">
          <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0">
            <FilterBtn active={filterType === 'group'} onClick={() => { setFilterType('group'); setSelectedId(''); }} icon={Users} label="Группы" />
            <FilterBtn active={filterType === 'educator'} onClick={() => { setFilterType('educator'); setSelectedId(''); }} icon={UserSquare2} label="Преподы" />
            <FilterBtn active={filterType === 'auditorium'} onClick={() => { setFilterType('auditorium'); setSelectedId(''); }} icon={School} label="Ауд." />
          </div>

          <div className="relative flex-1 w-full">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <select
              value={selectedId}
              onChange={(e) => setSelectedId(e.target.value ? Number(e.target.value) : '')}
              className="w-full pl-9 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-bold text-slate-700 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
            >
              <option value="">Выберите объект...</option>
              {entities.map((e) => (
                <option key={e.id} value={e.id}>{e.name}</option>
              ))}
            </select>
          </div>

          <select
            value={selectedPeriod?.id || ''}
            onChange={(e) => setSelectedPeriod(studyPeriods.find((p) => p.id === Number(e.target.value)) || null)}
            className="flex-1 w-full px-3 py-1.5 bg-blue-50 border border-blue-100 rounded-lg text-xs font-bold text-blue-900 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
          >
            <option value="">Выберите период...</option>
            {studyPeriods.map((p) => (
              <option key={p.id} value={p.id}>{p.name} ({p.startDate} — {p.endDate})</option>
            ))}
          </select>

          {loadingConstraints && <Loader2 size={14} className="animate-spin text-blue-600 shrink-0" />}
        </div>

        {/* Легенда видов ограничений (только встретившиеся) */}
        {legendItems.length > 0 && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-1 border-t border-slate-100">
            {legendItems.map((item) => (
              <div key={item.kind} className="flex items-center gap-1.5">
                <span className={cn('w-2.5 h-2.5 rounded-sm', (CONSTRAINT_STYLES[item.kind] ?? FALLBACK_CONSTRAINT_STYLE).dot)} />
                <span className="text-[11px] text-slate-600">
                  <span className="font-black">{item.abbreviation}</span> — {item.fullName}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {selectedEntity && periodDates ? (
        <ConstraintsGridSchedule
          constraints={constraints}
          startDate={periodDates.start}
          endDate={periodDates.end}
          entityLabel={selectedEntity.name}
        />
      ) : (
        <EmptyState
          label={!selectedEntity ? 'Выберите объект для просмотра ограничений' : 'Выберите учебный период'}
        />
      )}
    </div>
  );
};

const FilterBtn = ({ active, onClick, icon: Icon, label }: { active: boolean; onClick: () => void; icon: React.ElementType; label: string }) => (
  <button
    onClick={onClick}
    className={cn(
      'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-black transition-colors',
      active ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
    )}
  >
    <Icon size={14} /> {label}
  </button>
);

const EmptyState = ({ label }: { label: string }) => (
  <div className="bg-white border border-slate-100 rounded-xl p-8 shadow-sm text-center">
    <div className="flex flex-col items-center gap-3">
      <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
        <ShieldAlert className="text-slate-400" size={24} />
      </div>
      <p className="text-sm font-bold text-slate-900">{label}</p>
    </div>
  </div>
);
