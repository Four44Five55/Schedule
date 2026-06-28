import React, { useState, useEffect, useCallback } from 'react';
import {
  DisciplineDto, DisciplineCourseDto, CurriculumSlotDto,
  StudyStreamDto, EducatorDto, AssignmentDto, GroupDto,
  StudyPeriodDto, StudyPeriodCreateDto, PeriodType
} from '../../../types/api';
import { CurriculumService, ResourceService } from '../../../services/apiServices';
import {
  ChevronRight, Check, Plus, Users, Calendar, Play,
  Settings, Trash2, Edit2, X, BookOpen, CalendarPlus, Loader2, Copy
} from 'lucide-react';
import { cn } from '../../../utils/cn';
import { useEnums } from '../../../context/EnumContext';
import { DisciplineCourseFormModal } from '../../curriculum/components/DisciplineCourseFormModal';
import { CurriculumPlanEditor } from '../../curriculum/components/CurriculumPlanEditor';
import { courseSlotSource } from '../../curriculum/planSource';

type TabType = 'courses' | 'streams' | 'assignments' | 'generation';

const PERIOD_TYPE_OPTIONS: { value: PeriodType; label: string }[] = [
  { value: 'FALL_SEMESTER', label: 'Осенний семестр' },
  { value: 'SPRING_SEMESTER', label: 'Весенний семестр' },
  { value: 'FALL_EXAM_SESSION', label: 'Осенняя сессия' },
  { value: 'SPRING_EXAM_SESSION', label: 'Весенняя сессия' },
];

const PERIOD_STORAGE_KEY = 'unischedule.planner.selectedPeriodId';

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
  onGenerate: (courseIds: number[], period: StudyPeriodDto) => void;
  isGenerating: boolean;
}

