import React, { useState, useMemo, useEffect } from 'react';
import { AcademicGridSchedule } from './AcademicGridSchedule';
import { ScheduledLessonDto, EducatorDto, GroupDto, AuditoriumDto, StudyPeriodDto } from '../../../types/api';
import { ConstraintsService, ResourceService, ScheduleService } from '../../../services/apiServices';
import { CQRSService } from '../../../services/cqrsApiService';
import {
  ScheduleSessionDto
} from '../../../types/cqrs';
import {
  Search,
  Users,
  UserSquare2,
  School,
  Loader2,
  Settings,
  Save,
  CheckCircle,
  RefreshCw,
  Calendar
} from 'lucide-react';
import { cn } from '../../../utils/cn';

interface ScheduleManagerProps {
  lessons: ScheduledLessonDto[];
  grid: Record<string, ScheduledLessonDto[]>;
  startDate: Date;
  endDate: Date;
  onLessonChange?: (lessons: ScheduledLessonDto[], grid: Record<string, ScheduledLessonDto[]>) => void;
  currentSession?: ScheduleSessionDto | null;
}

type FilterType = 'group' | 'educator' | 'auditorium';

export const ScheduleManager: React.FC<ScheduleManagerProps> = ({ lessons, grid = {}, startDate, endDate, onLessonChange, currentSession: sessionProp }) => {
  const [filterType, setFilterType] = useState<FilterType>('group');
  const [selectedValue, setSelectedValue] = useState<string>('');
  const [constraints, setConstraints] = useState<any[]>([]);
  const [loadingConstraints, setLoadingConstraints] = useState(false);

  const [isEditMode, setIsEditMode] = useState(false);
  const [currentSession, setCurrentSession] = useState<ScheduleSessionDto | null>(sessionProp || null);
  const [loadingAction, setLoadingAction] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const [allResources, setAllResources] = useState<{
    groups: GroupDto[],
    educators: EducatorDto[],
    auditoriums: AuditoriumDto[]
  }>({ groups: [], educators: [], auditoriums: [] });

  const [studyPeriods, setStudyPeriods] = useState<StudyPeriodDto[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<StudyPeriodDto | null>(null);
  const [loadingPeriodSchedule, setLoadingPeriodSchedule] = useState(false);

  useEffect(() => {
    Promise.all([
      ResourceService.getGroups(),
      ResourceService.getEducators(),
      ResourceService.getAuditoriums(),
      ResourceService.getStudyPeriods(),
      ResourceService.getActiveStudyPeriod()
    ]).then(([groups, educators, auditoriums, periods, activePeriod]) => {
      setAllResources({ groups, educators, auditoriums });
      setStudyPeriods(periods);
      setSelectedPeriod(activePeriod);
    });
  }, []);

  // Синхронизируем currentSession с пропом
  useEffect(() => {
    setCurrentSession(sessionProp || null);
  }, [sessionProp]);

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

  const handleGenerateSchedule = async () => {
    if (!currentSession) return;
    setLoadingAction(true);
    setActionMessage('Генерация расписания...');
    try {
      const updated = await CQRSService.getSession(currentSession.id);
      setCurrentSession(updated);
      setIsEditMode(true);
      setActionMessage('✅ Расписание готово!');
      setTimeout(() => setActionMessage(null), 3000);
    } finally {
      setLoadingAction(false);
    }
  };

  const handleMoveLesson = async (_placementId: string) => {
    if (!currentSession) return;
    setActionMessage('✅ Занятие перенесено!');
    setTimeout(() => setActionMessage(null), 2000);
  };

  const handleReloadSession = async () => {
    if (!currentSession) return;
    setLoadingAction(true);
    try {
      const reloaded = await CQRSService.getSession(currentSession.id);
      setCurrentSession(reloaded);
    } finally {
      setLoadingAction(false);
    }
  };

  const handlePeriodChange = async (period: StudyPeriodDto | null) => {
    if (!period) return;

    setSelectedPeriod(period);
    setLoadingPeriodSchedule(true);
    setActionMessage('Загрузка расписания за период...');

    try {
      const result = await ScheduleService.loadExisting(period.startDate, period.endDate);
      if (result.status === 'loaded' && result.lessons.length > 0) {
        console.log('✅ Загружено расписание за период:', period.name, '-', result.lessons.length, 'занятий');
        onLessonChange?.(result.lessons, result.grid || {});
        setActionMessage(`✅ Загружено ${result.lessons.length} занятий`);
      } else {
        console.log('ℹ️ Нет расписания за выбранный период');
        onLessonChange?.([], {});
        setActionMessage('ℹ️ Нет расписания за этот период');
      }
    } catch (err) {
      console.error('Ошибка загрузки расписания:', err);
      setActionMessage('❌ Ошибка загрузки');
    } finally {
      setLoadingPeriodSchedule(false);
      setTimeout(() => setActionMessage(null), 3000);
    }
  };

  return (
      <div className="space-y-4">
        {/* Селект периода и фильтры */}
        <div className="bg-white border border-slate-100 rounded-xl p-1.5 shadow-sm space-y-3">

          {/* Выбор учебного периода */}
          {studyPeriods.length > 0 && (
              <div className="flex items-center gap-2 px-2">
                <Calendar className="text-slate-400" size={14} />
                <select
                    value={selectedPeriod?.id || ''}
                    onChange={(e) => {
                      const period = studyPeriods.find(p => p.id === parseInt(e.target.value));
                      handlePeriodChange(period || null);
                    }}
                    className="flex-1 px-3 py-1.5 bg-blue-50 border border-blue-100 rounded-lg text-xs font-bold text-blue-900 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
                >
                  <option value="">Выберите период...</option>
                  {studyPeriods.map((period) => (
                      <option key={period.id} value={period.id}>
                        {period.name} ({period.startDate} — {period.endDate})
                      </option>
                  ))}
                </select>
                {loadingPeriodSchedule && <Loader2 size={14} className="animate-spin text-blue-600" />}
              </div>
          )}

          {/* Фильтры по типу ресурса */}
          <div className="flex flex-col lg:flex-row items-center gap-3">
            <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0">
              <FilterBtn active={filterType === 'group'} onClick={() => { setFilterType('group'); setSelectedValue(''); }} icon={Users} label="Группы" />
              <FilterBtn active={filterType === 'educator'} onClick={() => { setFilterType('educator'); setSelectedValue(''); }} icon={UserSquare2} label="Преподы" />
              <FilterBtn active={filterType === 'auditorium'} onClick={() => { setFilterType('auditorium'); setSelectedValue(''); }} icon={School} label="Ауд." />
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
        </div>

        {currentSession && (
            <div className="bg-white border border-slate-100 rounded-xl p-4 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-tighter ${
                      currentSession.status === 'READY_FOR_EDIT'
                          ? 'bg-green-100 text-green-700'
                          : currentSession.status === 'GENERATING'
                              ? 'bg-yellow-100 text-yellow-700'
                              : 'bg-slate-100 text-slate-700'
                  }`}>
                    {currentSession.status === 'READY_FOR_EDIT' ? 'Готово к редактированию' :
                        currentSession.status === 'GENERATING' ? 'Генерация...' :
                            currentSession.status}
                  </span>
                    <span className="text-xs text-slate-500">
                    v<span className="font-black text-slate-900">{currentSession.version}</span>
                  </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                      onClick={handleReloadSession}
                      disabled={loadingAction}
                      className="p-2 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors disabled:opacity-50"
                      title="Обновить"
                  >
                    <RefreshCw size={14} className={loadingAction ? 'animate-spin' : ''} />
                  </button>

                  {currentSession.status === 'READY_FOR_EDIT' && (
                      <button
                          onClick={() => setIsEditMode(!isEditMode)}
                          className={`px-3 py-1.5 text-xs font-black rounded-lg transition-colors flex items-center gap-2 ${
                              isEditMode
                                  ? 'bg-red-100 text-red-700 hover:bg-red-200'
                                  : 'bg-blue-600 text-white hover:bg-blue-700'
                          }`}
                      >
                        {isEditMode ? <><CheckCircle size={14} /> Выход</> : <><Settings size={14} /> Редактировать</>}
                      </button>
                  )}
                </div>
              </div>

              {actionMessage && (
                  <div className={`px-3 py-2 rounded-lg text-xs font-medium ${
                      actionMessage.includes('✅') ? 'bg-green-100 text-green-800' : 'bg-blue-100 text-blue-800'
                  }`}>
                    {actionMessage}
                  </div>
              )}

              {(currentSession.status === 'INITIALIZED') && (
                  <button
                      onClick={handleGenerateSchedule}
                      disabled={loadingAction}
                      className="w-full px-4 py-2 bg-blue-600 text-white text-sm font-black rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                  >
                    {loadingAction ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                    Сгенерировать расписание
                  </button>
              )}
            </div>
        )}

        {selectedValue ? (
            <AcademicGridSchedule
                lessons={lessons}
                grid={grid}
                filterType={filterType}
                selectedValue={selectedValue}
                startDate={startDate}
                endDate={endDate}
                constraints={constraints}
                isEditMode={isEditMode}
                sessionId={currentSession?.id}
                currentVersion={currentSession?.version || 0}
                onMoveLesson={handleMoveLesson}
            />
        ) : (
            <div className="bg-white border border-slate-100 rounded-xl p-8 shadow-sm text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
                  <Search className="text-slate-400" size={24} />
                </div>
                <div className="space-y-1">
                  <p className="text-sm font-bold text-slate-900">Выберите объект для отображения расписания</p>
                  <p className="text-xs text-slate-500">Выберите группу, преподавателя или аудиторию из списка выше</p>
                </div>
              </div>
            </div>
        )}
      </div>
  );
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
