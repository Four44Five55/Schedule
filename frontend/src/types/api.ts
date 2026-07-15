// ============ ENUMS ============
export type KindOfStudy =
    | 'LECTURE'
    | 'PRACTICAL_WORK'
    | 'LAB_WORK'
    | 'SEMINAR'
    | 'GROUP_WORK'
    | 'GROUP_EXERCISE'
    | 'QUIZ'
    | 'INDIVIDUAL_REVIEW_INTERVIEW'
    | 'CREDIT_WITH_GRADE'
    | 'CREDIT_WITHOUT_GRADE'
    | 'EXAM'
    | 'INDEPENDENT_STUDY';

export type TimeSlotPair = 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH';
export type KindOfConstraints = 'BUSINESS_TRIP' | 'VACATION' | 'EXAM_SESSION' | 'MEDICAL_CARE' | 'LIBRARY' | 'FINAL_STATE_ATTESTATION' | 'OTHER';
export type PeriodType = 'FALL_SEMESTER' | 'SPRING_SEMESTER' | 'FALL_EXAM_SESSION' | 'SPRING_EXAM_SESSION';

// ============ ENUM DTO ============
export interface EnumDto {
  value: string;
  label: string;
  abbreviation: string;
  extra?: string;
}

// ============ RESOURCES ============
export interface EducatorDto {
  id: number;
  name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
}

export interface EducatorCreateDto {
  name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
}

export interface EducatorUpdateDto {
  name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
}

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY';

// Все лейблы enum-ов загружаются с бэкенда через GET /api/enums/all
// Никакого хардкода — единый источник правды на сервере

export interface GroupDto {
  id: number;
  name: string;
  size: number;
  baseAuditorium?: { id: number; name: string };
}

export interface GroupCreateDto {
  name: string;
  size: number;
  baseAuditoriumId?: number | null;
}

export interface GroupUpdateDto {
  name: string;
  size: number;
  baseAuditoriumId?: number | null;
}

export interface AuditoriumDto {
  id: number;
  name: string;
  capacity: number;
  building: { id: number; name: string; location: { id: number; name: string } };
  purpose?: { id: number; name: string } | null;
  features: { id: number; name: string; code: string }[];
}

export interface AuditoriumCreateDto {
  name: string;
  capacity: number;
  buildingId: number;
  purposeId?: number | null;
  featureIds?: number[];
}

export interface AuditoriumUpdateDto {
  name: string;
  capacity: number;
  buildingId: number;
  purposeId?: number | null;
  featureIds?: number[];
}

export interface LocationDto {
  id: number;
  name: string;
  address?: string;
  buildings?: { id: number; name: string }[];
}

export interface LocationCreateDto {
  name: string;
  address?: string;
}

export type LocationUpdateDto = LocationCreateDto;

export interface LocationDeletionImpactDto {
  locationId: number;
  name: string;
  deletable: boolean;
  buildingCount: number;
}

export interface BuildingDto {
  id: number;
  name: string;
  location: { id: number; name: string; address?: string };
  auditoriums?: { id: number; name: string; capacity: number }[];
}

export interface BuildingCreateDto {
  name: string;
  locationId: number;
}

export type BuildingUpdateDto = BuildingCreateDto;

export interface BuildingDeletionImpactDto {
  buildingId: number;
  name: string;
  deletable: boolean;
  auditoriumCount: number;
  placedLessons: number;
  lockedLessons: number;
  slotsRequiringIt: number;
  groupsUsingAsBase: number;
}

export interface FeatureDto {
  id: number;
  name: string;
  code: string;
}

export interface AuditoriumPurposeDto {
  id: number;
  name: string;
}

export interface AuditoriumPoolDto {
  id: number;
  name: string;
  description?: string;
  auditoriums: { id: number; name: string; capacity: number }[];
}

export interface StudyPeriodDto {
  id: number;
  name: string;
  studyYear: number;
  periodType: PeriodType;
  startDate: string;
  endDate: string;
}

export interface StudyPeriodCreateDto {
  name: string;
  studyYear: number;
  periodType: PeriodType;
  startDate: string;
  endDate: string;
}

// ============ CURRICULUM ============
export interface DisciplineDto {
  id: number;
  name: string;
  abbreviation: string;
  courses: DisciplineCourseDto[];
}

export interface DisciplineCourseDto {
  id: number;
  semester: number;
  discipline: { id: number; name: string; abbreviation: string };
  studyPeriod?: StudyPeriodDto;
}