export const PlannerManager: React.FC<PlannerManagerProps> = ({ disciplines, educators, groups, onGenerate, isGenerating }) => {
  const [activeTab, setActiveTab] = useState<TabType>('courses');

  // Учебный период — первичный контекст планировщика: он задаёт набор курсов и даты.
  // Выбор переживает уход с вкладки и F5 (localStorage), как фильтры в др. разделах.
  const [periods, setPeriods] = useState<StudyPeriodDto[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(() => {
    const saved = localStorage.getItem(PERIOD_STORAGE_KEY);
    return saved ? Number(saved) : null;
  });
  const [showPeriodForm, setShowPeriodForm] = useState(false);
  const [showCourseForm, setShowCourseForm] = useState(false);
  const [showCloneModal, setShowCloneModal] = useState(false);

  const [allCourses, setAllCourses] = useState<DisciplineCourseDto[]>([]);
  const [selectedCourses, setSelectedCourses] = useState<Set<number>>(new Set());
  const [courseSlots, setCourseSlots] = useState<Map<number, CurriculumSlotDto[]>>(new Map());
  const [courseAssignments, setCourseAssignments] = useState<Map<number, AssignmentDto[]>>(new Map());
  const [streams, setStreams] = useState<StudyStreamDto[]>([]);
  const [loading, setLoading] = useState(false);

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId) ?? null;

  // Периоды + потоки грузим один раз. Сохранённый выбор имеет приоритет (если такой
  // период ещё существует); иначе — активный период, иначе первый из списка.
  useEffect(() => {
    Promise.all([
      ResourceService.getStudyPeriods(),
      ResourceService.getActiveStudyPeriod(),
      ResourceService.getStreams(),
    ]).then(([allPeriods, active, str]) => {
      setPeriods(allPeriods);
      setStreams(str);
      setSelectedPeriodId(prev =>
        prev != null && allPeriods.some(p => p.id === prev)
          ? prev
          : (active?.id ?? allPeriods[0]?.id ?? null)
      );
    });
  }, []);

  // Персист выбранного периода между вкладками и перезагрузками.
  useEffect(() => {
    if (selectedPeriodId != null) {
      localStorage.setItem(PERIOD_STORAGE_KEY, String(selectedPeriodId));
    } else {
      localStorage.removeItem(PERIOD_STORAGE_KEY);
    }
  }, [selectedPeriodId]);

  // Курсы зависят от выбранного периода: меняется период — перезагружаем курсы и
  // сбрасываем выбор (курсы другого периода не должны «прилипать»).
  useEffect(() => {
    if (selectedPeriodId == null) {
      setAllCourses([]);
      setSelectedCourses(new Set());
      return;
    }
    setLoading(true);
    setSelectedCourses(new Set());
    CurriculumService.getCourses(selectedPeriodId)
      .then(setAllCourses)
      .finally(() => setLoading(false));
  }, [selectedPeriodId]);

  const handlePeriodCreated = (created: StudyPeriodDto) => {
    setPeriods(prev => [...prev, created]);
    setSelectedPeriodId(created.id);
    setShowPeriodForm(false);
  };

  // Обновить список курсов периода БЕЗ сброса выбора (в отличие от смены периода).
  const reloadCourses = useCallback(() => {
    if (selectedPeriodId != null) {
      CurriculumService.getCourses(selectedPeriodId).then(setAllCourses);
    }
  }, [selectedPeriodId]);

  const handleCourseSaved = () => {
    setShowCourseForm(false);
    reloadCourses();
  };

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

  // Редактор плана владеет своими слотами; после правки сообщает сюда, чтобы наши
  // производные данные (счётчик «Занятий N», вкладка «Назначения») не отставали.
  // Точечно перечитываем слоты курса в map — но только если он выбран (иначе в счётчик
  // попали бы слоты невыбранного курса).
  const reloadCourseSlots = useCallback((courseId: number) => {
    if (!selectedCourses.has(courseId)) return;
    CurriculumService.getSlotsByCourse(courseId).then(slots => {
      setCourseSlots(prev => new Map(prev).set(courseId, slots));
    });
  }, [selectedCourses]);

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

      {/* Учебный период — контекст всего планировщика */}
      <div className="flex items-center gap-3 bg-white rounded-xl border border-slate-200 p-3">
        <Calendar size={16} className="text-blue-600 shrink-0" />
        <label className="text-xs font-semibold text-slate-600 shrink-0">Учебный период</label>
        <select
          value={selectedPeriodId ?? ''}
          onChange={e => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
          className="flex-1 text-sm border border-slate-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">— выберите период —</option>
          {periods.map(p => (
            <option key={p.id} value={p.id}>{p.name} ({p.studyYear})</option>
          ))}
        </select>
        <button
          onClick={() => setShowPeriodForm(true)}
          className="flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shrink-0"
        >
          <CalendarPlus size={14} /> Новый период
        </button>
      </div>

      {showPeriodForm && (
        <PeriodFormModal onClose={() => setShowPeriodForm(false)} onCreated={handlePeriodCreated} />
      )}

      {showCourseForm && selectedPeriodId != null && (
        <DisciplineCourseFormModal
          course={null}
          lockedPeriodId={selectedPeriodId}
          onClose={() => setShowCourseForm(false)}
          onSaved={handleCourseSaved}
        />
      )}

      {showCloneModal && selectedPeriod && (
        <ClonePlanFromPeriodModal
          periods={periods}
          targetPeriod={selectedPeriod}
          onClose={() => setShowCloneModal(false)}
          onCloned={() => { setShowCloneModal(false); reloadCourses(); }}
        />
      )}

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
              <div>
                <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                  <span className="text-xs text-slate-500">
                    {selectedPeriod ? `Курсы периода «${selectedPeriod.name}»` : 'Сначала выберите учебный период'}
                  </span>
                  {/* Наполнение плана: ручное добавление курса либо клон плана из другого
                      периода (глубокая копия курсов со слотами и сцепками на бэке). */}
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setShowCloneModal(true)}
                      disabled={!selectedPeriod}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-white text-blue-600 border border-blue-200 text-xs font-semibold rounded-lg hover:bg-blue-50 disabled:opacity-50 transition-colors"
                    >
                      <Copy size={13} /> Скопировать из периода
                    </button>
                    <button
                      onClick={() => setShowCourseForm(true)}
                      disabled={!selectedPeriod}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                    >
                      <Plus size={13} /> Новый курс
                    </button>
                  </div>
                </div>
                <CourseSelector
                  disciplines={disciplines}
                  allCourses={allCourses}
                  selectedCourses={selectedCourses}
                  courseSlots={courseSlots}
                  onToggle={toggleCourse}
                  onCourseChanged={reloadCourseSlots}
                />
              </div>
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
                selectedPeriod={selectedPeriod}
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
  onCourseChanged: (courseId: number) => void;
}> = ({ disciplines, allCourses, selectedCourses, courseSlots, onToggle, onCourseChanged }) => {
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
  courseId: number;
  assignmentId: number | null;
  streamId: number | '';
  educatorIds: number[];
  applyAll: boolean;    // применить ко всем занятиям курса (только при создании)
  overwrite: boolean;   // при applyAll — перезаписывать уже назначенные слоты
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

  const openCreate = (slotId: number, courseId: number) => {
    setForm({ slotId, courseId, assignmentId: null, streamId: '', educatorIds: [], applyAll: false, overwrite: false });
  };

  const openEdit = (assignment: AssignmentDto, courseId: number) => {
    setForm({
      slotId: assignment.curriculumSlot.id,
      courseId,
      assignmentId: assignment.id,
      streamId: assignment.studyStream.id,
      educatorIds: assignment.educators.map(e => e.id),
      applyAll: false,
      overwrite: false,
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
      } else if (form.applyAll) {
        // Проставить этот поток+преподавателей на все занятия курса (bulk на бэке).
        await CurriculumService.applyAssignmentToCourse({
          courseId: form.courseId,
          studyStreamId: form.streamId as number,
          educatorIds: form.educatorIds,
          overwrite: form.overwrite,
        });
      } else {
        await CurriculumService.createAssignment({
          curriculumSlotId: form.slotId,
          assignments: [{ studyStreamId: form.streamId as number, educatorIds: form.educatorIds }],
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
                          onClick={() => isFormOpen ? closeForm() : openCreate(slot.id, courseId)}
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
                                  onClick={() => openEdit(a, courseId)}
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

                          {/* Массовое назначение — только при создании (не при правке). */}
                          {!form?.assignmentId && (
                            <div className="space-y-1.5">
                              <label className="flex items-center gap-2 text-xs text-slate-700 cursor-pointer">
                                <input
                                  type="checkbox"
                                  checked={form?.applyAll ?? false}
                                  onChange={e => setForm(prev => prev ? { ...prev, applyAll: e.target.checked, overwrite: e.target.checked ? prev.overwrite : false } : prev)}
                                  className="w-3.5 h-3.5"
                                />
                                <span>Применить ко всем занятиям курса (этот поток)</span>
                              </label>
                              {form?.applyAll && (
                                <label className="flex items-center gap-2 text-xs text-slate-500 cursor-pointer pl-5">
                                  <input
                                    type="checkbox"
                                    checked={form?.overwrite ?? false}
                                    onChange={e => setForm(prev => prev ? { ...prev, overwrite: e.target.checked } : prev)}
                                    className="w-3.5 h-3.5"
                                  />
                                  <span>Перезаписать уже назначенные (иначе пропускаются)</span>
                                </label>
                              )}
                            </div>
                          )}

                          <div className="flex gap-2">
                            <button
                              onClick={handleSave}
                              disabled={!form?.streamId || saving}
                              className="flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                            >
                              <Check size={12} />
                              {saving ? 'Сохранение...' : form?.assignmentId ? 'Обновить' : form?.applyAll ? 'Применить к курсу' : 'Создать'}
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
  selectedPeriod: StudyPeriodDto | null;
  onGenerate: (courseIds: number[], period: StudyPeriodDto) => void;
  isGenerating: boolean;
}> = ({ selectedCourses, totalSlots, selectedPeriod, onGenerate, isGenerating }) => {
  if (!selectedPeriod) {
    return (
      <div className="py-16 text-center text-slate-400 text-sm">
        <Calendar className="mx-auto mb-3 opacity-20" size={36} />
        <p>Выберите учебный период вверху страницы</p>
      </div>
    );
  }
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
        <div className="mb-3 text-sm text-blue-900">
          Период: <span className="font-bold">{selectedPeriod.name}</span>
          <span className="text-blue-500"> · {selectedPeriod.startDate} — {selectedPeriod.endDate}</span>
        </div>
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
        onClick={() => onGenerate(Array.from(selectedCourses), selectedPeriod)}
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

// ─── ClonePlanFromPeriodModal ────────────────────────────────────────────────
// Наполнение текущего периода копией курсов из другого периода. Глубокую копию
// (слоты + сцепки + ремап) делает бэкенд; здесь только выбор источника и курсов.

const ClonePlanFromPeriodModal: React.FC<{
  periods: StudyPeriodDto[];
  targetPeriod: StudyPeriodDto;
  onClose: () => void;
  onCloned: () => void;
}> = ({ periods, targetPeriod, onClose, onCloned }) => {
  const sourcePeriods = periods.filter(p => p.id !== targetPeriod.id);

  const [sourcePeriodId, setSourcePeriodId] = useState<number | null>(null);
  const [sourceCourses, setSourceCourses] = useState<DisciplineCourseDto[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (sourcePeriodId == null) {
      setSourceCourses([]);
      setSelectedIds(new Set());
      return;
    }
    setLoading(true);
    setSelectedIds(new Set());
    CurriculumService.getCourses(sourcePeriodId)
      .then(setSourceCourses)
      .finally(() => setLoading(false));
  }, [sourcePeriodId]);

  const toggle = (id: number) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const allSelected = sourceCourses.length > 0 && selectedIds.size === sourceCourses.length;
  const toggleAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(sourceCourses.map(c => c.id)));
  };

  const handleClone = async () => {
    if (selectedIds.size === 0) return;
    setSaving(true);
    setError(null);
    try {
      await CurriculumService.cloneCourses({
        sourceCourseIds: Array.from(selectedIds),
        targetPeriodId: targetPeriod.id,
      });
      onCloned();
    } catch (err: any) {
      const data = err?.response?.data;
      // 409 — курс уже есть в целевом периоде (операция атомарна, ничего не скопировано).
      setError(typeof data === 'string' ? data : (data?.message || 'Не удалось скопировать курсы.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden max-h-[90vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg"><Copy size={20} className="text-blue-600" /></div>
            <div>
              <h2 className="text-lg font-black text-slate-900">Скопировать из периода</h2>
              <p className="text-xs text-slate-500 font-medium mt-0.5">
                В период «{targetPeriod.name}»
              </p>
            </div>
          </div>
          <button onClick={onClose} disabled={saving} className="p-1 hover:bg-slate-200 rounded-lg transition-colors">
            <X size={20} className="text-slate-500" />
          </button>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
          )}

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Период-источник *</label>
            <select
              value={sourcePeriodId ?? ''}
              onChange={e => setSourcePeriodId(e.target.value ? Number(e.target.value) : null)}
              className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              <option value="">— выберите период —</option>
              {sourcePeriods.map(p => (
                <option key={p.id} value={p.id}>{p.name} ({p.studyYear})</option>
              ))}
            </select>
          </div>

          {sourcePeriodId != null && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-bold text-slate-600 uppercase tracking-wider">Курсы для копирования</label>
                {sourceCourses.length > 0 && (
                  <button onClick={toggleAll} className="text-xs text-blue-600 hover:text-blue-800 font-semibold">
                    {allSelected ? 'Снять все' : 'Выбрать все'}
                  </button>
                )}
              </div>

              {loading ? (
                <div className="flex items-center justify-center py-8 gap-2 text-slate-400 text-sm">
                  <Loader2 size={16} className="animate-spin" /> Загрузка...
                </div>
              ) : sourceCourses.length === 0 ? (
                <p className="text-sm text-slate-400 italic py-4 text-center">В этом периоде нет курсов</p>
              ) : (
                <div className="border border-slate-200 rounded-xl divide-y divide-slate-100 max-h-64 overflow-y-auto">
                  {sourceCourses.map(course => {
                    const isSelected = selectedIds.has(course.id);
                    return (
                      <label
                        key={course.id}
                        className={cn(
                          'flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors',
                          isSelected ? 'bg-blue-50' : 'hover:bg-slate-50'
                        )}
                      >
                        <input type="checkbox" checked={isSelected} onChange={() => toggle(course.id)} className="w-4 h-4" />
                        <span className="text-sm font-medium text-slate-800">{course.discipline.name}</span>
                        <span className="text-xs text-slate-400">Семестр {course.semester}</span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3 shrink-0">
          <button
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
          >
            Отмена
          </button>
          <button
            onClick={handleClone}
            disabled={selectedIds.size === 0 || saving}
            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {saving ? <><Loader2 size={16} className="animate-spin" /> Копирование...</> : <><Copy size={16} /> Скопировать ({selectedIds.size})</>}
          </button>
        </div>
      </div>
    </div>
  );
};

// ─── PeriodFormModal ─────────────────────────────────────────────────────────

const PeriodFormModal: React.FC<{
  onClose: () => void;
  onCreated: (period: StudyPeriodDto) => void;
}> = ({ onClose, onCreated }) => {
  const [name, setName] = useState('');
  const [studyYear, setStudyYear] = useState(new Date().getFullYear());
  const [periodType, setPeriodType] = useState<PeriodType>('FALL_SEMESTER');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSave = name.trim() && startDate && endDate && !saving;

  const handleSave = async () => {
    if (!canSave) return;
    if (startDate > endDate) {
      setError('Дата начала не может быть позже даты окончания.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload: StudyPeriodCreateDto = {
        name: name.trim(), studyYear, periodType, startDate, endDate,
      };
      const created = await ResourceService.createStudyPeriod(payload);
      onCreated(created);
    } catch (err: any) {
      const data = err?.response?.data;
      setError(typeof data === 'string' ? data : (data?.message || 'Не удалось создать период.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden" onClick={e => e.stopPropagation()}>
        <div className="px-6 py-4 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg"><CalendarPlus size={20} className="text-blue-600" /></div>
            <h2 className="text-lg font-black text-slate-900">Новый учебный период</h2>
          </div>
          <button onClick={onClose} disabled={saving} className="p-1 hover:bg-slate-200 rounded-lg transition-colors">
            <X size={20} className="text-slate-500" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
          )}

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Название *</label>
            <input
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Осенний семестр 2026/2027"
              className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Учебный год *</label>
              <input
                type="number"
                min={2020}
                value={studyYear}
                onChange={e => setStudyYear(Number(e.target.value))}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Тип периода *</label>
              <select
                value={periodType}
                onChange={e => setPeriodType(e.target.value as PeriodType)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              >
                {PERIOD_TYPE_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Дата начала *</label>
              <input
                type="date"
                value={startDate}
                onChange={e => setStartDate(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">Дата окончания *</label>
              <input
                type="date"
                value={endDate}
                onChange={e => setEndDate(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>
          </div>
        </div>

        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex gap-3">
          <button
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-bold text-sm hover:bg-white transition-colors disabled:opacity-50"
          >
            Отмена
          </button>
          <button
            onClick={handleSave}
            disabled={!canSave}
            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold text-sm hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {saving ? <><Loader2 size={16} className="animate-spin" /> Сохранение...</> : <><Check size={16} /> Создать</>}
          </button>
        </div>
      </div>
    </div>
  );
};
