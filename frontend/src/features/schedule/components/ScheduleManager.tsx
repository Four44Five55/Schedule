import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { parseISO } from 'date-fns';
import { AcademicGridSchedule } from './AcademicGridSchedule';
import { ScheduledLessonDto, EducatorDto, GroupDto, AuditoriumDto } from '../../../types/api';
import { ResourceService, ScheduleService } from '../../../services/apiServices';
import { useEntityConstraints } from '../../constraints/useEntityConstraints';
import { usePeriod } from '../../period/PeriodContext';
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

interface ScheduleManagerProps {
  currentSession?: ScheduleSessionDto | null;
  // Расширенный функционал Фичи 2 (пины): тумблер замка + «перегенерировать,
  // сохранив закреплённые». Включается только в планировщике; раздел «Расписание»
  // остаётся со старым функционалом (просмотр + перенос).
  pinningEnabled?: boolean;
}

type FilterType = 'group' | 'educator' | 'auditorium';

export const ScheduleManager: React.FC<ScheduleManagerProps> = ({ currentSession: sessionProp, pinningEnabled = false }) => {
  // Учебный период — из общего контекста (единый выбор в шапке). Раздел сам грузит
  // занятия этого периода; свой селектор периода убран.
  const { selectedPeriod } = usePeriod();

  const [lessons, setLessons] = useState<ScheduledLessonDto[]>([]);
  const [grid, setGrid] = useState<Record<string, ScheduledLessonDto[]>>({});
  const [loadingPeriodSchedule, setLoadingPeriodSchedule] = useState(false);

  // Тип фильтра и выбранный объект переживают обновление страницы (localStorage),
  // иначе F5 сбрасывает открытое расписание и его приходится выбирать заново.
  const [filterType, setFilterType] = useState<FilterType>(() => {
    const saved = localStorage.getItem('unischedule.schedule.filterType');
    return saved === 'group' || saved === 'educator' || saved === 'auditorium' ? saved : 'group';
  });
  const [selectedValue, setSelectedValue] = useState<string>(
    () => localStorage.getItem('unischedule.schedule.selectedValue') || ''
  );

  useEffect(() => {
    localStorage.setItem('unischedule.schedule.filterType', filterType);
  }, [filterType]);

  useEffect(() => {
    localStorage.setItem('unischedule.schedule.selectedValue', selectedValue);
  }, [selectedValue]);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentSession, setCurrentSession] = useState<ScheduleSessionDto | null>(sessionProp || null);
  const [loadingAction, setLoadingAction] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const [allResources, setAllResources] = useState<{
    groups: GroupDto[],
    educators: EducatorDto[],
    auditoriums: AuditoriumDto[]
  }>({ groups: [], educators: [], auditoriums: [] });

  useEffect(() => {
    Promise.all([
      ResourceService.getGroups(),
      ResourceService.getEducators(),
      ResourceService.getAuditoriums(),
    ]).then(([groups, educators, auditoriums]) => {
      setAllResources({ groups, educators, auditoriums });
    });
  }, []);

  // Загрузка расписания текущего периода (Query Side). Перезагружается при смене
  // общего периода и вызывается вручную после переноса/пина/перегенерации.
  const reloadPeriodSchedule = useCallback(async () => {
    if (!selectedPeriod) { setLessons([]); setGrid({}); return; }
    try {
      const result = await ScheduleService.loadExisting(selectedPeriod.startDate, selectedPeriod.endDate);
      if (result.status === 'loaded') {
        setLessons(result.lessons);
        setGrid(result.grid || {});
      } else {
        setLessons([]);
        setGrid({});
      }
    } catch (err) {
      console.error('Не удалось загрузить расписание:', err);
    }
  }, [selectedPeriod]);

  useEffect(() => {
    setLoadingPeriodSchedule(true);
    reloadPeriodSchedule().finally(() => setLoadingPeriodSchedule(false));
  }, [reloadPeriodSchedule]);

  // Синхронизируем currentSession с пропом (только если проп задан —
  // иначе не затираем сессию, подтянутую при открытии расписания).
  useEffect(() => {
    if (sessionProp) setCurrentSession(sessionProp);
  }, [sessionProp]);

  // При открытии расписания подтягиваем сессию «живого» расписания (и при
  // необходимости переоткрываем её), чтобы редактирование было доступно сразу.
  useEffect(() => {
    if (sessionProp) return;
    CQRSService.getEditableSession().then((s) => {
      if (s) setCurrentSession(s);
    });
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

  // Корневая сущность для подбора вариантов переноса — та, через которую
  // открыто расписание (выбранный фильтр). Первой проверяется именно она.
  const rootEntityType: 'GROUP' | 'EDUCATOR' | 'AUDITORIUM' =
      filterType === 'group' ? 'GROUP' : filterType === 'educator' ? 'EDUCATOR' : 'AUDITORIUM';

  const rootEntityId = useMemo(() => {
    if (!selectedValue) return undefined;
    const list = filterType === 'group' ? allResources.groups
        : filterType === 'educator' ? allResources.educators
            : allResources.auditoriums;
    return list.find(r => r.name === selectedValue)?.id;
  }, [filterType, selectedValue, allResources]);

  // Ограничения выбранной сущности — общий хук (тот же, что в планировщике).
  const { constraints, loading: loadingConstraints } = useEntityConstraints(filterType, rootEntityId);

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
    setActionMessage('✅ Занятие перенесено!');
    // Перезагружаем расписание за текущий период, чтобы перенос отразился сразу.
    // Query Side обновляется асинхронно, но диалог переноса уже выждал перед вызовом.
    await reloadPeriodSchedule();
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

  // Закрепить/открепить занятие (пин). Проекция во view асинхронна — выждем и перезагрузим.
  const handleToggleLock = async (lesson: ScheduledLessonDto) => {
    if (!lesson.placementId) return;
    try {
      await CQRSService.setLock(lesson.placementId, !lesson.locked);
      setActionMessage(lesson.locked ? 'Откреплено' : '🔒 Закреплено');
      setTimeout(() => { reloadPeriodSchedule(); setActionMessage(null); }, 700);
    } catch (e) {
      console.error('Ошибка закрепления:', e);
      setActionMessage('❌ Ошибка закрепления');
      setTimeout(() => setActionMessage(null), 3000);
    }
  };

  // Перегенерация с сохранением закреплённых занятий (Фаза A).
  const handleRegenerate = async () => {
    if (!currentSession || !selectedPeriod) return;
    setLoadingAction(true);
    setActionMessage('Перегенерация (сохранив закреплённые)...');
    try {
      const updated = await CQRSService.regenerateKeepingLocked(currentSession.id, {
        name: currentSession.name,
        studyPeriodId: selectedPeriod.id,
        courseIds: [],
      });
      setCurrentSession(updated);
      setTimeout(async () => {
        await reloadPeriodSchedule();
        setActionMessage('✅ Перегенерировано (закреплённые на местах)');
        setTimeout(() => setActionMessage(null), 3000);
      }, 1200);
    } catch (e) {
      console.error('Ошибка перегенерации:', e);
      setActionMessage('❌ Ошибка перегенерации');
      setTimeout(() => setActionMessage(null), 3000);
    } finally {
      setLoadingAction(false);
    }
  };

  return (
      <div className="space-y-4">
        {/* Единая липкая панель управления: фильтры · статус сессии + кнопки.
            Период выбирается глобально в шапке приложения, поэтому здесь его нет. */}
        <div className="sticky top-0 z-30 bg-white/95 backdrop-blur border border-slate-100 rounded-xl px-2 py-1.5 shadow-sm flex flex-wrap items-center gap-2">

          {loadingPeriodSchedule && (
              <span className="flex items-center gap-1 text-[10px] text-blue-600 shrink-0">
                <Loader2 size={12} className="animate-spin" /> период…
              </span>
          )}

          {/* Тип ресурса */}
          <div className="flex bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0">
            <FilterBtn active={filterType === 'group'} onClick={() => { setFilterType('group'); setSelectedValue(''); }} icon={Users} label="Группы" />
            <FilterBtn active={filterType === 'educator'} onClick={() => { setFilterType('educator'); setSelectedValue(''); }} icon={UserSquare2} label="Преподы" />
            <FilterBtn active={filterType === 'auditorium'} onClick={() => { setFilterType('auditorium'); setSelectedValue(''); }} icon={School} label="Ауд." />
          </div>

          {/* Объект: фиксированной ширины (хватает и на длинную фамилию), не на всю строку */}
          <div className="relative w-full sm:w-64 shrink-0">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <select
                value={selectedValue}
                onChange={(e) => setSelectedValue(e.target.value)}
                className="w-full pl-9 pr-8 py-1 bg-slate-50 border border-slate-200 rounded-lg text-xs font-bold text-slate-700 outline-none focus:ring-1 focus:ring-blue-500 appearance-none cursor-pointer"
            >
              <option value="">Выберите объект...</option>
              {options[filterType].map((opt) => (
                  <option key={opt} value={opt}>{opt}</option>
              ))}
            </select>
            {loadingConstraints && <Loader2 size={12} className="animate-spin text-blue-600 absolute right-3 top-1/2 -translate-y-1/2" />}
          </div>

          {/* Статус сессии + действия — в том же баре, прижаты вправо */}
          {currentSession && (
              <div className="flex items-center gap-2 shrink-0 ml-auto">
                <span className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-tighter ${
                    currentSession.status === 'READY_FOR_EDIT'
                        ? 'bg-green-100 text-green-700'
                        : currentSession.status === 'GENERATING'
                            ? 'bg-yellow-100 text-yellow-700'
                            : 'bg-slate-100 text-slate-700'
                }`}>
                  {currentSession.status === 'READY_FOR_EDIT' ? 'Готово' :
                      currentSession.status === 'GENERATING' ? 'Генерация…' :
                          currentSession.status}
                </span>
                <span className="text-[10px] text-slate-500">v<span className="font-black text-slate-900">{currentSession.version}</span></span>

                <button
                    onClick={handleReloadSession}
                    disabled={loadingAction}
                    className="p-1.5 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors disabled:opacity-50"
                    title="Обновить"
                >
                  <RefreshCw size={14} className={loadingAction ? 'animate-spin' : ''} />
                </button>

                {currentSession.status === 'INITIALIZED' && (
                    <button
                        onClick={handleGenerateSchedule}
                        disabled={loadingAction}
                        className="px-3 py-1.5 bg-blue-600 text-white text-xs font-black rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center gap-1.5"
                    >
                      {loadingAction ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                      Сгенерировать
                    </button>
                )}

                {currentSession.status === 'READY_FOR_EDIT' && (
                    <button
                        onClick={() => setIsEditMode(!isEditMode)}
                        className={`px-3 py-1.5 text-xs font-black rounded-lg transition-colors flex items-center gap-1.5 ${
                            isEditMode
                                ? 'bg-red-100 text-red-700 hover:bg-red-200'
                                : 'bg-blue-600 text-white hover:bg-blue-700'
                        }`}
                    >
                      {isEditMode ? <><CheckCircle size={14} /> Выход</> : <><Settings size={14} /> Редактировать</>}
                    </button>
                )}

                {pinningEnabled && currentSession.status === 'READY_FOR_EDIT' && (
                    <button
                        onClick={handleRegenerate}
                        disabled={loadingAction || !selectedPeriod}
                        title={!selectedPeriod
                            ? 'Выберите учебный период'
                            : 'Перераспределить незакреплённые занятия, сохранив закреплённые на местах'}
                        className="px-3 py-1.5 bg-amber-500 text-white text-xs font-black rounded-lg hover:bg-amber-600 disabled:opacity-50 transition-colors flex items-center gap-1.5"
                    >
                      {loadingAction ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
                      Перегенерировать
                    </button>
                )}
              </div>
          )}
        </div>

        {!selectedPeriod ? (
            <div className="bg-white border border-slate-100 rounded-xl p-8 shadow-sm text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
                  <Calendar className="text-slate-400" size={24} />
                </div>
                <p className="text-sm font-bold text-slate-900">Выберите учебный период в шапке</p>
              </div>
            </div>
        ) : selectedValue ? (
            <AcademicGridSchedule
                lessons={lessons}
                grid={grid}
                filterType={filterType}
                selectedValue={selectedValue}
                startDate={parseISO(selectedPeriod.startDate)}
                endDate={parseISO(selectedPeriod.endDate)}
                constraints={constraints}
                isEditMode={isEditMode}
                sessionId={currentSession?.id}
                currentVersion={currentSession?.version || 0}
                rootEntityType={rootEntityType}
                rootEntityId={rootEntityId}
                onMoveLesson={handleMoveLesson}
                onToggleLock={pinningEnabled ? handleToggleLock : undefined}
                chromeless
                maxHeightClass="max-h-[calc(100vh_-_150px)]"
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

        {/* Тост-уведомления вынесены из потока (fixed), чтобы появление/исчезновение
            сообщения не двигало сетку вверх-вниз. */}
        {actionMessage && (
            <div className={`fixed bottom-6 right-6 z-50 px-4 py-2.5 rounded-xl shadow-lg text-xs font-bold border ${
                actionMessage.includes('✅') ? 'bg-green-600 text-white border-green-500'
                    : actionMessage.includes('❌') ? 'bg-red-600 text-white border-red-500'
                        : 'bg-slate-900 text-white border-slate-700'
            }`}>
              {actionMessage}
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
