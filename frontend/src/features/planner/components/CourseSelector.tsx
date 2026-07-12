import React, { useState } from 'react';
import { DisciplineDto, DisciplineCourseDto, CurriculumSlotDto } from '../../../types/api';
import { ChevronRight, Check, BookOpen, Trash2 } from 'lucide-react';
import { cn } from '../../../utils/cn';
import { CurriculumPlanEditor } from '../../curriculum/components/CurriculumPlanEditor';
import { courseSlotSource } from '../../curriculum/planSource';

export const CourseSelector: React.FC<{
  disciplines: DisciplineDto[];
  allCourses: DisciplineCourseDto[];
  selectedCourses: Set<number>;
  courseSlots: Map<number, CurriculumSlotDto[]>;
  onToggle: (id: number) => void;
  onCourseChanged: (courseId: number) => void;
  onDelete: (courseId: number) => void;
  deletingCourseId: number | null;
}> = ({ disciplines, allCourses, selectedCourses, courseSlots, onToggle, onCourseChanged, onDelete, deletingCourseId }) => {
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const toggleExpand = (id: number) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const grouped = disciplines
    .map(d => ({ discipline: d, courses: allCourses.filter(c => c.discipline.id === d.id) }))
    .filter(g => g.courses.length > 0);

  if (grouped.length === 0) {
    return (
      <div className="py-16 text-center text-slate-400 text-sm">
        <BookOpen className="mx-auto mb-3 opacity-20" size={36} />
        <p>Нет доступных курсов дисциплин</p>
      </div>
    );
  }

  return (
    <div className="divide-y divide-slate-100">
      {grouped.map(({ discipline, courses }) => (
        <div key={discipline.id}>
          <div className="px-5 py-3 bg-slate-50 flex items-center gap-3">
            <div className="w-7 h-7 rounded bg-blue-100 flex items-center justify-center shrink-0">
              <span className="text-blue-700 font-bold text-xs">
                {discipline.abbreviation || discipline.name[0]}
              </span>
            </div>
            <span className="font-semibold text-slate-800 text-sm">{discipline.name}</span>
            <span className="text-xs text-slate-400">({courses.length})</span>
          </div>

          {courses.map(course => {
            const isSelected = selectedCourses.has(course.id);
            const slots = courseSlots.get(course.id) || [];
            const isExpanded = expanded.has(course.id);

            return (
              <div key={course.id} className={cn(isSelected && 'bg-blue-50/50')}>
                <div
                  className="px-5 py-3 flex items-center justify-between cursor-pointer hover:bg-slate-50 transition-colors"
                  onClick={() => onToggle(course.id)}
                >
                  <div className="flex items-center gap-3">
                    <div className={cn(
                      'w-4 h-4 rounded border-2 flex items-center justify-center transition-all shrink-0',
                      isSelected ? 'bg-blue-600 border-blue-600' : 'border-slate-300'
                    )}>
                      {isSelected && <Check size={10} className="text-white" />}
                    </div>
                    <span className="text-sm font-medium text-slate-800">
                      Семестр {course.semester}
                    </span>
                    {course.studyPeriod && (
                      <span className="text-xs text-slate-400">· {course.studyPeriod.name}</span>
                    )}
                    {isSelected && slots.length > 0 && (
                      <span className="text-xs text-blue-500 font-medium">{slots.length} занятий</span>
                    )}
                  </div>
                  <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                    <button
                      onClick={() => onDelete(course.id)}
                      disabled={deletingCourseId === course.id}
                      className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
                      title="Удалить курс"
                    >
                      <Trash2 size={13} className={cn(deletingCourseId === course.id && 'animate-pulse')} />
                    </button>
                    <button
                      onClick={() => toggleExpand(course.id)}
                      className="p-1 rounded hover:bg-slate-200 transition-colors"
                      title={isExpanded ? 'Свернуть план' : 'Показать план'}
                    >
                      <ChevronRight
                        size={14}
                        className={cn('text-slate-400 transition-transform', isExpanded && 'rotate-90')}
                      />
                    </button>
                  </div>
                </div>

                {isExpanded && (
                  <CurriculumPlanEditor
                    disciplineId={course.discipline.id}
                    planSource={courseSlotSource(course.id)}
                    onChanged={() => onCourseChanged(course.id)}
                  />
                )}
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
};
