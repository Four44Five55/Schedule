import React, { useState, useEffect, useCallback } from 'react';
import {
  DisciplineDto, DisciplineCourseDto, CurriculumSlotDto,
  StudyStreamDto, EducatorDto, AssignmentDto, GroupDto
} from '../../../types/api';
import { CurriculumService, ResourceService } from '../../../services/apiServices';
import {
  ChevronRight, Check, Plus, Users, Calendar, Play,
  Settings, Trash2, Edit2, X, BookOpen
} from 'lucide-react';
import { cn } from '../../../utils/cn';
import { useEnums } from '../../../context/EnumContext';

type TabType = 'courses' | 'streams' | 'assignments' | 'generation';

const KIND_COLORS: Record<string, string> = {
  LECTURE: 'bg-violet-100 text-violet-700',
  EXAM: 'bg-red-100 text-red-700',
  LAB_WORK: 'bg-green-100 text-green-700',
  PRACTICAL_WORK: 'bg-blue-100 text-blue-700',
  CREDIT_WITH_GRADE: 'bg-amber-100 text-amber-700',
  CREDIT_WITHOUT_GRADE: 'bg-amber-100 text-amber-700',
  QUIZ: 'bg-amber-100 text-amber-700',
};

interface PlannerManagerProps {
  disciplines: DisciplineDto[];
  educators: EducatorDto[];
  groups: GroupDto[];
  onGenerate: (courseIds: number[]) => void;
  isGenerating: boolean;
}

