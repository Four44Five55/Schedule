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

export interface BuildingDto {
  id: number;
  name: string;
  location: { id: number; name: string; address?: string };
  auditoriums?: { id: number; name: string; capacity: number }[];
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
