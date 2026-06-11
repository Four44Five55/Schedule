import React, { useState, useEffect } from 'react';
import { CurriculumService } from '../../../services/apiServices';
import { DisciplineDto, DisciplineCourseDto, CurriculumSlotDto } from '../../../types/api';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { BookOpen, ChevronRight, FileText, User, Users, School } from 'lucide-react';
import { cn } from '../../../utils/cn';

export const CurriculumManager: React.FC<{ disciplines: DisciplineDto[] }> = ({ disciplines }) => {
  const [selectedDiscipline, setSelectedDiscipline] = useState<DisciplineDto | null>(null);
  const [courses, setCourses] = useState<DisciplineCourseDto[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState<number | null>(null);
  const [slots, setSlots] = useState<CurriculumSlotDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (selectedDiscipline) {
      CurriculumService.getCoursesByDiscipline(selectedDiscipline.id).then(setCourses);
    } else {
      setCourses([]);
      setSelectedCourseId(null);
    }
  }, [selectedDiscipline]);

  useEffect(() => {
    if (selectedCourseId) {
      setLoading(true);
      CurriculumService.getSlotsByCourse(selectedCourseId)
        .then(setSlots)
        .finally(() => setLoading(false));
    } else {
      setSlots([]);
    }
  }, [selectedCourseId]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 items-start">
      {/* Sidebar: Disciplines & Courses */}
      <div className="lg:col-span-1 space-y-4">
        <Card title="Дисциплины" className="p-0">
          <div className="max-h-[300px] overflow-y-auto">
            {disciplines.map(d => (
              <button
                key={d.id}
                onClick={() => setSelectedDiscipline(d)}
                className={cn(
                  "w-full text-left px-4 py-3 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 last:border-none",
                  selectedDiscipline?.id === d.id && "bg-blue-50 text-blue-700 font-bold"
                )}
              >
                <span className="truncate">{d.name}</span>
                <ChevronRight size={14} className={cn("opacity-0", selectedDiscipline?.id === d.id && "opacity-100")} />
              </button>
            ))}
          </div>
        </Card>

        {selectedDiscipline && (
          <Card title="Семестры / Курсы" className="p-0">
            <div className="p-2 space-y-1">
              {courses.map(c => (
                <button
                  key={c.id}
                  onClick={() => setSelectedCourseId(c.id)}
                  className={cn(
                    "w-full text-left px-3 py-2 rounded-lg text-sm transition-all",
                    selectedCourseId === c.id ? "bg-slate-900 text-white font-bold" : "hover:bg-slate-100 text-slate-600"
                  )}
                >
                  {c.semester} семестр
                </button>
              ))}
              {courses.length === 0 && <p className="p-4 text-xs text-slate-400 italic">Нет активных курсов</p>}
            </div>
          </Card>
        )}
      </div>

      {/* Main: Slots Table */}
      <div className="lg:col-span-3">
        {selectedCourseId ? (
          <Card title={`Учебный план: ${selectedDiscipline?.name}`}>
            {loading ? (
              <div className="py-12 text-center text-slate-400 animate-pulse">Загрузка слотов...</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse">
                  <thead>
                    <tr className="bg-slate-50 text-[10px] text-slate-500 uppercase tracking-widest font-bold">
                      <th className="px-4 py-3 text-left w-12 border-b border-slate-100">#</th>
                      <th className="px-4 py-3 text-left border-b border-slate-100">Вид занятия</th>
                      <th className="px-4 py-3 text-left border-b border-slate-100">Тема</th>
                      <th className="px-4 py-3 text-left border-b border-slate-100">Требования к ауд.</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {slots.map((slot) => (
                      <tr key={slot.id} className="hover:bg-slate-50/50 transition-colors text-sm">
                        <td className="px-4 py-3 font-mono text-slate-400">{slot.position}</td>
                        <td className="px-4 py-3">
                          <Badge variant={slot.kindOfStudy === 'LECTURE' ? 'blue' : 'slate'}>
                            {slot.kindOfStudy}
                          </Badge>
                        </td>
                        <td className="px-4 py-3">
                          {slot.themeLesson ? (
                            <div className="flex flex-col">
                              <span className="font-bold text-slate-700">Т.{slot.themeLesson.themeNumber}</span>
                              <span className="text-xs text-slate-500 truncate max-w-[200px]">{slot.themeLesson.title}</span>
                            </div>
                          ) : <span className="text-slate-300">—</span>}
                        </td>
                        <td className="px-4 py-3 space-y-1">
                          {slot.requiredAuditorium && (
                            <div className="flex items-center gap-1.5 text-xs text-red-600 font-medium">
                              <School size={12} /> {slot.requiredAuditorium.name} (Обяз.)
                            </div>
                          )}
                          {slot.priorityAuditorium && (
                            <div className="flex items-center gap-1.5 text-xs text-blue-600">
                              <School size={12} /> {slot.priorityAuditorium.name} (Приор.)
                            </div>
                          )}
                          {!slot.requiredAuditorium && !slot.priorityAuditorium && <span className="text-slate-300">—</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        ) : (
          <div className="h-64 flex flex-col items-center justify-center text-slate-300 border-2 border-dashed border-slate-100 rounded-2xl bg-white">
            <FileText size={48} className="mb-4 opacity-20" />
            <p className="font-medium text-slate-400">Выберите дисциплину и семестр для просмотра плана</p>
          </div>
        )}
      </div>
    </div>
  );
};