export const PlannerManager: React.FC<PlannerManagerProps> = ({ disciplines, educators, groups, onGenerate, isGenerating }) => {
  const [activeTab, setActiveTab] = useState<TabType>('courses');

  const [allCourses, setAllCourses] = useState<DisciplineCourseDto[]>([]);
  const [selectedCourses, setSelectedCourses] = useState<Set<number>>(new Set());
  const [courseSlots, setCourseSlots] = useState<Map<number, CurriculumSlotDto[]>>(new Map());
  const [courseAssignments, setCourseAssignments] = useState<Map<number, AssignmentDto[]>>(new Map());
  const [streams, setStreams] = useState<StudyStreamDto[]>([]);
  const [loading, setLoading] = useState(false);

  // courses здесь нет в App, а streams редактируются прямо в планировщике —
  // поэтому грузим только их; educators и groups приходят пропсами из App.
  useEffect(() => {
    setLoading(true);
    Promise.all([
      CurriculumService.getCourses(),
      ResourceService.getStreams(),
    ]).then(([courses, str]) => {
      setAllCourses(courses);
      setStreams(str);
    }).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (selectedCourses.size === 0) {
      setCourseSlots(new Map());
      return;
    }
    const ids = Array.from(selectedCourses);
    Promise.all(ids.map(id => CurriculumService.getSlotsByCourse(id)))
      .then(results => {
        const map = new Map<number, CurriculumSlotDto[]>();
        ids.forEach((id, i) => map.set(id, results[i]));
        setCourseSlots(map);
      });
  }, [selectedCourses]);

  const loadAssignments = useCallback(async () => {
    if (selectedCourses.size === 0) {
      setCourseAssignments(new Map());
      return;
    }
    const ids = Array.from(selectedCourses);
    const results = await Promise.all(ids.map(id => CurriculumService.getAssignmentsByCourse(id)));
    const map = new Map<number, AssignmentDto[]>();
    ids.forEach((id, i) => map.set(id, results[i]));
    setCourseAssignments(map);
  }, [selectedCourses]);

  useEffect(() => {
    if (activeTab === 'assignments') {
      loadAssignments();
    }
  }, [activeTab, loadAssignments]);

  const toggleCourse = (courseId: number) => {
    setSelectedCourses(prev => {
      const next = new Set(prev);
      if (next.has(courseId)) next.delete(courseId);
      else next.add(courseId);
      return next;
    });
  };

  const totalSlots = Array.from(courseSlots.values()).flat().length;

  const tabs: { id: TabType; label: string; icon: React.ElementType }[] = [
    { id: 'courses', label: 'Курсы', icon: BookOpen },
    { id: 'streams', label: 'Потоки', icon: Users },
    { id: 'assignments', label: 'Назначения', icon: Settings },
    { id: 'generation', label: 'Генерация', icon: Play },
  ];

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-black text-slate-900">Планировщик</h1>
          <p className="text-sm text-slate-500 mt-1">
            Формирование учебного плана, назначений и запуск генерации
          </p>
        </div>
        {selectedCourses.size > 0 && (
          <div className="text-right">
            <div className="text-xs text-slate-500">
              Курсов: <span className="font-bold text-blue-600">{selectedCourses.size}</span>
              {' · '}
              Занятий: <span className="font-bold text-blue-600">{totalSlots}</span>
            </div>
          </div>
        )}
      </div>

      <div className="flex gap-1 border-b border-slate-200">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={cn(
              'px-4 py-2 text-sm font-semibold transition-colors border-b-2 flex items-center gap-1.5',
              activeTab === id
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-slate-500 hover:text-slate-800 hover:bg-slate-50'
            )}
          >
            <Icon size={14} />
            {label}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-slate-400 text-sm animate-pulse">Загрузка...</div>
        ) : (
          <>
            {activeTab === 'courses' && (
              <CourseSelector
                disciplines={disciplines}
                allCourses={allCourses}
                selectedCourses={selectedCourses}
                courseSlots={courseSlots}
                onToggle={toggleCourse}
              />
            )}
            {activeTab === 'streams' && (
              <StreamsTab
                streams={streams}
                groups={groups}
                onStreamsChange={setStreams}
              />
            )}
            {activeTab === 'assignments' && (
              <AssignmentsTab
                selectedCourses={selectedCourses}
                allCourses={allCourses}
                courseSlots={courseSlots}
                courseAssignments={courseAssignments}
                streams={streams}
                educators={educators}
                onRefresh={loadAssignments}
              />
            )}
            {activeTab === 'generation' && (
              <GenerationTab
                selectedCourses={selectedCourses}
                totalSlots={totalSlots}
                onGenerate={onGenerate}
                isGenerating={isGenerating}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
};

// ─── CourseSelector ──────────────────────────────────────────────────────────

const CourseSelector: React.FC<{
  disciplines: DisciplineDto[];
  allCourses: DisciplineCourseDto[];
  selectedCourses: Set<number>;
  courseSlots: Map<number, CurriculumSlotDto[]>;
  onToggle: (id: number) => void;
}> = ({ disciplines, allCourses, selectedCourses, courseSlots, onToggle }) => {
  const { getStudyLabel } = useEnums();
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
                  <button
                    onClick={e => { e.stopPropagation(); toggleExpand(course.id); }}
                    className="p-1 rounded hover:bg-slate-200 transition-colors"
                  >
                    <ChevronRight
                      size={14}
                      className={cn('text-slate-400 transition-transform', isExpanded && 'rotate-90')}
                    />
                  </button>
                </div>

                {isExpanded && (
                  <div className="pl-14 pr-5 pb-3 space-y-1">
                    {slots.length === 0 ? (
                      <p className="text-xs text-slate-400 italic">Нет занятий</p>
                    ) : (
                      slots.map((slot, i) => (
                        <div key={slot.id} className="flex items-center gap-2 text-xs text-slate-600">
                          <span className="text-slate-400 font-mono w-5 text-right">{i + 1}.</span>
                          <span className={cn(
                            'px-1.5 py-0.5 rounded text-[10px] font-semibold',
                            KIND_COLORS[slot.kindOfStudy] || 'bg-slate-100 text-slate-600'
                          )}>
                            {getStudyLabel(slot.kindOfStudy)}
                          </span>
                          {slot.themeLesson && (
                            <span className="text-slate-500 truncate">{slot.themeLesson.title}</span>
                          )}
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
};

// ─── StreamsTab ───────────────────────────────────────────────────────────────

const StreamsTab: React.FC<{
  streams: StudyStreamDto[];
  groups: GroupDto[];
  onStreamsChange: (streams: StudyStreamDto[]) => void;
}> = ({ streams, groups, onStreamsChange }) => {
  const [showForm, setShowForm] = useState(false);
  const [formName, setFormName] = useState('');
  const [formSemester, setFormSemester] = useState(1);
  const [formGroupIds, setFormGroupIds] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);

  const handleCreate = async () => {
    if (!formName.trim()) return;
    setSaving(true);
    try {
      await ResourceService.createStream({ name: formName.trim(), semester: formSemester, groupIds: formGroupIds });
      const updated = await ResourceService.getStreams();
      onStreamsChange(updated);
      setShowForm(false);
      setFormName('');
      setFormGroupIds([]);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Удалить поток?')) return;
    await ResourceService.deleteStream(id);
    onStreamsChange(streams.filter(s => s.id !== id));
  };

  const toggleGroup = (id: number) => {
    setFormGroupIds(prev => prev.includes(id) ? prev.filter(g => g !== id) : [...prev, id]);
  };

  return (
    <div className="p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-bold text-slate-800 text-sm">Учебные потоки</h3>
        <button
          onClick={() => setShowForm(v => !v)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={13} />
          Новый поток
        </button>
      </div>

      {showForm && (
        <div className="mb-5 p-4 border border-blue-200 rounded-lg bg-blue-50 space-y-3">
          <h4 className="text-sm font-semibold text-blue-900">Создать поток</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Название *</label>
              <input
                value={formName}
                onChange={e => setFormName(e.target.value)}
                placeholder="Поток А"
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Семестр</label>
              <input
                type="number"
                min={1}
                max={12}
                value={formSemester}
                onChange={e => setFormSemester(Number(e.target.value))}
                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Группы</label>
            {groups.length === 0 ? (
              <p className="text-xs text-slate-400">Нет доступных групп</p>
            ) : (
              <div className="grid grid-cols-4 gap-1 max-h-32 overflow-y-auto bg-white border border-slate-200 rounded-lg p-2">
                {groups.map(g => (
                  <label key={g.id} className="flex items-center gap-1.5 text-xs cursor-pointer p-1 rounded hover:bg-blue-50">
                    <input
                      type="checkbox"
                      checked={formGroupIds.includes(g.id)}
                      onChange={() => toggleGroup(g.id)}
                      className="w-3 h-3"
                    />
                    <span>{g.name}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={!formName.trim() || saving}
              className="px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {saving ? 'Сохранение...' : 'Создать'}
            </button>
            <button
              onClick={() => { setShowForm(false); setFormName(''); setFormGroupIds([]); }}
              className="px-3 py-1.5 bg-white text-slate-600 text-xs font-semibold rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
            >
              Отмена
            </button>
          </div>
        </div>
      )}

      {streams.length === 0 ? (
        <div className="py-12 text-center text-slate-400">
          <Users className="mx-auto mb-3 opacity-20" size={36} />
          <p className="text-sm">Нет учебных потоков</p>
          <p className="text-xs mt-1 text-slate-300">Создайте поток для назначения занятий</p>
        </div>
      ) : (
        <div className="space-y-2">
          {streams.map(stream => (
            <div key={stream.id} className="border border-slate-200 rounded-lg px-4 py-3 flex items-center justify-between">
              <div>
                <div className="font-medium text-sm text-slate-800">{stream.name}</div>
                <div className="text-xs text-slate-400 mt-0.5">
                  Семестр {stream.semester}
                  {stream.groups.length > 0 && (
                    <span className="ml-1">· {stream.groups.map(g => g.name).join(', ')}</span>
                  )}
                </div>
              </div>
              <button
                onClick={() => handleDelete(stream.id)}
                className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// ─── AssignmentsTab ──────────────────────────────────────────────────────────

interface AssignmentFormState {
  slotId: number;
  assignmentId: number | null;
  streamId: number | '';
  educatorIds: number[];
}

const AssignmentsTab: React.FC<{
  selectedCourses: Set<number>;
  allCourses: DisciplineCourseDto[];
  courseSlots: Map<number, CurriculumSlotDto[]>;
  courseAssignments: Map<number, AssignmentDto[]>;
  streams: StudyStreamDto[];
  educators: EducatorDto[];
  onRefresh: () => void;
}> = ({ selectedCourses, allCourses, courseSlots, courseAssignments, streams, educators, onRefresh }) => {
  const { getStudyLabel } = useEnums();
  const [form, setForm] = useState<AssignmentFormState | null>(null);
  const [saving, setSaving] = useState(false);

  const openCreate = (slotId: number) => {
    setForm({ slotId, assignmentId: null, streamId: '', educatorIds: [] });
  };

  const openEdit = (assignment: AssignmentDto) => {
    setForm({
      slotId: assignment.curriculumSlot.id,
      assignmentId: assignment.id,
      streamId: assignment.studyStream.id,
      educatorIds: assignment.educators.map(e => e.id),
    });
  };

  const closeForm = () => setForm(null);

  const toggleEducator = (id: number) => {
    if (!form) return;
    setForm(prev => {
      if (!prev) return prev;
      const ids = prev.educatorIds.includes(id)
        ? prev.educatorIds.filter(e => e !== id)
        : [...prev.educatorIds, id];
      return { ...prev, educatorIds: ids };
    });
  };

  const handleSave = async () => {
    if (!form || form.streamId === '') return;
    setSaving(true);
    try {
      if (form.assignmentId) {
        await CurriculumService.updateAssignment(form.assignmentId, {
          studyStreamId: form.streamId as number,
          educatorIds: form.educatorIds,
        });
      } else {
        await CurriculumService.createAssignment({
          curriculumSlotId: form.slotId,
          studyStreamId: form.streamId as number,
          educatorIds: form.educatorIds,
        });
      }
      closeForm();
      onRefresh();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Удалить назначение?')) return;
    await CurriculumService.deleteAssignment(id);
    onRefresh();
  };

  if (selectedCourses.size === 0) {
    return (
      <div className="py-16 text-center text-slate-400 text-sm">
        <Settings className="mx-auto mb-3 opacity-20" size={36} />
        <p>Выберите курсы во вкладке «Курсы»</p>
      </div>
    );
  }

  return (
    <div className="divide-y divide-slate-100">
      {Array.from(selectedCourses).map(courseId => {
        const course = allCourses.find(c => c.id === courseId);
        const slots = courseSlots.get(courseId) || [];
        const assignments = courseAssignments.get(courseId) || [];

        if (!course) return null;

        return (
          <div key={courseId}>
            <div className="px-5 py-3 bg-slate-50 flex items-center gap-3">
              <div className="w-7 h-7 rounded bg-violet-100 flex items-center justify-center shrink-0">
                <span className="text-violet-700 font-bold text-xs">
                  {course.discipline.abbreviation || course.discipline.name[0]}
                </span>
              </div>
              <div>
                <span className="font-semibold text-sm text-slate-800">{course.discipline.name}</span>
                <span className="ml-2 text-xs text-slate-400">
                  Семестр {course.semester}
                  {course.studyPeriod && ` · ${course.studyPeriod.name}`}
                </span>
              </div>
            </div>

            {slots.length === 0 ? (
              <div className="px-5 py-4 text-xs text-slate-400 italic">Нет занятий</div>
            ) : (
              slots.map((slot, idx) => {
                const slotAssignments = assignments.filter(a => a.curriculumSlot.id === slot.id);
                const isFormOpen = form?.slotId === slot.id;

                return (
                  <div key={slot.id} className="border-t border-slate-50">
                    <div className="px-5 py-3">
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-mono text-slate-400 w-6">#{idx + 1}</span>
                          <span className={cn(
                            'text-xs font-semibold px-2 py-0.5 rounded',
                            KIND_COLORS[slot.kindOfStudy] || 'bg-slate-100 text-slate-600'
                          )}>
                            {getStudyLabel(slot.kindOfStudy)}
                          </span>
                          {slot.themeLesson && (
                            <span className="text-sm text-slate-600 truncate max-w-xs">
                              {slot.themeLesson.title}
                            </span>
                          )}
                        </div>
                        <button
                          onClick={() => isFormOpen ? closeForm() : openCreate(slot.id)}
                          className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 font-medium px-2 py-1 hover:bg-blue-50 rounded transition-colors"
                        >
                          {isFormOpen ? <X size={12} /> : <Plus size={12} />}
                          {isFormOpen ? 'Закрыть' : 'Добавить'}
                        </button>
                      </div>

                      {slotAssignments.length > 0 && (
                        <div className="ml-8 space-y-1 mb-2">
                          {slotAssignments.map(a => (
                            <div key={a.id} className="flex items-center justify-between bg-slate-50 border border-slate-200 rounded px-3 py-2">
                              <div className="flex items-center gap-2 text-sm min-w-0">
                                <Users size={13} className="text-slate-400 shrink-0" />
                                <span className="font-medium text-slate-700 shrink-0">{a.studyStream.name}</span>
                                {a.educators.length > 0 && (
                                  <>
                                    <span className="text-slate-300 shrink-0">→</span>
                                    <span className="text-slate-500 truncate">
                                      {a.educators.map(e => e.name).join(', ')}
                                    </span>
                                  </>
                                )}
                              </div>
                              <div className="flex items-center gap-1 ml-2 shrink-0">
                                <button
                                  onClick={() => openEdit(a)}
                                  className="p-1 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                                >
                                  <Edit2 size={13} />
                                </button>
                                <button
                                  onClick={() => handleDelete(a.id)}
                                  className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
                                >
                                  <Trash2 size={13} />
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {isFormOpen && (
                        <div className="ml-8 mt-2 p-3 border border-blue-200 rounded-lg bg-blue-50 space-y-3">
                          <div className="grid grid-cols-2 gap-3">
                            <div>
                              <label className="block text-xs font-medium text-slate-600 mb-1">Учебный поток *</label>
                              <select
                                value={form?.streamId ?? ''}
                                onChange={e => setForm(prev => prev ? { ...prev, streamId: Number(e.target.value) } : prev)}
                                className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                              >
                                <option value="">— выберите —</option>
                                {streams.map(s => (
                                  <option key={s.id} value={s.id}>{s.name} (сем. {s.semester})</option>
                                ))}
                              </select>
                              {streams.length === 0 && (
                                <p className="text-xs text-amber-600 mt-1">Создайте потоки во вкладке «Потоки»</p>
                              )}
                            </div>
                            <div>
                              <label className="block text-xs font-medium text-slate-600 mb-1">
                                Преподаватели ({form?.educatorIds.length ?? 0})
                              </label>
                              <div className="bg-white border border-slate-200 rounded-lg p-2 max-h-28 overflow-y-auto">
                                {educators.length === 0 ? (
                                  <p className="text-xs text-slate-400">Нет преподавателей</p>
                                ) : (
                                  educators.map(e => (
                                    <label key={e.id} className="flex items-center gap-2 text-xs py-0.5 cursor-pointer hover:bg-slate-50 px-1 rounded">
                                      <input
                                        type="checkbox"
                                        checked={form?.educatorIds.includes(e.id) ?? false}
                                        onChange={() => toggleEducator(e.id)}
                                        className="w-3 h-3"
                                      />
                                      <span>{e.name}</span>
                                    </label>
                                  ))
                                )}
                              </div>
                            </div>
                          </div>
                          <div className="flex gap-2">
                            <button
                              onClick={handleSave}
                              disabled={!form?.streamId || saving}
                              className="flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                            >
                              <Check size={12} />
                              {saving ? 'Сохранение...' : form?.assignmentId ? 'Обновить' : 'Создать'}
                            </button>
                            <button
                              onClick={closeForm}
                              className="px-3 py-1.5 bg-white text-slate-600 text-xs font-semibold rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
                            >
                              Отмена
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        );
      })}
    </div>
  );
};

// ─── GenerationTab ───────────────────────────────────────────────────────────

const GenerationTab: React.FC<{
  selectedCourses: Set<number>;
  totalSlots: number;
  onGenerate: (courseIds: number[]) => void;
  isGenerating: boolean;
}> = ({ selectedCourses, totalSlots, onGenerate, isGenerating }) => {
  if (selectedCourses.size === 0) {
    return (
      <div className="py-16 text-center text-slate-400 text-sm">
        <Calendar className="mx-auto mb-3 opacity-20" size={36} />
        <p>Выберите курсы во вкладке «Курсы»</p>
      </div>
    );
  }

  return (
    <div className="p-5 space-y-5">
      <div className="p-4 bg-blue-50 border border-blue-100 rounded-xl">
        <h4 className="font-semibold text-blue-900 text-sm mb-3">Параметры генерации</h4>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div className="bg-white rounded-lg p-3 border border-blue-100 text-center">
            <div className="text-2xl font-black text-blue-600">{selectedCourses.size}</div>
            <div className="text-xs text-blue-500 mt-1">курсов выбрано</div>
          </div>
          <div className="bg-white rounded-lg p-3 border border-blue-100 text-center">
            <div className="text-2xl font-black text-blue-600">{totalSlots}</div>
            <div className="text-xs text-blue-500 mt-1">занятий всего</div>
          </div>
        </div>
      </div>

      <div className="text-xs text-slate-500 space-y-1.5">
        <p className="font-semibold text-slate-700">Перед генерацией убедитесь:</p>
        <ul className="space-y-1 pl-3">
          <li className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-300 shrink-0" />
            Для каждого занятия созданы назначения (вкладка «Назначения»)
          </li>
          <li className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-300 shrink-0" />
            Преподаватели и аудитории добавлены в систему
          </li>
          <li className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-300 shrink-0" />
            Активный учебный период настроен
          </li>
        </ul>
      </div>

      <button
        onClick={() => onGenerate(Array.from(selectedCourses))}
        disabled={isGenerating}
        className="w-full py-3 bg-blue-600 text-white font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors text-sm"
      >
        <Play size={16} />
        {isGenerating ? 'Генерация расписания...' : 'Сгенерировать расписание'}
      </button>

      {isGenerating && (
        <p className="text-xs text-center text-slate-400">
          После завершения вы будете перенаправлены в раздел «Расписание»
        </p>
      )}
    </div>
  );
};
