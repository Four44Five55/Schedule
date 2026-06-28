import React, { useState } from 'react';
import { DisciplineDto, DisciplineCourseDto } from '../../../types/api';
import {
  BookOpen, Plus, Edit2, Trash2, ChevronDown,
} from 'lucide-react';
import { CurriculumService } from '../../../services/apiServices';
import { DisciplineFormModal } from './DisciplineFormModal';
import { DisciplineCourseFormModal } from './DisciplineCourseFormModal';
import { CurriculumPlanEditor } from './CurriculumPlanEditor';
import { courseSlotSource } from '../planSource';
import { cn } from '../../../utils/cn';

interface DisciplineListProps {
  disciplines: DisciplineDto[];
  onRefresh?: () => void;
}

export const DisciplineList: React.FC<DisciplineListProps> = ({ disciplines, onRefresh }) => {
  // Which courses are currently expanded (show plan editor)
  const [expandedCourses, setExpandedCourses] = useState<Set<number>>(new Set());

  // Discipline CRUD
  const [selectedDiscipline, setSelectedDiscipline] = useState<DisciplineDto | null>(null);
  const [showDisciplineForm, setShowDisciplineForm] = useState(false);
  const [deletingDiscipline, setDeletingDiscipline] = useState<number | null>(null);

  // Course CRUD
  const [selectedCourseForEdit, setSelectedCourseForEdit] = useState<DisciplineCourseDto | null>(null);
  const [courseFormDisciplineId, setCourseFormDisciplineId] = useState<number | undefined>();
  const [showCourseForm, setShowCourseForm] = useState(false);
  const [deletingCourse, setDeletingCourse] = useState<number | null>(null);

  const toggleExpand = (courseId: number) => {
    setExpandedCourses(prev => {
      const next = new Set(prev);
      if (next.has(courseId)) next.delete(courseId);
      else next.add(courseId);
      return next;
    });
  };

  // ── Discipline handlers ──────────────────────────────────────
  const handleDeleteDiscipline = async (id: number) => {
    if (!confirm('Удалить дисциплину? Все связанные курсы и занятия также будут удалены.')) return;
    setDeletingDiscipline(id);
    try {
      await CurriculumService.deleteDiscipline(id);
      onRefresh?.();
    } catch {
      alert('Не удалось удалить дисциплину');
    } finally {
      setDeletingDiscipline(null);
    }
  };

  // ── Course handlers ──────────────────────────────────────────
  const handleDeleteCourse = async (courseId: number) => {
    if (!confirm('Удалить курс?')) return;
    setDeletingCourse(courseId);
    try {
      await CurriculumService.deleteCourse(courseId);
      setExpandedCourses(prev => { const next = new Set(prev); next.delete(courseId); return next; });
      onRefresh?.();
    } catch {
      alert('Не удалось удалить курс');
    } finally {
      setDeletingCourse(null);
    }
  };

  return (
    <>
      <div className="mb-6 flex justify-end">
        <button
          onClick={() => { setSelectedDiscipline(null); setShowDisciplineForm(true); }}
          className="px-4 py-2 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors flex items-center gap-2"
        >
          <Plus size={16} />
          Новая дисциплина
        </button>
      </div>

      {disciplines.length === 0 && (
        <div className="py-20 text-center text-slate-300 border-2 border-dashed border-slate-100 rounded-2xl">
          <BookOpen size={48} className="mx-auto mb-4 opacity-30" />
          <p className="text-slate-400 font-medium">Нет дисциплин. Создайте первую.</p>
        </div>
      )}

      <div className="space-y-6">
        {disciplines.map(disc => (
          <div key={disc.id} className="border border-slate-200 rounded-2xl overflow-hidden bg-white">

            {/* ── Discipline header ── */}
            <div className="flex items-center justify-between px-5 py-4 bg-slate-50 border-b border-slate-200">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-blue-100 flex items-center justify-center shrink-0">
                  <span className="text-blue-700 font-black text-sm">
                    {disc.abbreviation || disc.name[0]}
                  </span>
                </div>
                <div>
                  <h3 className="font-bold text-slate-900">{disc.name}</h3>
                  {disc.abbreviation && (
                    <p className="text-xs text-slate-400 mt-0.5">{disc.abbreviation}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => { setSelectedDiscipline(disc); setShowDisciplineForm(true); }}
                  className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                  title="Редактировать дисциплину"
                >
                  <Edit2 size={15} />
                </button>
                <button
                  onClick={() => handleDeleteDiscipline(disc.id)}
                  disabled={deletingDiscipline === disc.id}
                  className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                  title="Удалить дисциплину"
                >
                  <Trash2 size={15} className={cn(deletingDiscipline === disc.id && 'animate-pulse')} />
                </button>
              </div>
            </div>

            {/* ── Courses ── */}
            <div className="divide-y divide-slate-100">
              {(!disc.courses || disc.courses.length === 0) && (
                <div className="px-5 py-4 text-sm text-slate-400 italic">
                  Нет учебных курсов
                </div>
              )}

              {disc.courses?.map(course => {
                const isExpanded = expandedCourses.has(course.id);

                return (
                  <div key={course.id}>
                    {/* Course row */}
                    <div
                      className={cn(
                        'flex items-center justify-between px-5 py-3 cursor-pointer transition-colors',
                        isExpanded ? 'bg-blue-50' : 'hover:bg-slate-50'
                      )}
                      onClick={() => toggleExpand(course.id)}
                    >
                      <div className="flex items-center gap-3">
                        <ChevronDown
                          size={16}
                          className={cn(
                            'text-slate-400 transition-transform shrink-0',
                            isExpanded ? 'rotate-0' : '-rotate-90'
                          )}
                        />
                        <div>
                          <span className={cn(
                            'text-sm font-semibold',
                            isExpanded ? 'text-blue-700' : 'text-slate-800'
                          )}>
                            Семестр {course.semester}
                          </span>
                          {course.studyPeriod && (
                            <span className="ml-2 text-xs text-slate-400">
                              {course.studyPeriod.name}
                            </span>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                        <button
                          onClick={() => { setSelectedCourseForEdit(course); setShowCourseForm(true); }}
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-100 rounded-lg transition-colors"
                          title="Редактировать курс"
                        >
                          <Edit2 size={13} />
                        </button>
                        <button
                          onClick={() => handleDeleteCourse(course.id)}
                          disabled={deletingCourse === course.id}
                          className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                          title="Удалить курс"
                        >
                          <Trash2 size={13} className={cn(deletingCourse === course.id && 'animate-pulse')} />
                        </button>
                      </div>
                    </div>

                    {/* ── Plan editor (общий компонент, тот же что в планировщике) ── */}
                    {isExpanded && (
                      <CurriculumPlanEditor
                        disciplineId={disc.id}
                        planSource={courseSlotSource(course.id)}
                      />
                    )}
                  </div>
                );
              })}
            </div>

            {/* ── Add course button ── */}
            <div className="px-5 py-3 border-t border-slate-100 bg-slate-50/50">
              <button
                onClick={() => { setCourseFormDisciplineId(disc.id); setSelectedCourseForEdit(null); setShowCourseForm(true); }}
                className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-blue-600 transition-colors"
              >
                <Plus size={13} />
                Добавить учебный курс
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* ── Modals ── */}
      {showDisciplineForm && (
        <DisciplineFormModal
          discipline={selectedDiscipline}
          onClose={() => { setShowDisciplineForm(false); setSelectedDiscipline(null); }}
          onSaved={() => { setShowDisciplineForm(false); setSelectedDiscipline(null); onRefresh?.(); }}
        />
      )}

      {showCourseForm && (
        <DisciplineCourseFormModal
          course={selectedCourseForEdit}
          disciplineId={courseFormDisciplineId}
          onClose={() => { setShowCourseForm(false); setSelectedCourseForEdit(null); setCourseFormDisciplineId(undefined); }}
          onSaved={() => { setShowCourseForm(false); setSelectedCourseForEdit(null); setCourseFormDisciplineId(undefined); onRefresh?.(); }}
        />
      )}
    </>
  );
};
