import React from 'react';
import {
  LayoutDashboard,
  Users,
  BookOpen,
  School,
  Calendar,
  ShieldAlert,
  Layers,
  LogOut
} from 'lucide-react';
import { cn } from '../../utils/cn';

export type TabId =
    | 'dashboard'
    | 'curriculum'
    | 'disciplines'
    | 'educators'
    | 'auditoriums'
    | 'groups'
    | 'streams'
    | 'constraints'
    | 'schedule';

interface SidebarProps {
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
}

const menuItems: { id: TabId; label: string; icon: React.ElementType; color: string }[] = [
  { id: 'dashboard', label: 'Обзор', icon: LayoutDashboard, color: 'text-blue-500' },
  { id: 'schedule', label: 'Расписание', icon: Calendar, color: 'text-emerald-500' },
  { id: 'curriculum', label: 'Учебный план', icon: BookOpen, color: 'text-violet-500' },
  { id: 'disciplines', label: 'Дисциплины', icon: Layers, color: 'text-orange-500' },
  { id: 'educators', label: 'Преподаватели', icon: Users, color: 'text-indigo-500' },
  { id: 'auditoriums', label: 'Аудитории', icon: School, color: 'text-rose-500' },
  { id: 'groups', label: 'Группы', icon: Users, color: 'text-cyan-500' },
  { id: 'streams', label: 'Потоки', icon: Layers, color: 'text-teal-500' },
  { id: 'constraints', label: 'Ограничения', icon: ShieldAlert, color: 'text-amber-500' },
];

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab }) => {
  return (
      <aside className="w-52 bg-white border-r border-slate-100 h-screen flex flex-col sticky top-0 shrink-0 z-50 transition-all duration-300">
        <div className="p-4 mb-2">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 bg-gradient-to-tr from-blue-600 to-indigo-600 rounded-md flex items-center justify-center text-white font-black text-[10px] shadow-md shadow-blue-200 shrink-0">
              U
            </div>
            <div className="overflow-hidden">
              <h1 className="font-black text-slate-900 leading-none text-sm tracking-tight truncate">UniSchedule</h1>
              <p className="text-[6px] text-slate-400 font-bold uppercase tracking-[0.2em] mt-0.5 truncate">Management</p>
            </div>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto px-2 space-y-0.5 custom-scrollbar">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
                <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
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
  );
};
