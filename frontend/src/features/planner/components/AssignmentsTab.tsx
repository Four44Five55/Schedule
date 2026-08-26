import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  DisciplineCourseDto, CurriculumSlotDto, StudyStreamDto, EducatorDto, AssignmentDto,
  RemoveAssignmentsImpactDto
} from '../../../types/api';
import { CurriculumService } from '../../../services/apiServices';
import { Check, Plus, Users, Settings, Trash2, Edit2, X, ChevronRight, CopyMinus, Lock, LifeBuoy, Search } from 'lucide-react';
import { cn } from '../../../utils/cn';
import { useEnums } from '../../../context/EnumContext';
import { errorMessage } from '../../../services/apiError';
import { useToast } from '../../../context/ToastContext';

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
const deleteWarning = (impact: RemoveAssignmentsImpactDto | null, impactFailed = false): string => {
  if (impactFailed) {
    return 'Удалить назначение?\n\nПроверить, сколько занятий стоит в расписании, не удалось '
      + '(сервер не ответил). Вместе с назначением они будут сняты — включая закреплённые вручную.';
  }
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
  /**
   * Потоки, которым достаётся ОДИН состав преподавателей. Список, а не одно значение: потоки
   * бывают разные при одной и той же комбинации ведущих, и повторять ввод для каждого — то же
   * самое несколько раз. Схема не меняется: назначения по-прежнему заводятся по одному на поток
   * (`UNIQUE (curriculum_slot_id, study_stream_id)`), просто заводятся списком за один заход.
   *
   * ⚠️ **При правке всегда ровно один.** Назначение — это и есть пара «занятие + поток»;
   * «поменять поток на два» означало бы разделить строку надвое, а не изменить её. Размножать
   * потоки поэтому можно только при создании — см. развилку в разметке.
   */
  streamIds: number[];
  educatorIds: number[];
  // Запасные (И-22): числятся за дисциплиной, но занятий не ведут. Распределение их не видит —
  // они не занимают время и не попадают в свою сетку; печатаются только в подвале бланка.
  reserveEducatorIds: number[];
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
  const toast = useToast();
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
    setSkipped([]);
    setForm({ slotId, courseId, assignmentId: null, streamIds: [], educatorIds: [], reserveEducatorIds: [], applyAll: false, overwrite: false, selectedSlotIds: new Set() });
  };

  const openEdit = (assignment: AssignmentDto, courseId: number) => {
    setSkipped([]);
    setForm({
      slotId: assignment.curriculumSlot.id,
      courseId,
      assignmentId: assignment.id,
      streamIds: [assignment.studyStream.id],
      educatorIds: assignment.educators.map(e => e.id),
      reserveEducatorIds: assignment.reserveEducators.map(e => e.id),
      applyAll: false,
      overwrite: false,
      selectedSlotIds: new Set(),
    });
  };

  const closeForm = () => { setForm(null); setSkipped([]); setSaveError(null); };

  /**
   * Почему сохранять нельзя — строкой, а не булевым флагом. Кнопка, погашенная без причины,
   * это своя ошибка: человек видит, что нажать нельзя, и не видит, чего не хватает.
   *
   * **Пустой состав ведущих — не каприз формы, а зеркало бэка:** `educatorIds` помечен
   * `@NotEmpty` во всех трёх путях записи (создание, правка, массовое назначение). Раньше кнопка
   * оставалась активной, запрос уходил и возвращался 400 — а показать его было некому, и нажатие
   * выглядело как «ничего не произошло».
   *
   * ⚠️ Запасные (И-22) ведущего не заменяют: они занятий не ведут, и назначение из одних
   * запасных означало бы занятие, которое некому проводить.
   */
  const saveBlocker: string | null = !form ? null
    : form.streamIds.length === 0 ? 'Выберите учебный поток'
    : form.educatorIds.length === 0 ? 'Выберите хотя бы одного преподавателя: запасные занятий не ведут'
    : form.applyAll && form.selectedSlotIds.size === 0 ? 'Выберите хотя бы одно занятие курса'
    : null;

  /**
   * Потоки, которые бэк отказался заводить, потому что назначение у них на этом занятии уже есть.
   * В обычной работе список пуст — такие потоки в выборе недоступны; сюда попадает только то,
   * что разошлось с соседней вкладкой. Молчать нельзя: «создал пятерым, создалось четверым».
   */
  const [skipped, setSkipped] = useState<string[]>([]);

  /**
   * Отказ бэка. До этого у `handleSave` не было `catch` вовсе: 400 (пустой состав), 409 и 500
   * гасились в `finally`, и панель просто оставалась открытой — то есть неотличимо от «нажатие
   * не сработало». Логи `apiClient` выводятся только в dev-сборке, так что в проде следа не было.
   */
  const [saveError, setSaveError] = useState<string | null>(null);

  /**
   * Варианты выбора для открытой формы. Считаются **один раз на форму**, а не внутри цикла по
   * занятиям: форма всегда одна, а цикл проходит по всем занятиям всех развёрнутых курсов —
   * прежний расчёт по месту повторял одну и ту же работу на каждой строке плана.
   */
  const formCourse = form ? allCourses.find(c => c.id === form.courseId) ?? null : null;

  /** Потоки, у которых назначение на ЭТОМ занятии уже есть: второго ему завести некуда (UNIQUE). */
  const takenStreamIds = useMemo(() => new Set(
    form
      ? (courseAssignments.get(form.courseId) ?? [])
          .filter(a => a.curriculumSlot.id === form.slotId)
          .map(a => a.studyStream.id)
      : [],
  ), [form, courseAssignments]);

  /**
   * Потоки семестра курса. Уже выбранный остаётся в списке при любом семестре — иначе правка
   * «однотипного» назначения потеряла бы значение.
   */
  const streamOptions = useMemo<PickerOption[]>(() => {
    if (!form || !formCourse) return [];
    return streams
      .filter(st => st.semester === formCourse.semester || form.streamIds.includes(st.id))
      .map(st => ({
        id: st.id,
        name: st.name,
        // Пометка остаётся видимой и на выбранном потоке: она предупреждение, а не блокировка,
        // и в массовом режиме как раз подсказывает, кому понадобится «перезаписать».
        note: takenStreamIds.has(st.id) ? 'уже назначен' : `сем. ${st.semester}`,
        noteTone: takenStreamIds.has(st.id) ? 'warn' : 'muted',
      }));
  }, [form, formCourse, streams, takenStreamIds]);

  /**
   * Занятость потока запрещает выбор только для ОДНОГО занятия. В охвате нескольких она разная
   * у разных занятий, и решает её «перезаписать», а не этот список.
   */
  const blockedStreamIds = useMemo(
    () => (form && !form.applyAll ? Array.from(takenStreamIds) : []),
    [form, takenStreamIds],
  );

  /** Преподаватели — общий список для обеих ролей; чужая роль передаётся отдельно, `blockedIds`. */
  const educatorOptions = useMemo<PickerOption[]>(
    () => educators.map(e => ({
      id: e.id,
      name: e.name,
      // Кафедра — единственное, чем различаются однофамильцы; по ней же идёт поиск.
      note: e.orgUnitName ?? undefined,
      title: e.titleLine || e.name,
    })),
    [educators],
  );

  const toggleStream = useCallback((id: number) => {
    setForm(prev => {
      if (!prev) return prev;
      const ids = prev.streamIds.includes(id)
        ? prev.streamIds.filter(s => s !== id)
        : [...prev.streamIds, id];
      return { ...prev, streamIds: ids };
    });
  }, []);

  // Панель раскрывается ВНУТРИ списка занятий курса, а не поверх него. На нижних занятиях
  // длинного курса она открывалась бы за краем экрана — кнопка «Добавить» нажата, а формы не
  // видно. `block: 'nearest'` не дёргает страницу, когда панель и так на виду.
  const formRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (form) formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }, [form?.slotId, form?.assignmentId]);

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

  const toggleEducator = useCallback((id: number) => {
    setForm(prev => {
      if (!prev) return prev;
      const ids = prev.educatorIds.includes(id)
        ? prev.educatorIds.filter(e => e !== id)
        : [...prev.educatorIds, id];
      return { ...prev, educatorIds: ids };
    });
  }, []);

  // Запасной (И-22). Роли взаимоисключающие: человек либо ведёт занятие и занимает время, либо
  // числится и не занимает. Бэк пересечение отклоняет (400), поэтому в списках чужая роль просто
  // недоступна — правило видно до отправки, а не в тексте ошибки.
  const toggleReserveEducator = useCallback((id: number) => {
    setForm(prev => {
      if (!prev) return prev;
      const ids = prev.reserveEducatorIds.includes(id)
        ? prev.reserveEducatorIds.filter(e => e !== id)
        : [...prev.reserveEducatorIds, id];
      return { ...prev, reserveEducatorIds: ids };
    });
  }, []);

  const handleSave = async () => {
    if (!form || saveBlocker) return;
    setSaving(true);
    setSkipped([]);
    setSaveError(null);
    try {
      if (form.assignmentId) {
        // Правка — всегда один поток: назначение и есть пара «занятие + поток».
        await CurriculumService.updateAssignment(form.assignmentId, {
          studyStreamId: form.streamIds[0],
          educatorIds: form.educatorIds,
          reserveEducatorIds: form.reserveEducatorIds,
        });
      } else if (form.applyAll) {
        // Потоки × выбранный охват занятий курса — одним вызовом (bulk на бэке).
        await CurriculumService.applyAssignmentToCourse({
          courseId: form.courseId,
          studyStreamIds: form.streamIds,
          educatorIds: form.educatorIds,
          reserveEducatorIds: form.reserveEducatorIds,
          overwrite: form.overwrite,
          slotIds: Array.from(form.selectedSlotIds),
        });
      } else {
        // По назначению на поток, все — в одной транзакции: эндпоинт принимает список с самого
        // начала, отдельного «массового создания» заводить не пришлось.
        const created = await CurriculumService.createAssignment({
          curriculumSlotId: form.slotId,
          assignments: form.streamIds.map(streamId => ({
            studyStreamId: streamId,
            educatorIds: form.educatorIds,
            reserveEducatorIds: form.reserveEducatorIds,
          })),
        });
        // Бэк молча пропускает потоки, у которых назначение на этом занятии уже есть. Сверяем
        // заказанное с созданным и оставляем форму открытой, если совпало не всё.
        const done = new Set(created.map(a => a.studyStream.id));
        const missed = form.streamIds.filter(id => !done.has(id));
        if (missed.length > 0) {
          setSkipped(missed.map(id => streams.find(s => s.id === id)?.name ?? `поток #${id}`));
          // Созданные из выбора убираем: форма остаётся открытой ради непонятного остатка,
          // и показывать в ней уже сделанное значило бы звать нажать «Создать» ещё раз.
          setForm(prev => prev ? { ...prev, streamIds: missed } : prev);
          onRefresh();
          return;
        }
      }
      closeForm();
      onRefresh();
    } catch (err: any) {
      console.error('Ошибка сохранения назначения:', err);
      setSaveError(errorMessage(err, 'Не удалось сохранить. Попробуйте ещё раз.'));
    } finally {
      setSaving(false);
    }
  };

  // Удаление назначения уносит его размещения FK-каскадом — в том числе закреплённые
  // (замок про каскад не знает). Поэтому сначала спрашиваем бэк, что именно потеряется.
  const handleDelete = async (id: number) => {
    // Цена удаления: не узнали — предупреждаем об этом прямо в вопросе (deleteWarning), а не
    // подсовываем короткое «Удалить назначение?», за которым может стоять снос раскладки.
    let impact: RemoveAssignmentsImpactDto | null = null;
    let impactFailed = false;
    try {
      impact = await CurriculumService.getDeleteAssignmentImpact(id);
    } catch {
      impactFailed = true;
    }
    if (!window.confirm(deleteWarning(impact, impactFailed))) return;
    try {
      await CurriculumService.deleteAssignment(id);
      onRefresh();
    } catch (e) {
      // Раньше отказ здесь не ловился вовсе: обещание падало в никуда, список не менялся,
      // и назначение выглядело удалённым до первого обновления страницы.
      toast.failure(e, 'Не удалось удалить назначение.');
    }
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

  // Esc закрывает открытую панель. Поиск внутри пикера гасит своё Esc сам (preventDefault),
  // пока в нём есть запрос: первое нажатие возвращает полный список, и только второе закрывает
  // форму — иначе человек, сузивший список, терял бы вместе с фильтром весь набранный выбор.
  useEffect(() => {
    if (!form && !removeForm) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || e.defaultPrevented) return;
      if (form) closeForm(); else closeRemove();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [form, removeForm]);

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
    }).then(imp => { if (!cancelled) setRemoveImpact(imp); })
      .catch((e) => {
        // Панель массового снятия ждёт число «будет снято N» и без него показывает прочерк —
        // выглядит как «снимать нечего», хотя ответа просто не пришло.
        if (!cancelled) toast.failure(e, 'Не удалось посчитать, что будет снято.');
      });
    return () => { cancelled = true; };
  }, [removeForm, toast]);

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
                                  {a.reserveEducators.length > 0 && (
                                    <span
                                      title="Запасные: занятий не ведут, время не занимают; печатаются в подвале бланка"
                                      className="flex items-center gap-1 text-xs text-slate-400 truncate shrink-0"
                                    >
                                      <LifeBuoy size={12} className="shrink-0" />
                                      {a.reserveEducators.map(e => e.name).join(', ')}
                                    </span>
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
                        <div ref={formRef} className="ml-0 sm:ml-8 mt-2 p-3 border border-blue-200 rounded-lg bg-blue-50 space-y-3">
                          {/* Три состава — в одну линию: значения короткие (номер потока, фамилия),
                              ширины хватает, а решения между колонками связаны и принимаются
                              сравнением. Роли ведущего и запасного взаимоисключающие (И-22) —
                              врозь их пришлось бы сверять прокруткой; поток же задаёт, кому этот
                              состав достанется. Ниже `lg` колонок две, на узком — одна: три по
                              ~220 px начали бы резать фамилию вместе с кафедрой. */}
                          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                            {/* Правка — прежний `select`: назначение и есть пара «занятие + поток»,
                                размножать поток у существующей строки нечем. Создание — список:
                                потоки разные, а состав ведущих у них часто один и тот же. */}
                            {form?.assignmentId ? (
                              <div className="min-w-0">
                                <label className="block text-xs font-medium text-slate-600 mb-1">Учебный поток *</label>
                                <select
                                  value={form.streamIds[0] ?? ''}
                                  onChange={e => setForm(prev => prev
                                    ? { ...prev, streamIds: e.target.value ? [Number(e.target.value)] : [] }
                                    : prev)}
                                  className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                                >
                                  <option value="">— выберите —</option>
                                  {streamOptions.map(o => (
                                    <option key={o.id} value={o.id}>{o.name} ({o.note})</option>
                                  ))}
                                </select>
                              </div>
                            ) : (
                              <MultiPicker
                                label="Учебные потоки *"
                                hint="— один состав на всех выбранных"
                                options={streamOptions}
                                selectedIds={form?.streamIds ?? []}
                                blockedIds={blockedStreamIds}
                                blockedTitle="У этого занятия поток уже назначен — состав меняется правкой (✎)"
                                searchPlaceholder="Поиск потока"
                                emptyText={`Нет потоков семестра ${formCourse?.semester ?? ''} — создайте во вкладке «Потоки»`}
                                accent="blue"
                                onToggle={toggleStream}
                              />
                            )}

                            <MultiPicker
                              label="Преподаватели"
                              options={educatorOptions}
                              selectedIds={form?.educatorIds ?? []}
                              blockedIds={form?.reserveEducatorIds ?? []}
                              blockedTitle="Уже числится запасным на это занятие"
                              searchPlaceholder="Поиск по фамилии или кафедре"
                              emptyText="Нет преподавателей"
                              accent="blue"
                              onToggle={toggleEducator}
                            />

                            {/* Запасные (И-22): числятся за дисциплиной, но занятий не ведут.
                                Распределение их не видит — время они не занимают и в своей сетке
                                занятий не получают; видны только здесь и в подвале бланка. */}
                            <MultiPicker
                              label="Запасные"
                              hint="— не ведут занятий, но попадают в подвал бланка"
                              options={educatorOptions}
                              selectedIds={form?.reserveEducatorIds ?? []}
                              blockedIds={form?.educatorIds ?? []}
                              blockedTitle="Уже назначен ведущим на это занятие"
                              searchPlaceholder="Поиск по фамилии или кафедре"
                              emptyText="Нет преподавателей"
                              accent="amber"
                              onToggle={toggleReserveEducator}
                            />
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
                                <span>Применить к нескольким занятиям курса (выбранные потоки)</span>
                              </label>
                              {form?.applyAll && (
                                <div className="pl-5 space-y-1.5">
                                  <div className="text-[11px] text-slate-500">
                                    Выбрано занятий: <span className="font-bold text-blue-600">{form.selectedSlotIds.size}</span> из {formCourseSlots.length}
                                    {form.streamIds.length > 1 && (
                                      <> · охват: <span className="font-bold text-blue-600">{form.selectedSlotIds.size * form.streamIds.length}</span> пар «поток × занятие»</>
                                    )}
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

                          {/* Пропущенные потоки. В обычной работе не появляется — такие потоки
                              в списке недоступны; сработает, если соседняя вкладка назначила их
                              раньше. Форма остаётся открытой: остальное уже создано, и человек
                              видит, что именно не доехало. */}
                          {skipped.length > 0 && (
                            <div className="flex items-start gap-1.5 text-[11px] text-amber-800 bg-amber-100 border border-amber-200 rounded px-2 py-1.5">
                              <Lock size={12} className="shrink-0 mt-0.5" />
                              <span>
                                Уже были назначены на это занятие, поэтому пропущены:{' '}
                                <b>{skipped.join(', ')}</b>. Состав им меняет правка (✎).
                              </span>
                            </div>
                          )}

                          {saveError && (
                            <div className="flex items-start gap-1.5 text-[11px] text-red-800 bg-red-100 border border-red-200 rounded px-2 py-1.5">
                              <X size={12} className="shrink-0 mt-0.5" />
                              <span>{saveError}</span>
                            </div>
                          )}

                          <div className="flex flex-wrap items-center gap-2">
                            <button
                              onClick={handleSave}
                              disabled={!!saveBlocker || saving}
                              className="flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                            >
                              <Check size={12} />
                              {saving ? 'Сохранение...'
                                : form?.assignmentId ? 'Обновить'
                                : form?.applyAll ? 'Применить к выбранным'
                                : (form?.streamIds.length ?? 0) > 1 ? `Создать (${form!.streamIds.length})`
                                : 'Создать'}
                            </button>
                            <button
                              onClick={closeForm}
                              className="px-3 py-1.5 bg-white text-slate-600 text-xs font-semibold rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
                            >
                              Отмена
                            </button>
                            {/* Причина рядом с погашенной кнопкой, а не вместо неё: кнопку видно,
                                и видно, чего ей не хватает. */}
                            {saveBlocker && !saving && (
                              <span className="text-[11px] text-amber-700">{saveBlocker}</span>
                            )}
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


/** Оформление выбранных: ведущие и потоки — синие (как вся панель), запасные — янтарные. */
const PICKER_CHIP: Record<'blue' | 'amber', string> = {
  blue: 'bg-blue-100 text-blue-800 hover:bg-blue-200',
  amber: 'bg-amber-100 text-amber-800 hover:bg-amber-200',
};

/**
 * Один вариант выбора. Доменных полей здесь нет намеренно: пикер одинаково обслуживает потоки и
 * преподавателей, и знать, что такое кафедра или семестр, ему незачем — хост переводит свои
 * сущности в эти четыре поля и владеет смыслом.
 */
interface PickerOption {
  id: number;
  /** Основная подпись; по ней же и по `note` идёт поиск. */
  name: string;
  /** Приписка справа: кафедра, семестр, «уже назначен». */
  note?: string;
  /** `warn` — янтарным: предупреждение, которое видно и на выбранной строке. */
  noteTone?: 'muted' | 'warn';
  /** Подсказка при наведении, если она богаче подписи (у преподавателя — строка с регалиями). */
  title?: string;
}

/**
 * Выбор нескольких вариантов: поиск, выбранные — чипами над списком, занятые — с причиной.
 *
 * **Почему один компонент на потоки и на людей.** Как только у потоков появился поиск, оба списка
 * стали одинаковыми по взаимодействию — поиск, чипы, прокрутка, счётчик, запрет с причиной. Две
 * копии этого разошлись бы на первой же правке; поэтому пикер презентационный, а смысл вариантов
 * остаётся у хоста (`PickerOption`). Третьему списку менять компонент не придётся.
 *
 * **Строка поиска живёт внутри пикера.** Это состояние одного поля, а не формы: подними его в
 * `AssignmentsTab` — и каждое нажатие клавиши перерисовывало бы все развёрнутые курсы со слотами,
 * назначениями и деревьями охвата. Здесь перерисовывается один список.
 *
 * **Ищем по подписи и приписке, но не по `title`:** у преподавателя `title` начинается со звания
 * («п-к Иванов И.И.»), и поиск по нему спотыкался бы о приставку — то же решение, что в разделе
 * «Преподаватели». Приписка в запросе полезна там, где фамилия неуникальна и людей различает
 * только кафедра.
 *
 * **Выбранные показаны чипами, а не подъёмом наверх.** Сортировка «выбранные первыми» переставляла
 * бы строки под курсором в момент клика; к тому же поиск прятал бы уже выбранных, и состав
 * пришлось бы держать в голове.
 *
 * **Занятый вариант блокируется, только если он ещё не выбран.** Иначе смена режима (охват одного
 * занятия ↔ нескольких) оставляла бы галочку, которую нечем снять.
 */
const MultiPicker: React.FC<{
  label: string;
  hint?: string;
  options: PickerOption[];
  selectedIds: number[];
  /** Занятые встречной ролью: показаны, но недоступны — правило видно до отправки, а не в 400. */
  blockedIds: number[];
  blockedTitle: string;
  searchPlaceholder: string;
  emptyText: string;
  accent: 'blue' | 'amber';
  onToggle: (id: number) => void;
}> = ({ label, hint, options, selectedIds, blockedIds, blockedTitle,
        searchPlaceholder, emptyText, accent, onToggle }) => {
  const [query, setQuery] = useState('');

  const selected = useMemo(() => new Set(selectedIds), [selectedIds]);
  const blocked = useMemo(() => new Set(blockedIds), [blockedIds]);

  const trimmed = query.trim();
  const visible = useMemo(() => {
    const q = trimmed.toLowerCase();
    if (!q) return options;
    return options.filter(o =>
      o.name.toLowerCase().includes(q) || (o.note ?? '').toLowerCase().includes(q));
  }, [options, trimmed]);

  // Чипы строим из ПОЛНОГО списка, а не из отфильтрованного: выбранный не должен исчезать
  // из состава оттого, что не подошёл под запрос.
  const chosen = useMemo(() => options.filter(o => selected.has(o.id)), [options, selected]);

  return (
    <div className="min-w-0">
      <label className="block text-xs font-medium text-slate-600 mb-1 truncate">
        {label} ({selectedIds.length})
        {hint && <span className="ml-1.5 font-normal text-slate-400">{hint}</span>}
      </label>

      <div className="bg-white border border-slate-200 rounded-lg">
        {chosen.length > 0 && (
          <div className="flex flex-wrap gap-1 p-1.5 border-b border-slate-100">
            {chosen.map(o => (
              <button
                key={o.id}
                type="button"
                onClick={() => onToggle(o.id)}
                title={`${o.title ?? o.name} — убрать`}
                className={cn(
                  'flex items-center gap-1 max-w-full px-1.5 py-0.5 rounded text-[11px] font-medium transition-colors',
                  PICKER_CHIP[accent],
                )}
              >
                <span className="truncate">{o.name}</span>
                <X size={10} className="shrink-0" />
              </button>
            ))}
          </div>
        )}

        <div className="relative border-b border-slate-100">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            // Esc при непустом запросе гасит только запрос — панель закрывается следующим.
            // `preventDefault` (а не `stopPropagation`) потому, что слушатель формы висит на
            // window: до него событие дойдёт в любом случае, а `defaultPrevented` переживёт путь.
            onKeyDown={e => { if (e.key === 'Escape' && query) { e.preventDefault(); setQuery(''); } }}
            placeholder={searchPlaceholder}
            className="w-full pl-7 pr-6 py-1.5 text-xs bg-transparent outline-none placeholder:text-slate-400"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery('')}
              title="Очистить"
              className="absolute right-1.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X size={12} />
            </button>
          )}
        </div>

        {/* Высота списка растёт вместе с экраном: фиксированные 112 px давали 9 строк на любом. */}
        <div className="max-h-40 md:max-h-52 lg:max-h-64 overflow-y-auto p-1.5">
          {options.length === 0 ? (
            <p className="text-xs text-amber-600 px-1 py-0.5">{emptyText}</p>
          ) : visible.length === 0 ? (
            <p className="text-xs text-slate-400 px-1 py-0.5">Ничего не нашлось по «{trimmed}»</p>
          ) : (
            visible.map(o => {
              // Свой выбор снять можно всегда — блокировка не должна запирать уже отмеченное.
              const isChosen = selected.has(o.id);
              const isBlocked = blocked.has(o.id) && !isChosen;
              return (
                <label
                  key={o.id}
                  title={isBlocked ? blockedTitle : (o.title ?? o.name)}
                  className={cn(
                    'flex items-center gap-2 text-xs py-0.5 px-1 rounded',
                    isBlocked ? 'text-slate-300 cursor-not-allowed' : 'cursor-pointer hover:bg-slate-50',
                  )}
                >
                  <input
                    type="checkbox"
                    disabled={isBlocked}
                    checked={isChosen}
                    onChange={() => onToggle(o.id)}
                    className="w-3 h-3 shrink-0"
                  />
                  <span className="truncate">{o.name}</span>
                  {o.note && (
                    <span className={cn(
                      'ml-auto shrink-0 max-w-[45%] truncate text-[10px]',
                      isBlocked ? 'text-slate-300' : o.noteTone === 'warn' ? 'text-amber-600' : 'text-slate-400',
                    )}>
                      {o.note}
                    </span>
                  )}
                </label>
              );
            })
          )}
        </div>

        {trimmed && visible.length > 0 && (
          <div className="px-2 py-1 border-t border-slate-100 text-[10px] text-slate-400">
            показано {visible.length} из {options.length}
          </div>
        )}
      </div>
    </div>
  );
};
