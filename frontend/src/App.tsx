import { useState, useCallback, useEffect } from 'react';
import { Sidebar, TAB_IDS, type TabId } from './components/layout/Sidebar';
import { Dashboard } from './features/dashboard/components/Dashboard';
import { EducatorList } from './features/resources/components/EducatorList';
import { AuditoriumGrid } from './features/resources/components/AuditoriumGrid';
import { GroupList } from './features/resources/components/GroupList';
import { DisciplineList } from './features/curriculum/components/DisciplineList';
import { StudyStreamList } from './features/resources/components/StudyStreamList';
import { ConstraintsManager } from './features/constraints/components/ConstraintsManager';
import { PlannerManager } from './features/planner/components/PlannerManager';
import { ScheduleManager } from './features/schedule/components/ScheduleManager';
import type { ScheduledLessonDto, GroupDto, EducatorDto, AuditoriumDto, StudyStreamDto, DisciplineDto, ScheduleResultDto, StudyPeriodDto } from './types/api';
import { ScheduleService, ResourceService, CurriculumService } from './services/apiServices';
import { CQRSService, dateUtils } from './services/cqrsApiService';
import { Bell, Search, HelpCircle, CalendarRange, ChevronRight } from 'lucide-react';

const ACTIVE_TAB_STORAGE_KEY = 'unischedule.activeTab';