export interface DisciplineCreateDto {
  name: string;
  abbreviation?: string;
}

export interface DisciplineUpdateDto {
  name: string;
  abbreviation?: string;
}

export interface DisciplineCourseCreateDto {
  disciplineId: number;
  studyPeriodId: number;
  semester: number;
}

// Бэкенд (DisciplineCourseUpdateDto) принимает при обновлении только учебный период.
export interface DisciplineCourseUpdateDto {
  studyPeriodId: number;
}

// Предпросмотр последствий удаления курса (счётчики каскада) — для подтверждения.
export interface CourseDeletionImpactDto {
  courseId: number;
  disciplineName: string;
  semester: number;
  slots: number;
  assignments: number;
  placedLessons: number;
}

// Клон учебного плана: глубокая копия выбранных курсов в целевой период (бэкенд
// копирует слоты + сцепки; темы/назначения не копируются).
export interface CourseCloneRequestDto {
  sourceCourseIds: number[];
  targetPeriodId: number;
}

export interface CurriculumSlotDto {
  id: number;
  disciplineCourseId: number;
  position: number;
  kindOfStudy: KindOfStudy;
  themeLesson?: { id: number; themeNumber: string; title: string };
  requiredAuditorium?: { id: number; name: string };
  priorityAuditorium?: { id: number; name: string };
  allowedAuditoriumPool?: { id: number; name: string };
}

export interface CurriculumSlotCreateDto {
  disciplineCourseId: number;
  position: number;
  kindOfStudy: KindOfStudy;
  themeLessonId?: number;
  requiredAuditoriumId?: number;
  priorityAuditoriumId?: number;
  allowedAuditoriumPoolId?: number;
}

export interface CurriculumSlotUpdateDto {
  kindOfStudy: KindOfStudy; // на бэке @NotNull
  themeLessonId?: number;
  requiredAuditoriumId?: number;
  priorityAuditoriumId?: number;
  allowedAuditoriumPoolId?: number;
}

export interface ThemeLessonDto {
  id: number;
  themeNumber: string;
  title?: string;
  disciplineId: number;
  disciplineName: string;
}

export interface ThemeLessonCreateDto {
  themeNumber: string;
  title?: string;
  disciplineId: number;
}

export interface ThemeLessonUpdateDto {
  themeNumber: string;
  title?: string;
  disciplineId: number;
}

export interface SlotChainDto {
  id: number;
  slotA: { id: number; position: number; kindOfStudyName: string };
  slotB: { id: number; position: number; kindOfStudyName: string };
}

// Сцепка двух слотов: занятия идут неразрывно (slotB сразу после slotA).
export interface SlotChainCreateDto {
  slotAId: number;
  slotBId: number;
}

export interface StudyStreamDto {
  id: number;
  name: string;
  semester: number;
  groups: { id: number; name: string; size: number }[];
}

export interface StudyStreamCreateDto {
  name: string;
  semester: number;
  groupIds: number[];
}

export interface StudyStreamUpdateDto {
  name: string;
  semester: number;
  groupIds: number[];
}

export interface AssignmentDto {
  id: number;
  curriculumSlot: { id: number; position: number; kindOfStudyName: string };
  studyStream: { id: number; name: string };
  educators: { id: number; name: string }[];
}

// Создание назначений для ОДНОГО слота: бэк принимает батч (поддержка деления
// подгрупп — несколько потоков/преподавателей на один слот).
export interface AssignmentDetailDto {
  studyStreamId: number;
  educatorIds: number[];
}
export interface AssignmentCreateDto {
  curriculumSlotId: number;
  assignments: AssignmentDetailDto[];
}

// Назначить поток+преподавателей на все занятия курса. overwrite=false → пропускать
// уже назначенные на этот поток слоты; true → заменять состав преподавателей у них.
export interface ApplyAssignmentToCourseDto {
  courseId: number;
  studyStreamId: number;
  educatorIds: number[];
  overwrite: boolean;
  slotIds?: number[]; // охват: пусто → все слоты курса; иначе только эти (выбор по видам/занятиям)
}

export interface AssignmentUpdateDto {
  studyStreamId?: number;
  educatorIds?: number[];
}

// Массовое снятие «однотипных» назначений (зеркало apply-to-course): удаляются назначения
// с тем же потоком И составом преподавателей в пределах выбранных занятий (slotIds).
export interface RemoveAssignmentsFromCourseDto {
  courseId: number;
  studyStreamId: number;
  educatorIds: number[];
  slotIds?: number[]; // охват: пусто → все слоты курса; иначе только выбранные
}

