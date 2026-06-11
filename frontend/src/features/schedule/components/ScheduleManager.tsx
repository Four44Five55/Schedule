import React, { useState, useMemo, useEffect } from 'react';
import { CompactPaperSchedule } from './CompactPaperSchedule';
import { AcademicGridSchedule } from './AcademicGridSchedule';
import { ScheduledLessonDto, EducatorDto, GroupDto, AuditoriumDto } from '../../../types/api';
import { ConstraintsService, ResourceService } from '../../../services/apiServices';
import { Search, LayoutGrid, FileText, Users, UserSquare2, School, Loader2 } from 'lucide-react';
import { cn } from '../../../utils/cn';

interface ScheduleManagerProps {
  lessons: ScheduledLessonDto[];
  startDate: Date;
  endDate: Date;
}

type FilterType = 'group' | 'educator' | 'auditorium';
type ViewType = 'paper' | 'academic';

export const ScheduleManager: React.FC<ScheduleManagerProps> = ({ lessons, startDate, endDate }) => {
  const [filterType, setFilterType] = useState<FilterType>('group');
  const [viewType, setViewType] = useState<ViewType>('academic');
  const [selectedValue, setSelectedValue] = useState<string>('');
  const [constraints, setConstraints] = useState<any[]>([]);
  const [loadingConstraints, setLoadingConstraints] = useState(false);

  // Хранилище всех ресурсов для поиска ID по имени
  const [allResources, setAllResources] = useState<{
    groups: GroupDto[],
    educators: EducatorDto[],
    auditoriums: AuditoriumDto[]
  }>({ groups: [], educators: [], auditoriums: [] });

  useEffect(() => {
    Promise.all([
      ResourceService.getGroups(),
      ResourceService.getEducators(),
      ResourceService.getAuditoriums()
    ]).then(([groups, educators, auditoriums]) => {
      setAllResources({ groups, educators, auditoriums });
    });
  }, []);

  const options = useMemo(() => {
    const groups = new Set<string>();
    const educators = new Set<string>();
    const auditoriums = new Set<string>();

    lessons.forEach((l) => {
      l.groupNames.forEach((g) => groups.add(g));
      l.educatorNames.forEach((e) => educators.add(e));
      l.auditoriumNames.forEach((a) => auditoriums.add(a));
    });

    return {
      group: Array.from(groups).sort(),
      educator: Array.from(educators).sort(),
      auditorium: Array.from(auditoriums).sort(),
    };
  }, [lessons]);

  // Загрузка реальных ограничений при смене выбора
  useEffect(() => {
    if (!selectedValue) {
      setConstraints([]);
      return;
    }

    setLoadingConstraints(true);
    let promise;

    if (filterType === 'group') {
      const id = allResources.groups.find(g => g.name === selectedValue)?.id;
      promise = id ? ConstraintsService.getGroupConstraintsByGroup(id) : Promise.resolve([]);
    } else if (filterType === 'educator') {
      const id = allResources.educators.find(e => e.name === selectedValue)?.id;
      promise = id ? ConstraintsService.getEducatorConstraintsByEducator(id) : Promise.resolve([]);
    } else {
      const id = allResources.auditoriums.find(a => a.name === selectedValue)?.id;
      promise = id ? ConstraintsService.getAuditoriumConstraintsByAuditorium(id) : Promise.resolve([]);
    }

    promise.then(data => {
      setConstraints(data);
      setLoadingConstraints(false);
    }).catch(() => setLoadingConstraints(false));

  }, [selectedValue, filterType, allResources]);

  return (
      <div className="space-y-4">
        <div className="bg-white border border-slate-100 rounded-xl p-1.5 shadow-sm flex flex-col lg:flex-row items-center gap-3">
          <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0">
            <FilterBtn active={filterType === 'group'} onClick={() => { setFilterType('group'); setSelectedValue(''); }} icon={Users} label="Группы" />
            <FilterBtn active={filterType === 'educator'} onClick={() => { setFilterType('educator'); setSelectedValue(''); }} icon={UserSquare2} label="Преподы" />
            <FilterBtn active={filterType === 'auditorium'} onClick={() => { setFilterType('auditorium'); setSelectedValue(''); }} icon={School} label="Ауд." />
          </div>

          <div className="flex bg-slate-100 rounded-lg p-0.5 shrink-0">
            <button
                onClick={() => setViewType('academic')}
                className={cn('p-1.5 rounded-md transition-all', viewType === 'academic' ? 'bg-white shadow-sm text-blue-600' : 'text-slate-500 hover:text-slate-700')}
            >
              <LayoutGrid size={16} />
            </button>
            <button
                onClick={() => setViewType('paper')}
                className={cn('p-1.5 rounded-md transition-all', viewType === 'paper' ? 'bg-white shadow-sm text-blue-600' : 'text-slate-500 hover:text-slate-700')}
            >
              <FileText size={16} />
            </button>
          </div>

          <div className="relative flex-1 w-full">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <select
                value={selectedValue}
                onChange={(e) => setSelectedValue(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-bold text-slate-700 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
            >
              <option value="">Выберите объект...</option>
              {options[filterType].map((opt) => (
                  <option key={opt} value={opt}>{opt}</option>
              ))}
            </select>
          </div>

          <div className="px-3 py-1 bg-blue-50 rounded-lg border border-blue-100 flex items-center gap-2 shrink-0 min-w-[120px]">
            {loadingConstraints ? <Loader2 size={12} className="animate-spin text-blue-600" /> : <span className="text-[9px] text-blue-600 font-black uppercase tracking-tighter">Ресурс:</span>}
            <span className="text-xs font-black text-blue-900 truncate max-w-[100px]">{selectedValue || '—'}</span>
          </div>
        </div>

        {selectedValue && (
            viewType === 'academic' ? (
                <AcademicGridSchedule lessons={filteredLessons(lessons, filterType, selectedValue)} startDate={startDate} endDate={endDate} constraints={constraints} />
            ) : (
                <CompactPaperSchedule lessons={filteredLessons(lessons, filterType, selectedValue)} startDate={startDate} endDate={endDate} constraints={constraints} />
            )
        )}
      </div>
  );
};

const filteredLessons = (lessons: ScheduledLessonDto[], type: FilterType, value: string) => {
  return lessons.filter((l) => {
    if (type === 'group') return l.groupNames.includes(value);
    if (type === 'educator') return l.educatorNames.includes(value);
    if (type === 'auditorium') return l.auditoriumNames.includes(value);
    return false;
  });
};

const FilterBtn = ({ active, onClick, icon: Icon, label }: any) => (
    <button
        onClick={onClick}
        className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-[10px] font-black uppercase tracking-tight transition-all ${
            active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:bg-white/50'
        }`}
    >
      <Icon size={12} />
      {label}
    </button>
);
