import { useState } from 'react';
import { Sidebar, type TabId } from './components/layout/Sidebar';
import { Dashboard } from './features/dashboard/components/Dashboard';
import { EducatorList } from './features/resources/components/EducatorList';
import { AuditoriumGrid } from './features/resources/components/AuditoriumGrid';
import { GroupList } from './features/resources/components/GroupList';
import { DisciplineList } from './features/curriculum/components/DisciplineList';
import { StudyStreamList } from './features/resources/components/StudyStreamList';
import { ConstraintsManager } from './features/constraints/components/ConstraintsManager';
import { CurriculumManager } from './features/curriculum/components/CurriculumManager';
import { ScheduleManager } from './features/schedule/components/ScheduleManager';
import { useResources } from './hooks/useData';
import type { ScheduledLessonDto } from './types/api';
import { ScheduleService } from './services/apiServices';
import { Bell, Search, HelpCircle, Loader2, CalendarRange, ChevronRight } from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<TabId>('dashboard');
  const resourceData = useResources();
  const { educators, auditoriums, groups, disciplines, loading } = resourceData;
  const [scheduleLessons, setScheduleLessons] = useState<ScheduledLessonDto[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);

  const handleGenerateSchedule = async (courseIds: number[]) => {
    setIsGenerating(true);
    try {
      const result = await ScheduleService.generateBatch(courseIds);
      setScheduleLessons(result.lessons);
      setActiveTab('schedule');
    } catch (err) {
      console.error(err);
      alert('Ошибка при генерации расписания');
    } finally {
      setIsGenerating(false);
    }
  };

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

    return (
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-700">
          {(() => {
            switch (activeTab) {
              case 'dashboard':
                return (
                    <Dashboard
                        stats={{ educators: educators.length, auditoriums: auditoriums.length, groups: groups.length, disciplines: disciplines.length }}
                        onGenerate={handleGenerateSchedule}
                        isGenerating={isGenerating}
                    />
                );
              case 'educators': return <EducatorList educators={educators} />;
              case 'auditoriums': return <AuditoriumGrid auditoriums={auditoriums} />;
              case 'groups': return <GroupList groups={groups} />;
              case 'disciplines': return <DisciplineList disciplines={disciplines} />;
              case 'streams': return <StudyStreamList streams={(resourceData as any).streams || []} />;
              case 'constraints': return <ConstraintsManager />;
              case 'curriculum': return <CurriculumManager disciplines={disciplines} />;
              case 'schedule':
                return <ScheduleManager lessons={scheduleLessons} startDate={new Date(2026, 1, 9)} endDate={new Date(2026, 7, 31)} />;
              default:
                return (
                    <div className="flex flex-col items-center justify-center h-96 text-slate-300">
                      <HelpCircle size={48} className="mb-4 opacity-10" />
                      <p className="text-lg font-bold">Модуль "{activeTab}" не найден</p>
                    </div>
                );
            }
          })()}
        </div>
    );
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
                  <h1 className="text-xl font-black text-slate-900 capitalize tracking-tight leading-none">{activeTab === 'dashboard' ? 'Dashboard' : activeTab}</h1>
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
