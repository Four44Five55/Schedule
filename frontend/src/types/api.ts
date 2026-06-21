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
  studyPeriod?: { id: number; name: string; studyYear: number; periodType: string };
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

export interface SlotChainDto {
  id: number;
  slotA: { id: number; position: number; kindOfStudyName: string };
  slotB: { id: number; position: number; kindOfStudyName: string };
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

export interface AssignmentCreateDto {
  curriculumSlotId: number;
  studyStreamId: number;
  educatorIds: number[];
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
  startDate: string;
  endDate: string;
  description?: string;
}

export interface GroupConstraintDto {
  id: number;
  groupId: number;
  groupName: string;
  kindOfConstraint: KindOfConstraints;
  startDate: string;
  endDate: string;
  description?: string;
}

export interface AuditoriumConstraintDto {
  id: number;
  auditoriumId: number;
  auditoriumName: string;
  kindOfConstraint: KindOfConstraints;
  startDate: string;
  endDate: string;
  description?: string;
}

/** Любое ограничение (преподавателя, группы или аудитории) — общие поля. */
export type ConstraintDto =
    | EducatorConstraintDto
    | GroupConstraintDto
    | AuditoriumConstraintDto;

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
