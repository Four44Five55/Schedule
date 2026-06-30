import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  DisciplineDto, DisciplineCourseDto, CurriculumSlotDto,
  StudyStreamDto, EducatorDto, AssignmentDto, GroupDto,
  StudyPeriodDto
} from '../../../types/api';
import { CurriculumService, ResourceService } from '../../../services/apiServices';
import {
  Plus, Users, Calendar, Play, Settings, BookOpen, CalendarPlus, Copy, ShieldAlert, CalendarRange
} from 'lucide-react';
import { parseISO } from 'date-fns';
import { cn } from '../../../utils/cn';
import { DisciplineCourseFormModal } from '../../curriculum/components/DisciplineCourseFormModal';
import { ConstraintsWorkspace } from '../../constraints/components/ConstraintsWorkspace';
import { ManualPlacementWorkspace } from './ManualPlacementWorkspace';
import { CourseSelector } from './CourseSelector';
import { StreamsTab } from './StreamsTab';
import { AssignmentsTab } from './AssignmentsTab';
import { GenerationTab } from './GenerationTab';
import { PeriodFormModal } from './PeriodFormModal';
import { ClonePlanFromPeriodModal } from './ClonePlanFromPeriodModal';

type TabType = 'courses' | 'streams' | 'assignments' | 'constraints' | 'schedule' | 'generation';

const PERIOD_STORAGE_KEY = 'unischedule.planner.selectedPeriodId';

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

  // Назначения нужны и для вкладки «Назначения», и для «Ограничения» (круг участников).
  useEffect(() => {
    if (activeTab === 'assignments' || activeTab === 'constraints') {
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

  // Круг участников выбранных курсов — для ограничений в планировщике (только их сущности).
  // Преподаватели и группы выводятся из назначений; аудитории пока не скоупим (=все).
  const constraintScope = useMemo(() => {
    const educatorIds = new Set<number>();
    const groupIds = new Set<number>();
    selectedCourses.forEach(courseId => {
      (courseAssignments.get(courseId) ?? []).forEach(a => {
        a.educators.forEach(e => educatorIds.add(e.id));
        streams.find(s => s.id === a.studyStream.id)?.groups.forEach(g => groupIds.add(g.id));
      });
    });
    return { educatorIds: Array.from(educatorIds), groupIds: Array.from(groupIds) };
  }, [selectedCourses, courseAssignments, streams]);

  const tabs: { id: TabType; label: string; icon: React.ElementType }[] = [
    { id: 'courses', label: 'Курсы', icon: BookOpen },
    { id: 'streams', label: 'Потоки', icon: Users },
    { id: 'assignments', label: 'Назначения', icon: Settings },
    { id: 'constraints', label: 'Ограничения', icon: ShieldAlert },
    { id: 'schedule', label: 'Расписание', icon: CalendarRange },
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
            {activeTab === 'constraints' && (
              <div className="p-4">
                {selectedPeriod ? (
                  <ConstraintsWorkspace
                    startDate={parseISO(selectedPeriod.startDate)}
                    endDate={parseISO(selectedPeriod.endDate)}
                    scope={constraintScope}
                  />
                ) : (
                  <div className="py-16 text-center text-slate-400 text-sm">
                    <Calendar className="mx-auto mb-3 opacity-20" size={36} />
                    <p>Выберите учебный период вверху страницы</p>
                  </div>
                )}
              </div>
            )}
            {activeTab === 'schedule' && (
              <div className="p-4">
                {selectedPeriod ? (
                  selectedCourses.size > 0 ? (
                    <ManualPlacementWorkspace
                      period={selectedPeriod}
                      courseIds={Array.from(selectedCourses)}
                    />
                  ) : (
                    <div className="py-16 text-center text-slate-400 text-sm">
                      <BookOpen className="mx-auto mb-3 opacity-20" size={36} />
                      <p>Выберите курсы во вкладке «Курсы» — их занятия появятся в палитре</p>
                    </div>
                  )
                ) : (
                  <div className="py-16 text-center text-slate-400 text-sm">
                    <Calendar className="mx-auto mb-3 opacity-20" size={36} />
                    <p>Выберите учебный период вверху страницы</p>
                  </div>
                )}
              </div>
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
