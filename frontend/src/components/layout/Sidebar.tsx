import React, { useEffect } from 'react';
import {
  LayoutDashboard,
  Users,
  BookOpen,
  School,
  Calendar,
  ShieldAlert,
  Layers,
  LogOut,
  CalendarRange,
  Menu,
  ChevronLeft,
  Network,
  FileUp
} from 'lucide-react';
import { cn } from '../../utils/cn';

export type TabId =
    | 'dashboard'
    | 'planner'
    | 'curriculum'
    | 'disciplines'
    | 'educators'
    | 'auditoriums'
    | 'groups'
    | 'streams'
    | 'orgUnits'
    | 'constraints'
    | 'schedule'
    | 'import';

interface SidebarProps {
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
}

const menuItems: { id: TabId; label: string; icon: React.ElementType; color: string }[] = [
  { id: 'dashboard', label: 'Обзор', icon: LayoutDashboard, color: 'text-blue-500' },
  { id: 'planner', label: 'Планировщик', icon: CalendarRange, color: 'text-blue-600' },
  { id: 'schedule', label: 'Расписание', icon: Calendar, color: 'text-emerald-500' },
  { id: 'curriculum', label: 'Учебный план', icon: BookOpen, color: 'text-violet-500' },
  { id: 'disciplines', label: 'Дисциплины', icon: Layers, color: 'text-orange-500' },
  { id: 'educators', label: 'Преподаватели', icon: Users, color: 'text-indigo-500' },
  { id: 'auditoriums', label: 'Аудитории', icon: School, color: 'text-rose-500' },
  { id: 'groups', label: 'Группы', icon: Users, color: 'text-cyan-500' },
  { id: 'streams', label: 'Потоки', icon: Layers, color: 'text-teal-500' },
  { id: 'orgUnits', label: 'Оргструктура', icon: Network, color: 'text-sky-500' },
  { id: 'constraints', label: 'Ограничения', icon: ShieldAlert, color: 'text-amber-500' },
  { id: 'import', label: 'Импорт', icon: FileUp, color: 'text-fuchsia-500' },
];

/** Все валидные id вкладок — единый источник для навигации и восстановления состояния. */
export const TAB_IDS: TabId[] = menuItems.map((item) => item.id);

/**
 * Навигация из двух слоёв:
 * - **Рельс** (`w-14`) — всегда в потоке, только иконки. Держит ширину постоянной, поэтому
 *   контент справа (широкие сетки расписания) не «бегает» при разворачивании.
 * - **Развёрнутая панель** — `fixed` поверх контента (вне потока → нулевой layout shift).
 *   Открывается КЛИКОМ по кнопке-меню, закрывается кликом по пункту, вне панели или Esc.
 */
