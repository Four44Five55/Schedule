import React, { useEffect, useState } from 'react';
import {
  DisciplineCourseDto, CurriculumSlotDto, StudyStreamDto, EducatorDto, AssignmentDto,
  RemoveAssignmentsImpactDto
} from '../../../types/api';
import { CurriculumService } from '../../../services/apiServices';
import { Check, Plus, Users, Settings, Trash2, Edit2, X, ChevronRight, CopyMinus, Lock } from 'lucide-react';
import { cn } from '../../../utils/cn';
import { useEnums } from '../../../context/EnumContext';

const KIND_COLORS: Record<string, string> = {
  LECTURE: 'bg-violet-100 text-violet-700',
  EXAM: 'bg-red-100 text-red-700',
  LAB_WORK: 'bg-green-100 text-green-700',
  PRACTICAL_WORK: 'bg-blue-100 text-blue-700',
  COURSE_PROJECT: 'bg-teal-100 text-teal-700',
  CREDIT_WITH_GRADE: 'bg-amber-100 text-amber-700',
  CREDIT_WITHOUT_GRADE: 'bg-amber-100 text-amber-700',
  QUIZ: 'bg-amber-100 text-amber-700',
};

// Разворот дисциплин переживает уход с вкладки и F5 (как выбор периода в планировщике).
const EXPANDED_STORAGE_KEY = 'unischedule.planner.assignments.expandedCourses';

/**
 * Текст подтверждения точечного удаления. Отдельно называет закреплённые занятия: они уходят
 * тем же FK-каскадом, что и сгенерированные, но восстановить их можно только руками.
 */
const deleteWarning = (impact: RemoveAssignmentsImpactDto | null): string => {
  if (!impact || impact.placedLessons === 0) return 'Удалить назначение?';
  const locked = impact.lockedLessons > 0
    ? `\nИз них закреплённых (замок): ${impact.lockedLessons} — ручная раскладка пропадёт.`
    : '';
  return `Удалить назначение?\nВ расписании стоит занятий: ${impact.placedLessons} — они будут сняты.${locked}`;
};

interface AssignmentFormState {
  slotId: number;
  courseId: number;
  assignmentId: number | null;
  streamId: number | '';
  educatorIds: number[];
  applyAll: boolean;              // массовое назначение по охвату (только при создании)
  overwrite: boolean;            // при applyAll — перезаписывать уже назначенные слоты
  selectedSlotIds: Set<number>;  // охват массового назначения (виды/конкретные занятия)
}

// Массовое снятие «однотипных» назначений (зеркало applyAll): тот же поток+преподаватели
// снимаются с выбранных занятий. По умолчанию охват = только исходное занятие.
interface RemoveFormState {
  anchorAssignmentId: number;    // назначение, от которого открыли панель (место рендера)
  courseId: number;
  streamId: number;
  streamName: string;
  educatorIds: number[];
  educatorLabel: string;
  selectedSlotIds: Set<number>;  // охват снятия
}