// Предпросмотр последствий снятия назначений (массового и точечного).
export interface RemoveAssignmentsImpactDto {
  matchedAssignments: number;
  placedLessons: number;
  lockedLessons: number; // из них закреплённых — ручная раскладка, теряется каскадом
}

// ============ CONSTRAINTS ============
export interface EducatorConstraintDto {
  id: number;
  educatorId: number;
  educatorName: string;
  kindOfConstraint: KindOfConstraints;
  abbreviation: string;
  fullName: string;
  startDate: string;
  endDate: string;
  description?: string;
  /** Пара ограничения; отсутствует/undefined = весь день. */
  timeSlot?: TimeSlotPair;
}

export interface GroupConstraintDto {
  id: number;
  groupId: number;
  groupName: string;
  kindOfConstraint: KindOfConstraints;
  abbreviation: string;
  fullName: string;
  startDate: string;
  endDate: string;
  description?: string;
  /** Пара ограничения; отсутствует/undefined = весь день. */
  timeSlot?: TimeSlotPair;
}

export interface AuditoriumConstraintDto {
  id: number;
  auditoriumId: number;
  auditoriumName: string;
  kindOfConstraint: KindOfConstraints;
  abbreviation: string;
  fullName: string;
  startDate: string;
  endDate: string;
  description?: string;
  /** Пара ограничения; отсутствует/undefined = весь день. */
  timeSlot?: TimeSlotPair;
}

/** Любое ограничение (преподавателя, группы или аудитории) — общие поля. */
export type ConstraintDto =
    | EducatorConstraintDto
    | GroupConstraintDto
    | AuditoriumConstraintDto;

export interface EducatorConstraintCreateDto {
  educatorId: number;
  kindOfConstraint: KindOfConstraints;
  startDate: string;
  endDate: string;
  description?: string;
  /** Пара ограничения; опускается = весь день. */
  timeSlot?: TimeSlotPair;
}

export interface GroupConstraintCreateDto {
  groupId: number;
  kindOfConstraint: KindOfConstraints;
  startDate: string;
  endDate: string;
  description?: string;
  /** Пара ограничения; опускается = весь день. */
  timeSlot?: TimeSlotPair;
}

export interface AuditoriumConstraintCreateDto {
  auditoriumId: number;
  kindOfConstraint: KindOfConstraints;
  startDate: string;
  endDate: string;
  description?: string;
  /** Пара ограничения; опускается = весь день. */
  timeSlot?: TimeSlotPair;
}

// ============ SCHEDULE ============
export interface ScheduledLessonDto {
  id: number;
  date: string;
  timeSlotPair: TimeSlotPair;
  disciplineName: string;
  disciplineAbbreviation: string;
  kindOfStudy: KindOfStudy;
  kindOfStudyName: string;
  kindOfStudyAbbr: string;
  position: number;
  themeNumber?: string;
  themeTitle?: string;
  educatorIds: number[];
  educatorNames: string[];
  streamName: string;
  groupNames: string[];
  auditoriumNames: string[];
  auditoriumIds: number[];
  placementId?: string; // UUID размещения (Command Side) для переноса; есть только у загруженного из БД расписания
  curriculumSlotId?: number; // слот учебного плана — для определения сцепок (SlotChain)
  locked?: boolean; // пин: занятие закреплено вручную (распределитель его не двигает)
  source?: string; // происхождение размещения: 'GENERATED' | 'MANUAL'
}

// ============ PERIOD READINESS ============
// Готовность периода для дашборда: «всего к размещению» бэк берёт из набора генерации
// (GenerationScope.lessons), т.к. query-сторона знает только размещённое.
export interface PeriodReadinessDto {
  total: number;
  placed: number;
  unplaced: number;
}

// ============ ПРЕДПРОСМОТР ПОСЛЕДСТВИЙ УДАЛЕНИЯ ============
// Каскады БД уносят размещения молча и про `locked` ничего не знают, поэтому цену удаления
// (особенно потерю ручной раскладки) бэк называет ДО подтверждения.

