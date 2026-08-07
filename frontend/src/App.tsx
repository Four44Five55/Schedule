import { useState, useCallback, useEffect } from 'react';
import { Sidebar, TAB_IDS, type TabId } from './components/layout/Sidebar';
import { Dashboard } from './features/dashboard/components/Dashboard';
import { EducatorList } from './features/resources/components/EducatorList';
import { AuditoriumsSection } from './features/resources/components/AuditoriumsSection';
import { GroupList } from './features/resources/components/GroupList';
import { DisciplineList } from './features/curriculum/components/DisciplineList';
import { StudyStreamList } from './features/resources/components/StudyStreamList';
import { OrgUnitManager } from './features/orgUnit/components/OrgUnitManager';
import { ConstraintsManager } from './features/constraints/components/ConstraintsManager';
import { PlannerManager } from './features/planner/components/PlannerManager';
import { ScheduleManager } from './features/schedule/components/ScheduleManager';
import type { GroupDto, EducatorDto, AuditoriumDto, StudyStreamDto, DisciplineDto, StudyPeriodDto } from './types/api';
import { ResourceService, CurriculumService } from './services/apiServices';
import { CQRSService } from './services/cqrsApiService';
import { PeriodProvider, usePeriod, PeriodSelect } from './features/period/PeriodContext';
import { HelpCircle, CalendarRange } from 'lucide-react';

const ACTIVE_TAB_STORAGE_KEY = 'unischedule.activeTab';

export default function App() {
  // Общий контекст учебного периода — единая точка выбора для всех разделов.
  return (
    <PeriodProvider>
      <AppShell />
    </PeriodProvider>
  );
}

function AppShell() {
  // Смена периода — глобально (шапка). Генерация переключает период на сгенерированный.
  const { setSelectedPeriodId } = usePeriod();

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

  // ========== CRUD перезагрузки ==========
  const reloadGroups = useCallback(async () => {
    try { setGroups(await ResourceService.getGroups()); } catch (err) { console.error('Ошибка загрузки групп:', err); }
  }, []);

  const reloadEducators = useCallback(async () => {
    try { setEducators(await ResourceService.getEducators()); } catch (err) { console.error('Ошибка загрузки преподавателей:', err); }
  }, []);

  const reloadAuditoriums = useCallback(async () => {
    try { setAuditoriums(await ResourceService.getAuditoriums()); } catch (err) { console.error('Ошибка загрузки аудиторий:', err); }
  }, []);

  const reloadDisciplines = useCallback(async () => {
    try { setDisciplines(await CurriculumService.getDisciplines()); } catch (err) { console.error('Ошибка загрузки дисциплин:', err); }
  }, []);

  const reloadStreams = useCallback(async () => {
    try { setStreams(await ResourceService.getStreams()); } catch (err) { console.error('Ошибка загрузки потоков:', err); }
  }, []);

  // ========== Генерация расписания ==========
  const [isGenerating, setIsGenerating] = useState(false);

  const handleGenerateSchedule = async (courseIds: number[], period?: StudyPeriodDto) => {
    setIsGenerating(true);
    try {
      // Период обязателен для генерации. Планировщик передаёт выбранный явно;
      // прочие вызовы fallback'ятся на активный период.
      const targetPeriod = period ?? await ResourceService.getActiveStudyPeriod();
      if (!targetPeriod) {
        alert('Не выбран учебный период. Создайте/выберите период в планировщике.');
        return;
      }

      // Генерация всегда идёт через сессию периода (не создаём новую «с нуля»):
      // так закреплённые вручную занятия (пины) не архивируются, а генератор
      // раскладывает вокруг них.
      const session = await CQRSService.getSessionForPeriod(targetPeriod.id);
      await CQRSService.regenerateKeepingLocked(session.id, {
        name: 'Генерация от ' + new Date().toLocaleString('ru-RU'),
        studyPeriodId: targetPeriod.id,
        courseIds: courseIds
      });

      // Показываем именно сгенерированный период (общий выбор) и открываем расписание —
      // раздел «Расписание» сам подтянет занятия этого периода.
      setSelectedPeriodId(targetPeriod.id);
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
              <p className="text-slate-400 text-sm font-medium">Подключаемся к базе данных...</p>
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
                onNavigate={setActiveTab}
            />
        );
      case 'educators':
        return <EducatorList educators={educators} onEducatorsChange={reloadEducators} />;
      case 'auditoriums':
        return <AuditoriumsSection auditoriums={auditoriums} onAuditoriumsChange={reloadAuditoriums} />;
      case 'groups':
        return <GroupList groups={groups} onGroupsChange={reloadGroups} />;
      case 'disciplines':
        // Справочник: планы правятся в планировщике, отсюда — только переход туда.
        return <DisciplineList disciplines={disciplines} onRefresh={reloadDisciplines} onNavigate={setActiveTab} />;
      case 'streams':
        return <StudyStreamList streams={streams} onStreamsChange={reloadStreams} />;
      case 'orgUnits':
        // Преподаватели и группы уже загружены — счётчики подразделений считаются из них,
        // без отдельных запросов. После правок обновляем оба списка: там показывается привязка.
        return (
            <OrgUnitManager
                educators={educators}
                groups={groups}
                onChanged={() => {
                  void reloadEducators();
                  void reloadGroups();
                }}
            />
        );
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
        // Раздел «Расписание» самодостаточен: период берёт из общего контекста и сам
        // грузит занятия этого периода.
        return <ScheduleManager />;
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
          <div className="p-6 max-w-[1600px] mx-auto w-full">
            {/* Тонкий заголовок раздела + ЕДИНЫЙ глобальный выбор учебного периода справа
                (одна точка смены периода для планировщика/расписания/ограничений/дашборда). */}
            <div className="mb-3 flex items-center gap-2.5">
              <div className="p-1.5 bg-blue-600 rounded-lg text-white">
                <CalendarRange size={14} />
              </div>
              <h1 className="text-base font-black text-slate-900 capitalize tracking-tight leading-none">
                {activeTab === 'dashboard' ? 'Dashboard' : activeTab}
              </h1>
              <div className="ml-auto">
                <PeriodSelect />
              </div>
            </div>
            {renderContent()}
          </div>
        </main>
      </div>
  );
}