export const AssignmentsTab: React.FC<{
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
  // Массовое снятие «однотипных» назначений.
  const [removeForm, setRemoveForm] = useState<RemoveFormState | null>(null);
  const [removeImpact, setRemoveImpact] = useState<RemoveAssignmentsImpactDto | null>(null);
  const [removing, setRemoving] = useState(false);
  // Развёрнутые виды в дереве охвата массового назначения (чистый UI, вне формы).
  const [expandedKinds, setExpandedKinds] = useState<Set<string>>(new Set());
  // Развёрнутые курсы-дисциплины (по courseId). По умолчанию пусто = все свёрнуты;
  // состояние восстанавливается из localStorage и сохраняется при каждом изменении.
  const [expanded, setExpanded] = useState<Set<number>>(() => {
    try {
      const saved = localStorage.getItem(EXPANDED_STORAGE_KEY);
      return saved ? new Set<number>(JSON.parse(saved)) : new Set<number>();
    } catch {
      return new Set<number>();
    }
  });

  useEffect(() => {
    localStorage.setItem(EXPANDED_STORAGE_KEY, JSON.stringify(Array.from(expanded)));
  }, [expanded]);

  const toggleExpand = (courseId: number) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(courseId)) next.delete(courseId); else next.add(courseId);
      return next;
    });
  };

  const openCreate = (slotId: number, courseId: number) => {
    setForm({ slotId, courseId, assignmentId: null, streamId: '', educatorIds: [], applyAll: false, overwrite: false, selectedSlotIds: new Set() });
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
      selectedSlotIds: new Set(),
    });
  };

  const closeForm = () => setForm(null);

  // ── Массовое назначение: охват (виды / конкретные занятия) ──
  // Слоты курса текущей формы — источник дерева охвата.
  const formCourseSlots = form ? (courseSlots.get(form.courseId) || []) : [];

  // Включение режима: инициализируем охват ВСЕМИ слотами курса (= прежнее «весь курс»).
  const toggleApplyAll = (on: boolean) => {
    setForm(prev => prev ? {
      ...prev,
      applyAll: on,
      overwrite: on ? prev.overwrite : false,
      selectedSlotIds: on
        ? new Set((courseSlots.get(prev.courseId) || []).map(s => s.id))
        : new Set(),
    } : prev);
  };

  const setSelectedSlots = (updater: (prev: Set<number>) => Set<number>) => {
    setForm(prev => prev ? { ...prev, selectedSlotIds: updater(prev.selectedSlotIds) } : prev);
  };

  const toggleSlot = (slotId: number) => setSelectedSlots(prev => {
    const next = new Set(prev);
    if (next.has(slotId)) next.delete(slotId); else next.add(slotId);
    return next;
  });

  // Чекбокс вида: снять все его слоты, если все выбраны; иначе выбрать все.
  const toggleKind = (kindSlotIds: number[], allSelected: boolean) => setSelectedSlots(prev => {
    const next = new Set(prev);
    if (allSelected) kindSlotIds.forEach(id => next.delete(id));
    else kindSlotIds.forEach(id => next.add(id));
    return next;
  });

  const toggleKindExpand = (kind: string) => setExpandedKinds(prev => {
    const next = new Set(prev);
    if (next.has(kind)) next.delete(kind); else next.add(kind);
    return next;
  });

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
        // Проставить этот поток+преподавателей на выбранный охват занятий курса (bulk на бэке).
        await CurriculumService.applyAssignmentToCourse({
          courseId: form.courseId,
          studyStreamId: form.streamId as number,
          educatorIds: form.educatorIds,
          overwrite: form.overwrite,
          slotIds: Array.from(form.selectedSlotIds),
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

  // Удаление назначения уносит его размещения FK-каскадом — в том числе закреплённые
  // (замок про каскад не знает). Поэтому сначала спрашиваем бэк, что именно потеряется.
  const handleDelete = async (id: number) => {
    const impact = await CurriculumService.getDeleteAssignmentImpact(id).catch(() => null);
    if (!window.confirm(deleteWarning(impact))) return;
    await CurriculumService.deleteAssignment(id);
    onRefresh();
  };

  // ── Массовое снятие «однотипных» назначений ──
  const openRemove = (a: AssignmentDto, courseId: number) => {
    closeForm(); // не держим обе панели открытыми
    setRemoveImpact(null);
    setRemoveForm({
      anchorAssignmentId: a.id,
      courseId,
      streamId: a.studyStream.id,
      streamName: a.studyStream.name,
      educatorIds: a.educators.map(e => e.id),
      educatorLabel: a.educators.map(e => e.name).join(', ') || '—',
      selectedSlotIds: new Set([a.curriculumSlot.id]), // по умолчанию — только это занятие
    });
  };
  const closeRemove = () => { setRemoveForm(null); setRemoveImpact(null); };

  const setRemoveSlots = (updater: (prev: Set<number>) => Set<number>) =>
    setRemoveForm(prev => prev ? { ...prev, selectedSlotIds: updater(prev.selectedSlotIds) } : prev);
  const toggleRemoveSlot = (slotId: number) => setRemoveSlots(prev => {
    const next = new Set(prev);
    if (next.has(slotId)) next.delete(slotId); else next.add(slotId);
    return next;
  });
  const toggleRemoveKind = (kindSlotIds: number[], allSelected: boolean) => setRemoveSlots(prev => {
    const next = new Set(prev);
    if (allSelected) kindSlotIds.forEach(id => next.delete(id));
    else kindSlotIds.forEach(id => next.add(id));
    return next;
  });

  // Предпросмотр последствий (счётчики с бэка) — при открытии и смене охвата.
  useEffect(() => {
    if (!removeForm) return;
    let cancelled = false;
    setRemoveImpact(null);
    CurriculumService.getRemoveAssignmentsImpact({
      courseId: removeForm.courseId,
      studyStreamId: removeForm.streamId,
      educatorIds: removeForm.educatorIds,
      slotIds: Array.from(removeForm.selectedSlotIds),
    }).then(imp => { if (!cancelled) setRemoveImpact(imp); }).catch(() => {});
    return () => { cancelled = true; };
  }, [removeForm]);

  const confirmRemove = async () => {
    if (!removeForm) return;
    // Замки названы отдельно: их снос необратим и стоит пользователю ручной раскладки.
    if ((removeImpact?.lockedLessons ?? 0) > 0 && !window.confirm(
      `Среди удаляемых занятий закреплённых (замок): ${removeImpact!.lockedLessons}.\n` +
      'Они будут сняты из расписания вместе с назначениями — ручная раскладка пропадёт. Продолжить?'
    )) return;
    setRemoving(true);
    try {
      await CurriculumService.removeAssignmentsFromCourse({
        courseId: removeForm.courseId,
        studyStreamId: removeForm.streamId,
        educatorIds: removeForm.educatorIds,
        slotIds: Array.from(removeForm.selectedSlotIds),
      });
      closeRemove();
      onRefresh();
    } finally {
      setRemoving(false);
    }
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

        // Курс с открытой формой держим развёрнутым, чтобы форма не пряталась.
        const isCollapsed = !expanded.has(courseId) && form?.courseId !== courseId;
        // Сколько занятий курса уже имеют хотя бы одно назначение (для свёрнутого вида).
        const assignedSlotCount = slots.filter(s =>
          assignments.some(a => a.curriculumSlot.id === s.id)
        ).length;
        // Цветовая маркировка счётчика: всё назначено → зелёный, ничего → красный, частично →
        // янтарный. Пустой курс (нет занятий) — нейтральный: назначать нечего.
        const assignmentTone = slots.length === 0
          ? 'text-slate-400'
          : assignedSlotCount >= slots.length
            ? 'text-emerald-600'
            : assignedSlotCount === 0
              ? 'text-red-500'
              : 'text-amber-600';

        return (
          <div key={courseId}>
            <button
              type="button"
              onClick={() => toggleExpand(courseId)}
              className="w-full px-5 py-3 bg-slate-50 flex items-center gap-3 text-left hover:bg-slate-100 transition-colors"
            >
              <ChevronRight
                size={14}
                className={cn('text-slate-400 transition-transform shrink-0', !isCollapsed && 'rotate-90')}
              />
              <div className="w-7 h-7 rounded bg-violet-100 flex items-center justify-center shrink-0">
                <span className="text-violet-700 font-bold text-xs">
                  {course.discipline.abbreviation || course.discipline.name[0]}
                </span>
              </div>
              <div className="min-w-0">
                <span className="font-semibold text-sm text-slate-800">{course.discipline.name}</span>
                <span className="ml-2 text-xs text-slate-400">
                  Семестр {course.semester}
                  {course.studyPeriod && ` · ${course.studyPeriod.name}`}
                </span>
              </div>
              <span className={cn('ml-auto text-xs font-semibold shrink-0', assignmentTone)}>
                назначено {assignedSlotCount}/{slots.length}
              </span>
            </button>

            {isCollapsed ? null : slots.length === 0 ? (
              <div className="px-5 py-4 text-xs text-slate-400 italic">Нет занятий</div>
            ) : (
              slots.map((slot, idx) => {
                const slotAssignments = assignments.filter(a => a.curriculumSlot.id === slot.id);
                const isFormOpen = form?.slotId === slot.id;
                // Потоки для выпадашки: только текущего семестра курса — семестр это заявленный
                // инвариант (StudyStream.semester ↔ DisciplineCourse.semester), поэтому поток
                // другого семестра назначать курсу и не нужно. Уже выбранный поток оставляем в
                // списке всегда (иначе правка «однотипного» назначения потеряла бы значение).
                const formStreams = isFormOpen
                  ? streams.filter(s => s.semester === course.semester || s.id === form?.streamId)
                  : [];

                return (
                  <div key={slot.id} className="border-t border-slate-50">
                    <div className="px-5 py-3">
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-baseline gap-2">
                          <span className="text-xs font-mono text-slate-400 w-6">#{idx + 1}</span>
                          <span className={cn(
                            'text-xs font-semibold px-2 py-0.5 rounded',
                            KIND_COLORS[slot.kindOfStudy] || 'bg-slate-100 text-slate-600'
                          )}>
                            {getStudyLabel(slot.kindOfStudy)}
                          </span>
                          {slot.themeLesson && (
                            <span className="text-xs font-mono font-semibold text-slate-500 shrink-0">
                              Т.{slot.themeLesson.themeNumber}
                            </span>
                          )}
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
                            <div key={a.id}>
                              <div className="flex items-center justify-between bg-slate-50 border border-slate-200 rounded px-3 py-2">
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
                                    onClick={() => removeForm?.anchorAssignmentId === a.id ? closeRemove() : openRemove(a, courseId)}
                                    title="Снять такие же (этот поток+преподаватели) в выбранных занятиях"
                                    className={cn('p-1 rounded transition-colors',
                                      removeForm?.anchorAssignmentId === a.id
                                        ? 'text-red-600 bg-red-50'
                                        : 'text-slate-400 hover:text-red-500 hover:bg-red-50')}
                                  >
                                    <CopyMinus size={13} />
                                  </button>
                                  <button
                                    onClick={() => handleDelete(a.id)}
                                    title="Удалить только это назначение"
                                    className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
                                  >
                                    <Trash2 size={13} />
                                  </button>
                                </div>
                              </div>

                              {removeForm?.anchorAssignmentId === a.id && (
                                <div className="mt-2 p-3 border border-red-200 rounded-lg bg-red-50 space-y-2">
                                  <div className="text-xs text-slate-700">
                                    Снять назначение <b>{removeForm.streamName}</b>
                                    <span className="text-slate-400"> → </span>
                                    <span className="text-slate-500">{removeForm.educatorLabel}</span> в выбранных занятиях:
                                  </div>
                                  <div className="text-[11px] text-slate-500">
                                    Выбрано занятий: <span className="font-bold text-red-600">{removeForm.selectedSlotIds.size}</span>
                                    {' '}из {(courseSlots.get(courseId) || []).length}
                                  </div>
                                  <SlotScopeTree
                                    slots={courseSlots.get(courseId) || []}
                                    selected={removeForm.selectedSlotIds}
                                    expandedKinds={expandedKinds}
                                    getStudyLabel={getStudyLabel}
                                    onToggleSlot={toggleRemoveSlot}
                                    onToggleKind={toggleRemoveKind}
                                    onToggleKindExpand={toggleKindExpand}
                                  />
                                  <div className="text-[11px] text-slate-600">
                                    {removeImpact
                                      ? `Будет удалено назначений: ${removeImpact.matchedAssignments}, из них размещённых в расписании: ${removeImpact.placedLessons}`
                                      : 'Подсчёт последствий…'}
                                  </div>
                                  {(removeImpact?.lockedLessons ?? 0) > 0 && (
                                    <div className="flex items-start gap-1.5 text-[11px] font-semibold text-red-700 bg-red-100 border border-red-200 rounded px-2 py-1.5">
                                      <Lock size={12} className="shrink-0 mt-0.5" />
                                      <span>
                                        Среди них закреплённых (замок): {removeImpact!.lockedLessons}.
                                        Они тоже будут сняты — ручная раскладка пропадёт.
                                      </span>
                                    </div>
                                  )}
                                  <div className="flex gap-2">
                                    <button
                                      onClick={confirmRemove}
                                      disabled={removing || removeForm.selectedSlotIds.size === 0 || (removeImpact?.matchedAssignments ?? 0) === 0}
                                      className="flex items-center gap-1 px-3 py-1.5 bg-red-600 text-white text-xs font-semibold rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors"
                                    >
                                      <Trash2 size={12} />
                                      {removing ? 'Удаление…' : 'Удалить в выбранных'}
                                    </button>
                                    <button
                                      onClick={closeRemove}
                                      className="px-3 py-1.5 bg-white text-slate-600 text-xs font-semibold rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
                                    >
                                      Отмена
                                    </button>
                                  </div>
                                </div>
                              )}
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
                                {formStreams.map(s => (
                                  <option key={s.id} value={s.id}>{s.name} (сем. {s.semester})</option>
                                ))}
                              </select>
                              {formStreams.length === 0 && (
                                <p className="text-xs text-amber-600 mt-1">
                                  Нет потоков семестра {course.semester} — создайте во вкладке «Потоки»
                                </p>
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
                                  onChange={e => toggleApplyAll(e.target.checked)}
                                  className="w-3.5 h-3.5"
                                />
                                <span>Применить к нескольким занятиям курса (этот поток)</span>
                              </label>
                              {form?.applyAll && (
                                <div className="pl-5 space-y-1.5">
                                  <div className="text-[11px] text-slate-500">
                                    Выбрано занятий: <span className="font-bold text-blue-600">{form.selectedSlotIds.size}</span> из {formCourseSlots.length}
                                  </div>
                                  <SlotScopeTree
                                    slots={formCourseSlots}
                                    selected={form.selectedSlotIds}
                                    expandedKinds={expandedKinds}
                                    getStudyLabel={getStudyLabel}
                                    onToggleSlot={toggleSlot}
                                    onToggleKind={toggleKind}
                                    onToggleKindExpand={toggleKindExpand}
                                  />
                                  <label className="flex items-center gap-2 text-xs text-slate-500 cursor-pointer">
                                    <input
                                      type="checkbox"
                                      checked={form?.overwrite ?? false}
                                      onChange={e => setForm(prev => prev ? { ...prev, overwrite: e.target.checked } : prev)}
                                      className="w-3.5 h-3.5"
                                    />
                                    <span>Перезаписать уже назначенные (иначе пропускаются)</span>
                                  </label>
                                </div>
                              )}
                            </div>
                          )}

                          <div className="flex gap-2">
                            <button
                              onClick={handleSave}
                              disabled={!form?.streamId || saving || (!!form?.applyAll && form.selectedSlotIds.size === 0)}
                              className="flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                            >
                              <Check size={12} />
                              {saving ? 'Сохранение...' : form?.assignmentId ? 'Обновить' : form?.applyAll ? 'Применить к выбранным' : 'Создать'}
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

/**
 * Дерево охвата массового назначения: слоты курса, сгруппированные по виду занятия.
 * Чекбокс вида (tri-state: все/часть/ни одного) переключает все свои занятия; вид можно
 * развернуть и снять отдельные. Презентационный — состояние выбора владеет форма-хост.
 */
const SlotScopeTree: React.FC<{
  slots: CurriculumSlotDto[];
  selected: Set<number>;
  expandedKinds: Set<string>;
  getStudyLabel: (kind: string) => string;
  onToggleSlot: (slotId: number) => void;
  onToggleKind: (kindSlotIds: number[], allSelected: boolean) => void;
  onToggleKindExpand: (kind: string) => void;
}> = ({ slots, selected, expandedKinds, getStudyLabel, onToggleSlot, onToggleKind, onToggleKindExpand }) => {
  // Группировка по виду с сохранением порядка плана (по минимальной позиции слота вида).
  const groups = React.useMemo(() => {
    const map = new Map<string, CurriculumSlotDto[]>();
    for (const s of slots) {
      if (!map.has(s.kindOfStudy)) map.set(s.kindOfStudy, []);
      map.get(s.kindOfStudy)!.push(s);
    }
    return Array.from(map.entries())
      .map(([kind, list]) => ({ kind, list: list.sort((a, b) => a.position - b.position) }))
      .sort((a, b) => a.list[0].position - b.list[0].position);
  }, [slots]);

  if (slots.length === 0) {
    return <div className="text-xs text-slate-400 italic px-1 py-2">В курсе нет занятий</div>;
  }

  return (
    <div className="bg-white border border-slate-200 rounded-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
      {groups.map(({ kind, list }) => {
        const ids = list.map(s => s.id);
        const selCount = ids.filter(id => selected.has(id)).length;
        const allSel = selCount === ids.length;
        const someSel = selCount > 0 && !allSel;
        const isOpen = expandedKinds.has(kind);
        return (
          <div key={kind}>
            <div className="flex items-center gap-2 px-2 py-1.5">
              <input
                type="checkbox"
                checked={allSel}
                ref={el => { if (el) el.indeterminate = someSel; }}
                onChange={() => onToggleKind(ids, allSel)}
                className="w-3.5 h-3.5 shrink-0"
              />
              <button
                type="button"
                onClick={() => onToggleKindExpand(kind)}
                className="flex items-center gap-1.5 flex-1 min-w-0 text-left"
              >
                <ChevronRight size={13} className={cn('text-slate-400 transition-transform shrink-0', isOpen && 'rotate-90')} />
                <span className={cn('text-xs font-semibold px-2 py-0.5 rounded', KIND_COLORS[kind] || 'bg-slate-100 text-slate-600')}>
                  {getStudyLabel(kind)}
                </span>
                <span className="text-[11px] text-slate-400">выбрано {selCount}/{ids.length}</span>
              </button>
            </div>
            {isOpen && (
              <div className="pl-8 pb-1 space-y-0.5">
                {list.map(s => (
                  <label key={s.id} className="flex items-center gap-2 text-xs text-slate-600 px-2 py-0.5 rounded hover:bg-slate-50 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selected.has(s.id)}
                      onChange={() => onToggleSlot(s.id)}
                      className="w-3 h-3 shrink-0"
                    />
                    <span className="font-mono text-slate-400 shrink-0">#{s.position}</span>
                    <span className="truncate">
                      {s.themeLesson
                        ? `Т.${s.themeLesson.themeNumber}${s.themeLesson.title ? ' · ' + s.themeLesson.title : ''}`
                        : 'без темы'}
                    </span>
                  </label>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