export default function App() {
  // Активная вкладка переживает обновление страницы: храним её в localStorage,
  // а не только в state (иначе F5 сбрасывает на дашборд). Восстановленное значение
  // валидируем по списку вкладок, чтобы не открыть несуществующий раздел.
  const [activeTab, setActiveTab] = useState<TabId>(() => {
    const saved = localStorage.getItem(ACTIVE_TAB_STORAGE_KEY) as TabId | null;
    return saved && TAB_IDS.includes(saved) ? saved : 'dashboard';
  });

  useEffect(() => {
    localStorage.setItem(ACTIVE_TAB_STORAGE_KEY, activeTab);
  }, [activeTab]);

  // ========== Загрузка ресурсов ==========
  const [loading, setLoading] = useState(true);
  const [educators, setEducators] = useState<EducatorDto[]>([]);
  const [auditoriums, setAuditoriums] = useState<AuditoriumDto[]>([]);
  const [groups, setGroups] = useState<GroupDto[]>([]);
  const [disciplines, setDisciplines] = useState<DisciplineDto[]>([]);
  const [streams, setStreams] = useState<StudyStreamDto[]>([]);

  // Начальная загрузка всех ресурсов
  useEffect(() => {
    Promise.all([
      ResourceService.getEducators(),
      ResourceService.getAuditoriums(),
      ResourceService.getGroups(),
      CurriculumService.getDisciplines(),
      ResourceService.getStreams(),
    ])
        .then(([edu, aud, grp, disc, str]) => {
          setEducators(edu);
          setAuditoriums(aud);
          setGroups(grp);
          setDisciplines(disc);
          setStreams(str);
        })
        .catch((err) => console.error('Ошибка загрузки данных:', err))
        .finally(() => setLoading(false));
  }, []);

  // ========== Загрузка существующего расписания при старте ==========
  const [scheduleLessons, setScheduleLessons] = useState<ScheduledLessonDto[]>([]);
  const [scheduleGrid, setScheduleGrid] = useState<Record<string, ScheduledLessonDto[]>>({});
  const [schedulePeriod, setSchedulePeriod] = useState<StudyPeriodDto | null>(null);

  useEffect(() => {
    ResourceService.getActiveStudyPeriod()
        .then((activePeriod) => {
          if (activePeriod) {
            console.log('✅ Активный период:', activePeriod.name, '(', activePeriod.startDate, '—', activePeriod.endDate, ')');
            setSchedulePeriod(activePeriod);
            return ScheduleService.loadExisting(activePeriod.startDate, activePeriod.endDate);
          } else {
            console.log('ℹ️ Нет активного учебного периода');
            return Promise.resolve({
              status: 'empty',
              lessons: [],
              grid: {},
              placedCount: 0,
              unplacedCount: 0,
              startDate: new Date().toISOString().split('T')[0],
              endDate: new Date().toISOString().split('T')[0],
              totalSlots: 0,
              usedSlots: 0
            });
          }
        })
        .then((result: ScheduleResultDto) => {
          if (result.status === 'loaded' && result.lessons.length > 0) {
            console.log('✅ Загружено существующее расписание:', result.lessons.length, 'занятий');
            setScheduleLessons(result.lessons);
            setScheduleGrid(result.grid || {});
          } else {
            console.log('ℹ️ Нет существующего расписания, нужна генерация');
          }
        })
        .catch((err) => {
          console.error('Ошибка загрузки расписания:', err);
        });
  }, []);

  // ========== CRUD перезагрузки ==========
  const reloadGroups = useCallback(async () => {
    try {
      const data = await ResourceService.getGroups();
      setGroups(data);
    } catch (err) {
      console.error('Ошибка загрузки групп:', err);
    }
  }, []);

  const reloadEducators = useCallback(async () => {
    try {
      const data = await ResourceService.getEducators();
      setEducators(data);
    } catch (err) {
      console.error('Ошибка загрузки преподавателей:', err);
    }
  }, []);

  const reloadAuditoriums = useCallback(async () => {
    try {
      const data = await ResourceService.getAuditoriums();
      setAuditoriums(data);
    } catch (err) {
      console.error('Ошибка загрузки аудиторий:', err);
    }
  }, []);

  const reloadDisciplines = useCallback(async () => {
    try {
      const data = await CurriculumService.getDisciplines();
      setDisciplines(data);
    } catch (err) {
      console.error('Ошибка загрузки дисциплин:', err);
    }
  }, []);

  const reloadStreams = useCallback(async () => {
    try {
      const data = await ResourceService.getStreams();
      setStreams(data);
    } catch (err) {
      console.error('Ошибка загрузки потоков:', err);
    }
  }, []);

  // ========== Расписание ==========
  const [isGenerating, setIsGenerating] = useState(false);

  const handleGenerateSchedule = async (courseIds: number[], period?: StudyPeriodDto) => {
    setIsGenerating(true);
    try {
      // Период обязателен для генерации. Планировщик передаёт выбранный явно;
      // прочие вызовы (например, дашборд) fallback'ятся на активный период.
      const targetPeriod = period ?? await ResourceService.getActiveStudyPeriod();
      if (!targetPeriod) {
        alert('Не выбран учебный период. Создайте/выберите период в планировщике.');
        return;
      }

      await CQRSService.generateSchedule({
        name: 'Генерация от ' + new Date().toLocaleString('ru-RU'),
        studyPeriodId: targetPeriod.id,
        courseIds: courseIds
      });

      // Показываем именно сгенерированный период (а не «активный на сегодня»),
      // чтобы можно было готовить будущий семестр.
      setSchedulePeriod(targetPeriod);
      const result = await ScheduleService.loadExisting(targetPeriod.startDate, targetPeriod.endDate);
      setScheduleLessons(result.lessons);
      setScheduleGrid(result.grid || {});

      setActiveTab('schedule');
    } catch (err) {
      console.error(err);
      alert('Ошибка при генерации расписания');
    } finally {
      setIsGenerating(false);
    }
  };

  // ========== Рендер контента ==========
  const renderContent = () => {
    if (loading) {
      return (
          <div className="flex flex-col items-center justify-center h-[calc(100vh-200px)] gap-6">
            <div className="relative">
              <div className="w-16 h-16 border-4 border-blue-100 border-t-blue-600 rounded-full animate-spin" />
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="w-8 h-8 bg-blue-50 rounded-full" />
              </div>
            </div>
            <div className="text-center space-y-2">
              <p className="font-black text-slate-800 text-lg uppercase tracking-widest">Синхронизация</p>
              <p className="text-slate-400 text-sm font-medium">Подключаемся к университетской базе данных...</p>
            </div>
          </div>
      );
    }

    switch (activeTab) {
      case 'dashboard':
        return (
            <Dashboard
                stats={{
                  educators: educators.length,
                  auditoriums: auditoriums.length,
                  groups: groups.length,
                  disciplines: disciplines.length,
                }}
                onGenerate={handleGenerateSchedule}
                isGenerating={isGenerating}
            />
        );
      case 'educators':
        return <EducatorList educators={educators} onEducatorsChange={reloadEducators} />;
      case 'auditoriums':
        return <AuditoriumGrid auditoriums={auditoriums} onAuditoriumsChange={reloadAuditoriums} />;
      case 'groups':
        return <GroupList groups={groups} onGroupsChange={reloadGroups} />;
      case 'disciplines':
        return <DisciplineList disciplines={disciplines} onRefresh={reloadDisciplines} />;
      case 'streams':
        return <StudyStreamList streams={streams} onStreamsChange={reloadStreams} />;
      case 'constraints':
        return <ConstraintsManager />;
      case 'planner':
        return (
            <PlannerManager
                disciplines={disciplines}
                educators={educators}
                groups={groups}
                onGenerate={handleGenerateSchedule}
                isGenerating={isGenerating}
            />
        );
      case 'curriculum':
        return (
            <div className="flex flex-col items-center justify-center h-96 gap-4 border-2 border-dashed border-slate-200 rounded-2xl bg-white">
              <div className="w-14 h-14 bg-amber-50 rounded-2xl flex items-center justify-center">
                <HelpCircle size={28} className="text-amber-400" />
              </div>
              <div className="text-center">
                <p className="font-black text-slate-700 text-lg">В разработке</p>
                <p className="text-slate-400 text-sm mt-1">Раздел «Учебный план» временно недоступен</p>
                <p className="text-slate-300 text-xs mt-2">Управление занятиями перенесено в раздел «Дисциплины»</p>
              </div>
            </div>
        );
      case 'schedule':
        return (
            <ScheduleManager
                lessons={scheduleLessons}
                grid={scheduleGrid}
                startDate={schedulePeriod ? dateUtils.parseDate(schedulePeriod.startDate) : new Date(2026, 1, 9)}
                endDate={schedulePeriod ? dateUtils.parseDate(schedulePeriod.endDate) : new Date(2026, 7, 31)}
                onLessonChange={(lessons, grid) => {
                  setScheduleLessons(lessons);
                  setScheduleGrid(grid);
                }}
            />
        );
      default:
        return (
            <div className="flex flex-col items-center justify-center h-96 text-slate-300">
              <HelpCircle size={48} className="mb-4 opacity-10" />
              <p className="text-lg font-bold">Модуль "{activeTab}" не найден</p>
            </div>
        );
    }
  };

  return (
      <div className="flex min-h-screen bg-slate-50 selection:bg-blue-100 selection:text-blue-900">
        <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />

        <main className="flex-1 flex flex-col min-w-0">
          <header className="h-12 bg-white/80 backdrop-blur-xl border-b border-slate-100 sticky top-0 z-40 px-6 flex items-center justify-between">
            <div className="flex items-center gap-4 flex-1">
              <div className="relative w-full max-w-xs group">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-blue-500 transition-colors" size={14} />
                <input
                    type="text"
                    placeholder="Поиск..."
                    className="w-full pl-9 pr-3 py-1.5 bg-slate-100/50 border-none rounded-xl text-xs focus:ring-1 focus:ring-blue-500/20 focus:bg-white transition-all outline-none"
                />
              </div>
            </div>

            <div className="flex items-center gap-4">
              <div className="hidden lg:flex items-center gap-1.5 px-3 py-1 bg-emerald-50 rounded-lg border border-emerald-100">
                <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-[9px] font-black text-emerald-700 uppercase tracking-tight">Active</span>
              </div>

              <button className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg relative">
                <Bell size={16} />
                <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-blue-600 rounded-full border border-white" />
              </button>

              <div className="h-6 w-px bg-slate-100" />

              <div className="flex items-center gap-2.5 pl-1">
                <div className="text-right hidden sm:block">
                  <p className="text-[11px] font-black text-slate-800 leading-none">Admin</p>
                  <p className="text-[8px] text-slate-400 font-bold uppercase">Root</p>
                </div>
                <div className="w-8 h-8 bg-slate-900 rounded-lg shadow-sm flex items-center justify-center text-white font-black text-[10px]">
                  AD
                </div>
              </div>
            </div>
          </header>

          <div className="p-6 max-w-[1600px] mx-auto w-full">
            <div className="mb-4 flex flex-col md:flex-row md:items-center justify-between gap-2 border-b border-slate-100 pb-4">
              <div className="flex items-center gap-4">
                <div className="p-2 bg-blue-600 rounded-lg text-white">
                  <CalendarRange size={16} />
                </div>
                <div>
                  <h1 className="text-xl font-black text-slate-900 capitalize tracking-tight leading-none">
                    {activeTab === 'dashboard' ? 'Dashboard' : activeTab}
                  </h1>
                  <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mt-1">Management System</p>
                </div>
              </div>
              <div className="flex items-center gap-1.5 text-[10px] font-bold text-slate-400 bg-slate-50 px-3 py-1 rounded-full border border-slate-100">
                <span className="hover:text-blue-600 cursor-pointer transition-colors">Home</span>
                <ChevronRight size={10} />
                <span className="text-slate-900 capitalize">{activeTab}</span>
              </div>
            </div>
            {renderContent()}
          </div>
        </main>
      </div>
  );
}
