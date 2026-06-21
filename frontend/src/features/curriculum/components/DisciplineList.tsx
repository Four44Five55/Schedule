import React, { useState } from 'react';
import { DisciplineDto, DisciplineCourseDto, CurriculumSlotDto } from '../../../types/api';
import {
  BookOpen, Plus, Edit2, Trash2, ChevronDown,
  School, FileText, Loader2
} from 'lucide-react';
import { CurriculumService } from '../../../services/apiServices';
import { DisciplineFormModal } from './DisciplineFormModal';
import { DisciplineCourseFormModal } from './DisciplineCourseFormModal';
import { CurriculumSlotFormModal } from './CurriculumSlotFormModal';
import { cn } from '../../../utils/cn';

interface DisciplineListProps {
  disciplines: DisciplineDto[];
  onRefresh?: () => void;
}

const KIND_LABELS: Record<string, string> = {
  LECTURE: 'Лекция',
  PRACTICAL_WORK: 'Практика',
  LAB_WORK: 'Лаб. работа',
  SEMINAR: 'Семинар',
  GROUP_WORK: 'Групп. работа',
  GROUP_EXERCISE: 'Упражнение',
  QUIZ: 'Зачёт',
  INDIVIDUAL_REVIEW_INTERVIEW: 'Инд. опрос',
  CREDIT_WITH_GRADE: 'Зачёт с оценкой',
  CREDIT_WITHOUT_GRADE: 'Зачёт б/о',
  EXAM: 'Экзамен',
  INDEPENDENT_STUDY: 'Самост. работа',
};

const KIND_COLORS: Record<string, string> = {
  LECTURE: 'bg-violet-100 text-violet-700 border-violet-200',
  EXAM: 'bg-red-100 text-red-700 border-red-200',
  LAB_WORK: 'bg-green-100 text-green-700 border-green-200',
  PRACTICAL_WORK: 'bg-blue-100 text-blue-700 border-blue-200',
  SEMINAR: 'bg-indigo-100 text-indigo-700 border-indigo-200',
  CREDIT_WITH_GRADE: 'bg-amber-100 text-amber-700 border-amber-200',
  CREDIT_WITHOUT_GRADE: 'bg-amber-100 text-amber-700 border-amber-200',
  QUIZ: 'bg-amber-100 text-amber-700 border-amber-200',
};

const SlotsSummary: React.FC<{ slots: CurriculumSlotDto[] }> = ({ slots }) => {
  const counts = slots.reduce<Record<string, number>>((acc, s) => {
    acc[s.kindOfStudy] = (acc[s.kindOfStudy] || 0) + 1;
    return acc;
  }, {});

  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);

  return (
    <div className="flex items-center gap-2 flex-wrap px-8 py-3 border-b border-blue-100 bg-white/60">
      {entries.map(([kind, count]) => (
        <div
          key={kind}
          className={cn(
            'flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-xs font-semibold',
            KIND_COLORS[kind] || 'bg-slate-100 text-slate-600 border-slate-200'
          )}
        >
          <span>{KIND_LABELS[kind] || kind}</span>
          <span className="font-black opacity-70">·</span>
          <span className="font-black">{count}</span>
        </div>
      ))}
      <div className="ml-auto text-xs text-slate-400 font-medium">
        Всего: <span className="font-bold text-slate-700">{slots.length}</span> зан.
      </div>
    </div>
  );
};

// Cache: courseId → slots
type SlotsCache = Map<number, CurriculumSlotDto[]>;