// Аудитория: занятия останутся БЕЗ комнаты (вернуть её автоматически нечем).
// `deletable` — решение БЭКА (аудиторию, на которую ссылается учебный план, БД удалить не даст),
// фронт его не выводит из чисел, а показывает; slotsRequiringIt нужен только для текста.
export interface AuditoriumDeletionImpactDto {
  auditoriumId: number;
  name: string;
  deletable: boolean;
  placedLessons: number;
  lockedLessons: number;
  slotsRequiringIt: number;
  groupsUsingAsBase: number;
}

// Занятие учебного плана: каскадом уйдут его назначения и размещения (включая закреплённые).
export interface SlotDeletionImpactDto {
  slotId: number;
  position: number;
  kindOfStudy: string | null;
  assignments: number;
  placedLessons: number;
  lockedLessons: number;
}

// ============ ЗДОРОВЬЕ ПРОЕКЦИИ ============
// Сходятся ли Command Side и Query Side. Проекция асинхронна, и её сбой раньше был виден
// только в логе — то есть не виден никому: занятие просто не появлялось в сетке.
// missing > 0 → расписание отображается неполно, лечится перепроекцией сессии.
export interface ProjectionHealthDto {
  sessionId: string | null;
  placements: number;
  projected: number;
  missing: number;
}

// ============ КАЧЕСТВО РАСПИСАНИЯ ПРЕПОДАВАТЕЛЕЙ (дашборд) ============

/**
 * Качество расписания одного преподавателя за период: компактность (penalty меньше = плотнее)
 * + равномерность нагрузки (субботние пары и отклонение от среднего).
 */
export interface EducatorScheduleQualityDto {
  educatorId: number;
  educatorName: string;
  compact: boolean;          // стоит ли флаг compact_schedule
  teachingDays: number;
  totalPairs: number;
  avgPairsPerDay: number;
  singlePairDays: number;    // дней с одной парой (ось междневная)
  windowDays: number;
  windowSlots: number;       // окна внутри дня (ось внутридневная)
  excessDays: number;        // «лишние» дни сверх идеала
  penalty: number;           // сводный штраф компактности (суббота НЕ входит)
  fourthPairs: number;       // дней с занятой 4-й парой (в штраф НЕ входит — это про время дня)
  saturdayPairs: number;     // пар в субботы
  saturdayDeviation: number; // отклонение субботних пар от среднего по преподавателям
}

/** Сводка качества расписания за период (компактность — по флаговым; avgSaturday — по всем). */
export interface PeriodScheduleQualityDto {
  compactEducators: number;
  wellPacked: number;            // из флаговых: 0 окон и 0 одиночных дней
  avgPenalty: number;
  totalSinglePairDays: number;
  totalWindowSlots: number;
  avgSaturday: number;           // среднее субботних пар по всем ведущим
  educators: EducatorScheduleQualityDto[]; // все ведущие; компактные первыми, по убыванию штрафа
}

// ============ ВЫГРУЗКА РАСПИСАНИЯ В EXCEL ============

/** Перспектива выгрузки расписания: с чьей точки зрения строится файл. */
export type ExportAxis = 'GROUP' | 'EDUCATOR' | 'AUDITORIUM';

// ============ ПЛОТНОСТЬ ГРУПП 1–3 (дашборд) ============

/**
 * Плотность одной группы в парах 1–3 за период. Ёмкость считается на бэке ЧЕСТНО —
 * с учётом закрытых пар (Вс, Сб-4) и групповых ограничений; фронт капасити не считает.
 */
export interface GroupDensityDto {
  groupId: number;
  groupName: string;
  demand: number;         // всего занятий к размещению (набор генерации)
  placed13: number;       // размещено в парах 1–3 (УНИКАЛЬНЫХ занятий, не строк проекции)
  inFourth: number;       // размещено в 4-й паре
  remaining: number;      // осталось разместить = max(0, demand - placed13 - inFourth)
  capacity13: number;     // реально доступные пары 1–3 (закрытые пары + ограничения вычтены)
  free13: number;         // свободных пар 1–3 = max(0, capacity13 - placed13)
  mustGoToFourth: number; // из оставшихся столько НЕ влезет в 1–3 → придётся в 4-ю пару
}

// ============ SCHEDULE RESULT ============
export interface ScheduleResultDto {
  status: string;
  lessons: ScheduledLessonDto[];
  grid: Record<string, ScheduledLessonDto[]>; // Координатная сетка: "YYYY-MM-DD_SLOT" -> Уроки
  placedCount: number;
  unplacedCount: number;
  startDate: string;
  endDate: string;
  totalSlots: number;
  usedSlots: number;
}
