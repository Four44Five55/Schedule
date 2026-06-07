import { useState, useEffect } from 'react';
import { Sidebar, TabId } from './components/layout/Sidebar';
import { Dashboard } from './features/dashboard/components/Dashboard';
import { EducatorList } from './features/resources/components/EducatorList';
import { AuditoriumGrid } from './features/resources/components/AuditoriumGrid';
import { ScheduleManager } from './features/schedule/components/ScheduleManager';
import { useResources } from './hooks/useData';
import { 
  Bell, 
  Search, 
  HelpCircle 
} from 'lucide-react';

function App() {
  const [activeTab, setActiveTab] = useState<TabId>('dashboard');
  const { educators, auditoriums, groups, disciplines, loading } = useResources();
  const [scheduleLessons, setScheduleLessons] = useState<any[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);

  const handleGenerateSchedule = async (courseIds: number[]) => {
    setIsGenerating(true);
    try {
      const { ScheduleService } = await import('./services/apiServices');
      const result = await ScheduleService.generateBatch(courseIds);
      setScheduleLessons(result.lessons);
      alert(`Генерация завершена! Размещено: ${result.placedCount} занятий.`);
      setActiveTab('schedule');
    } catch (err) {
      console.error(err);
      alert("Ошибка при генерации расписания");
    } finally {
      setIsGenerating(false);
    }
  };

  const renderContent = () => {
    if (loading) return <div className="p-8">Загрузка данных из системы...</div>;

    switch (activeTab) {
      case 'dashboard':
        return (
          <Dashboard 
            stats={{ 
              educators: educators.length, 
              auditoriums: auditoriums.length, 
              groups: groups.length, 
              disciplines: disciplines.length 
            }} 
            onGenerate={handleGenerateSchedule}
            isGenerating={isGenerating}
          />
        );
      case 'educators':
        return <EducatorList educators={educators} />;
      case 'auditoriums':
        return <AuditoriumGrid auditoriums={auditoriums} />;
      case 'schedule':
        return (
          <ScheduleManager 
            lessons={scheduleLessons} 
            startDate={new Date(2026, 1, 9)} 
            endDate={new Date(2026, 7, 31)} 
          />
        );
      default:
        return (
          <div className="flex flex-col items-center justify-center h-96 text-slate-400">
            <HelpCircle size={48} className="mb-4 opacity-20" />
            <p>Модуль "{activeTab}" находится в разработке</p>
          </div>
        );
    }
  };

  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />
      
      <main className="flex-1 flex flex-col min-w-0">
        <header className="h-16 bg-white border-b border-slate-200 sticky top-0 z-10 px-8 flex items-center justify-between">
          <div className="relative w-96">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
            <input 
              type="text" 
              placeholder="Быстрый поиск..." 
              className="w-full pl-10 pr-4 py-2 bg-slate-50 border-none rounded-lg text-sm focus:ring-2 focus:ring-blue-500 transition-all"
            />
          </div>

          <div className="flex items-center gap-4">
            <button className="p-2 text-slate-400 hover:text-slate-600 relative">
              <Bell size={20} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full border-2 border-white"></span>
            </button>
            <div className="h-8 w-px bg-slate-200 mx-2" />
            <div className="flex items-center gap-3">
              <div className="text-right">
                <p className="text-sm font-bold text-slate-800 leading-tight">Admin User</p>
                <p className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">System Root</p>
              </div>
              <div className="w-10 h-10 bg-gradient-to-br from-blue-600 to-indigo-700 rounded-xl shadow-blue-200 shadow-lg" />
            </div>
          </div>
        </header>

        <div className="p-8 max-w-[1600px] mx-auto w-full">
          <header className="mb-8">
            <h1 className="text-2xl font-bold text-slate-900 capitalize">{activeTab}</h1>
            <p className="text-slate-500 text-sm">Управление образовательным процессом университета</p>
          </header>
          
          {renderContent()}
        </div>
      </main>
    </div>
  );
}

export default App;