export const DisciplineList: React.FC<DisciplineListProps> = ({ disciplines, onRefresh }) => {
  // Which courses are currently expanded (show slots)
  const [expandedCourses, setExpandedCourses] = useState<Set<number>>(new Set());
  // Slots per course
  const [slotsCache, setSlotsCache] = useState<SlotsCache>(new Map());
  const [loadingCourses, setLoadingCourses] = useState<Set<number>>(new Set());

  // Discipline CRUD
  const [selectedDiscipline, setSelectedDiscipline] = useState<DisciplineDto | null>(null);
  const [showDisciplineForm, setShowDisciplineForm] = useState(false);
  const [deletingDiscipline, setDeletingDiscipline] = useState<number | null>(null);

  // Course CRUD
  const [selectedCourseForEdit, setSelectedCourseForEdit] = useState<DisciplineCourseDto | null>(null);
  const [courseFormDisciplineId, setCourseFormDisciplineId] = useState<number | undefined>();
  const [showCourseForm, setShowCourseForm] = useState(false);
  const [deletingCourse, setDeletingCourse] = useState<number | null>(null);

  // Slot CRUD
  const [slotFormCourseId, setSlotFormCourseId] = useState<number | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<CurriculumSlotDto | null>(null);
  const [showSlotForm, setShowSlotForm] = useState(false);
  const [deletingSlot, setDeletingSlot] = useState<number | null>(null);

  const loadSlots = async (courseId: number) => {
    setLoadingCourses(prev => new Set(prev).add(courseId));
    try {
      const data = await CurriculumService.getSlotsByCourse(courseId);
      setSlotsCache(prev => new Map(prev).set(courseId, data));
    } finally {
      setLoadingCourses(prev => { const next = new Set(prev); next.delete(courseId); return next; });
    }
  };

  const reloadSlots = (courseId: number) => loadSlots(courseId);

  const toggleExpand = (courseId: number) => {
    setExpandedCourses(prev => {
      const next = new Set(prev);
      if (next.has(courseId)) {
        next.delete(courseId);
      } else {
        next.add(courseId);
        if (!slotsCache.has(courseId)) {
          loadSlots(courseId);
        }
      }
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
      setSlotsCache(prev => { const next = new Map(prev); next.delete(courseId); return next; });
      onRefresh?.();
    } catch {
      alert('Не удалось удалить курс');
    } finally {
      setDeletingCourse(null);
    }
  };

  // ── Slot handlers ────────────────────────────────────────────
  const handleDeleteSlot = async (slotId: number, courseId: number) => {
    if (!confirm('Удалить занятие?')) return;
    setDeletingSlot(slotId);
    try {
      await CurriculumService.deleteSlot(slotId);
      reloadSlots(courseId);
    } catch {
      alert('Не удалось удалить занятие');
    } finally {
      setDeletingSlot(null);
    }
  };

  const openCreateSlot = (courseId: number) => {
    setSlotFormCourseId(courseId);
    setSelectedSlot(null);
    setShowSlotForm(true);
  };

  const openEditSlot = (slot: CurriculumSlotDto, courseId: number) => {
    setSlotFormCourseId(courseId);
    setSelectedSlot(slot);
    setShowSlotForm(true);
  };

  const slotsForCourse = (courseId: number) => slotsCache.get(courseId) || [];
  const nextPosition = (courseId: number) => {
    const s = slotsForCourse(courseId);
    return s.length > 0 ? Math.max(...s.map(x => x.position)) + 1 : 0;
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
                const isLoading = loadingCourses.has(course.id);
                const slots = slotsForCourse(course.id);

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
                        {isExpanded && slots.length > 0 && (
                          <span className="text-xs text-blue-500 font-medium bg-blue-100 px-2 py-0.5 rounded-full">
                            {slots.length} занятий
                          </span>
                        )}
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

                    {/* ── Slots panel ── */}
                    {isExpanded && (
                      <div className="border-t border-blue-100 bg-blue-50/30">
                        {/* Slots header */}
                        <div className="flex items-center justify-between px-8 py-2.5">
                          <div className="flex items-center gap-2 text-xs font-bold text-slate-500 uppercase tracking-wider">
                            <FileText size={12} />
                            Учебный план
                          </div>
                          <button
                            onClick={() => openCreateSlot(course.id)}
                            className="flex items-center gap-1 px-2.5 py-1.5 bg-blue-600 text-white text-xs font-bold rounded-lg hover:bg-blue-700 transition-colors"
                          >
                            <Plus size={12} />
                            Добавить занятие
                          </button>
                        </div>

                        {!isLoading && slots.length > 0 && (
                          <SlotsSummary slots={slots} />
                        )}

                        {isLoading ? (
                          <div className="flex items-center justify-center py-8 gap-2 text-slate-400 text-sm">
                            <Loader2 size={16} className="animate-spin" />
                            Загрузка...
                          </div>
                        ) : slots.length === 0 ? (
                          <div className="flex flex-col items-center justify-center py-10 text-slate-400">
                            <FileText size={32} className="mb-2 opacity-20" />
                            <p className="text-sm">Нет занятий в этом курсе</p>
                            <button
                              onClick={() => openCreateSlot(course.id)}
                              className="mt-3 text-xs text-blue-600 hover:text-blue-800 font-semibold flex items-center gap-1"
                            >
                              <Plus size={12} /> Добавить первое занятие
                            </button>
                          </div>
                        ) : (
                          <div className="px-8 pb-4">
                            <div className="space-y-1.5">
                              {slots.map((slot, idx) => (
                                <div
                                  key={slot.id}
                                  className="flex items-center gap-3 bg-white border border-slate-200 rounded-xl px-4 py-3 group hover:border-blue-200 hover:shadow-sm transition-all"
                                >
                                  {/* Position */}
                                  <div className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
                                    <span className="text-xs font-black text-slate-500">{idx + 1}</span>
                                  </div>

                                  {/* Kind badge */}
                                  <span className={cn(
                                    'shrink-0 text-xs font-semibold px-2 py-1 rounded-lg border',
                                    KIND_COLORS[slot.kindOfStudy] || 'bg-slate-100 text-slate-600 border-slate-200'
                                  )}>
                                    {KIND_LABELS[slot.kindOfStudy] || slot.kindOfStudy}
                                  </span>

                                  {/* Theme */}
                                  <div className="flex-1 min-w-0">
                                    {slot.themeLesson ? (
                                      <div className="flex items-baseline gap-2">
                                        <span className="text-xs font-bold text-slate-400 shrink-0">
                                          Т.{slot.themeLesson.themeNumber}
                                        </span>
                                        <span className="text-sm text-slate-700 truncate">
                                          {slot.themeLesson.title}
                                        </span>
                                      </div>
                                    ) : (
                                      <span className="text-sm text-slate-300 italic">Тема не указана</span>
                                    )}
                                  </div>

                                  {/* Auditorium requirements */}
                                  <div className="shrink-0 flex items-center gap-2 text-xs">
                                    {slot.requiredAuditorium && (
                                      <span className="flex items-center gap-1 text-red-600 bg-red-50 px-2 py-0.5 rounded">
                                        <School size={10} /> {slot.requiredAuditorium.name}
                                      </span>
                                    )}
                                    {slot.priorityAuditorium && !slot.requiredAuditorium && (
                                      <span className="flex items-center gap-1 text-blue-600 bg-blue-50 px-2 py-0.5 rounded">
                                        <School size={10} /> {slot.priorityAuditorium.name}
                                      </span>
                                    )}
                                  </div>

                                  {/* Actions */}
                                  <div className="shrink-0 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                    <button
                                      onClick={() => openEditSlot(slot, course.id)}
                                      className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                                    >
                                      <Edit2 size={13} />
                                    </button>
                                    <button
                                      onClick={() => handleDeleteSlot(slot.id, course.id)}
                                      disabled={deletingSlot === slot.id}
                                      className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                                    >
                                      {deletingSlot === slot.id
                                        ? <Loader2 size={13} className="animate-spin text-red-500" />
                                        : <Trash2 size={13} />
                                      }
                                    </button>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
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

      {showSlotForm && slotFormCourseId && (
        <CurriculumSlotFormModal
          slot={selectedSlot}
          disciplineCourseId={slotFormCourseId}
          nextPosition={nextPosition(slotFormCourseId)}
          onClose={() => { setShowSlotForm(false); setSelectedSlot(null); setSlotFormCourseId(null); }}
          onSaved={() => { setShowSlotForm(false); setSelectedSlot(null); reloadSlots(slotFormCourseId!); setSlotFormCourseId(null); }}
        />
      )}
    </>
  );
};
