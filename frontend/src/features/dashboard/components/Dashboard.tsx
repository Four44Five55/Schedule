import React, { useState } from 'react';
import { cn } from '../../../utils/cn';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Users, School, BookOpen, Play, CheckCircle2, Loader2, Zap, ArrowRight, TrendingUp } from 'lucide-react';

interface DashboardProps {
  stats: {
    educators: number;
    auditoriums: number;
    groups: number;
    disciplines: number;
  };
  onGenerate: (courseIds: number[]) => void;
  isGenerating: boolean;
}

const DEFAULT_COURSE_IDS = [704, 705, 701, 702, 707, 703, 706, 708, 709, 710];

export const Dashboard: React.FC<DashboardProps> = ({ stats, onGenerate, isGenerating }) => {
  const [selectedCourses] = useState<number[]>(DEFAULT_COURSE_IDS);

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard label="Преподаватели" value={stats.educators} icon={Users} color="blue" trend="+2 в этом семестре" />
        <StatCard label="Аудитории" value={stats.auditoriums} icon={School} color="emerald" trend="100% доступно" />
        <StatCard label="Группы" value={stats.groups} icon={Users} color="amber" trend="4 новых потока" />
        <StatCard label="Дисциплины" value={stats.disciplines} icon={BookOpen} color="purple" trend="Полное покрытие" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-6">
          <Card className="relative overflow-hidden border-none shadow-2xl shadow-blue-500/10 bg-gradient-to-br from-slate-900 to-slate-800 text-white">
            <div className="absolute top-0 right-0 p-8 opacity-10 pointer-events-none">
              <Zap size={120} />
            </div>
            
            <div className="relative z-10 space-y-6">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-500 rounded-lg">
                  <Zap size={24} className="text-white" />
                </div>
                <h3 className="text-xl font-black">Интеллектуальная генерация</h3>
              </div>

              <div className="p-4 bg-white/5 border border-white/10 rounded-2xl backdrop-blur-md">
                <div className="flex items-start gap-3">
                  <CheckCircle2 className="text-emerald-400 mt-1 shrink-0" size={20} />
                  <div className="text-sm text-slate-300 leading-relaxed">
                    <p className="font-bold text-white mb-1">Скоростной двухфазный алгоритм</p>
                    <p>Используется оптимизированная система распределения, учитывающая более 50 ограничений одновременно. Среднее время обработки: 2.4с.</p>
                  </div>
                </div>
              </div>

              <div className="space-y-4">
                <div className="flex justify-between items-end">
                  <p className="text-sm font-bold text-slate-400">Пакетная обработка ({selectedCourses.length} курсов)</p>
                  <span className="text-[10px] text-blue-400 font-black uppercase tracking-widest">Готов к запуску</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {selectedCourses.map(id => (
                    <div key={id} className="px-3 py-1 bg-white/10 border border-white/10 rounded-lg text-xs font-mono text-blue-200">
                      #{id}
                    </div>
                  ))}
                </div>
              </div>

              <div className="pt-4">
                <button
                  onClick={() => onGenerate(selectedCourses)}
                  disabled={isGenerating}
                  className={cn(
                    "w-full sm:w-auto px-10 py-4 rounded-2xl font-black transition-all flex items-center justify-center gap-3 group",
                    isGenerating
                      ? "bg-slate-700 text-slate-400 cursor-not-allowed"
                      : "bg-blue-600 hover:bg-blue-500 text-white shadow-xl shadow-blue-600/30 hover:shadow-blue-600/50 hover:-translate-y-0.5 active:translate-y-0"
                  )}
                >
                  {isGenerating ? (
                    <>
                      <Loader2 size={20} className="animate-spin" />
                      ГЕНЕРАЦИЯ...
                    </>
                  ) : (
                    <>
                      ЗАПУСТИТЬ ГЕНЕРАЦИЮ
                      <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                    </>
                  )}
                </button>
              </div>
            </div>
          </Card>
        </div>

        <div className="space-y-6">
          <Card title="Аналитика системы" className="h-full">
            <div className="space-y-8">
              <div className="space-y-2">
                <div className="flex justify-between items-center text-sm">
                  <span className="text-slate-500 font-medium">Заполнение сетки</span>
                  <span className="text-blue-600 font-black">-- %</span>
                </div>
                <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                  <div className="h-full bg-blue-500 w-[0%] transition-all duration-1000" />
                </div>
              </div>

              <div className="space-y-4">
                <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em]">История запусков</p>
                <div className="space-y-3">
                  {[1, 2, 3].map(i => (
                    <div key={i} className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl border border-slate-100">
                      <div className="w-8 h-8 bg-white rounded-lg flex items-center justify-center shadow-sm">
                        <TrendingUp size={16} className="text-emerald-500" />
                      </div>
                      <div>
                        <p className="text-xs font-bold text-slate-800">Успешный запуск #{1020 + i}</p>
                        <p className="text-[10px] text-slate-400">Вчера, 18:45 • 240мс</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              
              <button className="w-full py-3 bg-slate-900 text-white rounded-xl text-xs font-bold hover:bg-slate-800 transition-all shadow-lg shadow-slate-200">
                Сформировать отчет
              </button>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

const StatCard = ({ label, value, icon: Icon, color, trend }: any) => {
  const colors: any = {
    blue: 'from-blue-500/20 to-blue-500/5 text-blue-600 border-blue-100',
    emerald: 'from-emerald-500/20 to-emerald-500/5 text-emerald-600 border-emerald-100',
    amber: 'from-amber-500/20 to-amber-500/5 text-amber-600 border-amber-100',
    purple: 'from-purple-500/20 to-purple-500/5 text-purple-600 border-purple-100',
  };
  return (
    <Card className="p-0 border-none shadow-sm hover:shadow-xl transition-all duration-300 group overflow-hidden">
      <div className="p-6 space-y-4">
        <div className="flex justify-between items-start">
          <div className={cn("p-3 rounded-2xl bg-gradient-to-br shadow-inner", colors[color])}>
            <Icon size={24} />
          </div>
          <Badge variant={color === 'emerald' ? 'emerald' : 'slate'} className="bg-white/50 backdrop-blur-sm">Active</Badge>
        </div>
        <div>
          <p className="text-4xl font-black text-slate-900 tracking-tight">{value}</p>
          <p className="text-sm font-bold text-slate-500 mt-1">{label}</p>
        </div>
        <div className="pt-4 border-t border-slate-50 flex items-center gap-1.5">
          <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">{trend}</span>
        </div>
      </div>
    </Card>
  );
};