export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab }) => {
  const [expanded, setExpanded] = React.useState(false);

  // Esc закрывает развёрнутую панель.
  useEffect(() => {
    if (!expanded) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setExpanded(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [expanded]);

  const handleSelect = (id: TabId) => {
    setActiveTab(id);
    setExpanded(false);
  };

  return (
      <>
        {/* ===== РЕЛЬС (всегда виден, в потоке) ===== */}
        <aside className="w-14 bg-white border-r border-slate-100 h-screen flex flex-col items-center sticky top-0 shrink-0 z-30">
          <button
              onClick={() => setExpanded(true)}
              aria-label="Открыть меню"
              aria-expanded={expanded}
              title="Меню"
              className="mt-4 mb-2 w-10 h-10 flex items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-900 transition-colors"
          >
            <Menu size={18} />
          </button>

          <nav className="flex-1 overflow-y-auto w-full px-2 py-1 flex flex-col items-center gap-1 custom-scrollbar">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                  <button
                      key={item.id}
                      onClick={() => setActiveTab(item.id)}
                      title={item.label}
                      aria-label={item.label}
                      className={cn(
                          'relative w-10 h-10 flex items-center justify-center rounded-lg transition-all duration-200 group',
                          isActive
                              ? 'bg-blue-50 shadow-sm'
                              : 'text-slate-500 hover:bg-slate-50'
                      )}
                  >
                    <Icon size={18} className={cn(isActive ? item.color : 'text-slate-400 group-hover:text-slate-600')} />
                    {isActive && (
                        <div className="absolute left-0 w-1 h-4 rounded-full bg-blue-600" />
                    )}
                  </button>
              );
            })}
          </nav>

          <div className="py-3">
            <button
                title="Выход"
                aria-label="Выход"
                className="w-10 h-10 flex items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors"
            >
              <LogOut size={16} />
            </button>
          </div>
        </aside>

        {/* ===== ПОДЛОЖКА (клик вне панели закрывает) ===== */}
        <div
            aria-hidden
            onClick={() => setExpanded(false)}
            className={cn(
                'fixed inset-0 z-40 bg-slate-900/10 transition-opacity duration-200',
                expanded ? 'opacity-100' : 'opacity-0 pointer-events-none'
            )}
        />

        {/* ===== РАЗВЁРНУТАЯ ПАНЕЛЬ (fixed, поверх контента) ===== */}
        <aside
            className={cn(
                'fixed inset-y-0 left-0 w-52 bg-white border-r border-slate-100 shadow-2xl h-screen flex flex-col z-50 transition-transform duration-300 ease-out',
                expanded ? 'translate-x-0' : '-translate-x-full'
            )}
        >
          <div className="p-4 mb-2 flex items-center justify-between">
            <div className="flex items-center gap-2 overflow-hidden">
              <div className="w-6 h-6 bg-gradient-to-tr from-blue-600 to-indigo-600 rounded-md flex items-center justify-center text-white font-black text-[10px] shadow-md shadow-blue-200 shrink-0">
                U
              </div>
              <div className="overflow-hidden">
                <h1 className="font-black text-slate-900 leading-none text-sm tracking-tight truncate">UniSchedule</h1>
                <p className="text-[6px] text-slate-400 font-bold uppercase tracking-[0.2em] mt-0.5 truncate">Management</p>
              </div>
            </div>
            <button
                onClick={() => setExpanded(false)}
                aria-label="Свернуть меню"
                title="Свернуть"
                className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors shrink-0"
            >
              <ChevronLeft size={16} />
            </button>
          </div>

          <nav className="flex-1 overflow-y-auto px-2 space-y-0.5 custom-scrollbar">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                  <button
                      key={item.id}
                      onClick={() => handleSelect(item.id)}
                      className={cn(
                          'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg transition-all duration-200 group relative',
                          isActive
                              ? 'bg-blue-50 text-blue-700 font-bold shadow-sm'
                              : 'text-slate-500 hover:bg-slate-50 hover:text-slate-900'
                      )}
                  >
                    <Icon size={16} className={cn(isActive ? item.color : 'text-slate-400 group-hover:text-slate-600')} />
                    <span className="text-xs truncate">{item.label}</span>
                    {isActive && (
                        <div className="absolute left-0 w-1 h-4 rounded-full bg-blue-600" />
                    )}
                  </button>
              );
            })}
          </nav>

          <div className="p-3">
            <div className="bg-slate-900 rounded-xl p-3 relative overflow-hidden group">
              <div className="relative z-10 flex items-center gap-2 mb-2">
                <div className="w-6 h-6 rounded-full bg-slate-700 border border-slate-600 flex items-center justify-center text-[8px] font-bold text-white uppercase shrink-0">
                  AD
                </div>
                <div className="overflow-hidden">
                  <p className="text-[10px] font-bold text-white truncate">Administrator</p>
                </div>
              </div>
              <button className="relative z-10 w-full flex items-center justify-center gap-1.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-white text-[9px] font-bold rounded-lg transition-colors">
                <LogOut size={12} />
                Выход
              </button>
            </div>
          </div>
        </aside>
      </>
  );
};
