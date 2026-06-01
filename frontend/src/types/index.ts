// ====================================================================
// TypeScript-типы, полностью соответствующие Java DTO.
// ====================================================================

// ── ENUMS ──────────────────────────────────────────────────────────
// TimeSlotPair (4 пары)
export type TimeSlotPair = 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH';
export const TIME_SLOT_LABELS: Record<TimeSlotPair, {label:string;time:string}> = {
  FIRST:  {label:'1 пара', time:'9:00–10:35'},
  SECOND: {label:'2 пара', time:'10:55–12:30'},
  THIRD:  {label:'3 пара', time:'12:50–14:25'},
  FOURTH: {label:'4 пара', time:'16:20–17:55'},
};
export const ALL_SLOTS: TimeSlotPair[] = ['FIRST','SECOND','THIRD','FOURTH'];

// DayOfWeek (7 дней, включая воскресенье)
export type DayOfWeek = 'MONDAY'|'TUESDAY'|'WEDNESDAY'|'THURSDAY'|'FRIDAY'|'SATURDAY'|'SUNDAY';
export const DAY_LABELS: Record<DayOfWeek,string> = {
  MONDAY:'Пн', TUESDAY:'Вт', WEDNESDAY:'Ср', THURSDAY:'Чт',
  FRIDAY:'Пт', SATURDAY:'Сб', SUNDAY:'Вс',
};
export const DAY_FULL_LABELS: Record<DayOfWeek,string> = {
  MONDAY:'Понедельник', TUESDAY:'Вторник', WEDNESDAY:'Среда',
  THURSDAY:'Четверг', FRIDAY:'Пятница', SATURDAY:'Суббота', SUNDAY:'Воскресенье',
};

// KindOfStudy (12 значений)
export type KindOfStudy =
  | 'LECTURE'|'PRACTICAL_WORK'|'LAB_WORK'|'SEMINAR'
  | 'GROUP_WORK'|'GROUP_EXERCISE'|'QUIZ'
  | 'INDIVIDUAL_REVIEW_INTERVIEW'|'CREDIT_WITH_GRADE'
  | 'CREDIT_WITHOUT_GRADE'|'EXAM'|'INDEPENDENT_STUDY';
export const KIND_OF_STUDY_LABELS: Record<KindOfStudy,string> = {
  LECTURE:'Лекция', PRACTICAL_WORK:'Практическое занятие', LAB_WORK:'Лабораторная работа',
  SEMINAR:'Семинар', GROUP_WORK:'Групповое занятие', GROUP_EXERCISE:'Групповое упражнение',
  QUIZ:'Контрольная работа', INDIVIDUAL_REVIEW_INTERVIEW:'Индивидуальное контрольное собеседование',
  CREDIT_WITH_GRADE:'Зачет с оценкой', CREDIT_WITHOUT_GRADE:'Зачет без оценки',
  EXAM:'Экзамен', INDEPENDENT_STUDY:'Самостоятельная работа',
};
export const KIND_OF_STUDY_ABBR: Record<KindOfStudy,string> = {
  LECTURE:'Л', PRACTICAL_WORK:'ПЗ', LAB_WORK:'ЛР',
  SEMINAR:'С', GROUP_WORK:'ГЗ', GROUP_EXERCISE:'ГУ',
  QUIZ:'КР', INDIVIDUAL_REVIEW_INTERVIEW:'ИКС',
  CREDIT_WITH_GRADE:'ЗО', CREDIT_WITHOUT_GRADE:'ЗЧ',
  EXAM:'ЭКЗ', INDEPENDENT_STUDY:'СР',
};

// KindOfConstraints (7 значений)
export type KindOfConstraints = 'BUSINESS_TRIP'|'VACATION'|'EXAM_SESSION'|'MEDICAL_CARE'|'LIBRARY'|'FINAL_STATE_ATTESTATION'|'OTHER';
export const CONSTRAINT_LABELS: Record<KindOfConstraints,string> = {
  BUSINESS_TRIP:'Командировка', VACATION:'Отпуск', EXAM_SESSION:'Экзаменационная сессия',
  MEDICAL_CARE:'УМО', LIBRARY:'Библиотека', FINAL_STATE_ATTESTATION:'ГИА', OTHER:'Другое',
};
export const CONSTRAINT_ABBR: Record<KindOfConstraints,string> = {
  BUSINESS_TRIP:'Ком', VACATION:'Отп', EXAM_SESSION:'ЭкзС',
  MEDICAL_CARE:'УМО', LIBRARY:'Биб', FINAL_STATE_ATTESTATION:'ГИА', OTHER:'ДВО',
};

// PeriodType (4 значения)
export type PeriodType = 'FALL_SEMESTER'|'SPRING_SEMESTER'|'FALL_EXAM_SESSION'|'SPRING_EXAM_SESSION';
export const PERIOD_TYPE_LABELS: Record<PeriodType,string> = {
  FALL_SEMESTER:'Осенний семестр', SPRING_SEMESTER:'Весенний семестр',
  FALL_EXAM_SESSION:'Осенняя сессия', SPRING_EXAM_SESSION:'Весенняя сессия',
};

// compactSchedule – привычные имена дней
export const RUS_DAY_NAMES = ['Понедельник','Вторник','Среда','Четверг','Пятница','Суббота','Воскресенье'];

// ── DTO (отвечают Java-рекордам) ──────────────────────────────────

export interface LocationDto {
  id: number; name: string; address?: string;
  buildings?: { id: number; name: string }[];
}
export interface LocationCreateDto { name: string; address?: string; }

export interface BuildingDto {
  id: number; name: string;
  location: { id: number; name: string; address?: string };
  auditoriums?: { id: number; name: string; capacity: number }[];
}
export interface BuildingCreateDto { name: string; locationId: number; }

export interface FeatureDto { id: number; name: string; code: string; }
export interface FeatureCreateDto { name: string; code: string; }

export interface AuditoriumPurposeDto { id: number; name: string; }
export interface AuditoriumPurposeCreateDto { name: string; }

export interface AuditoriumDto {
  id: number; name: string; capacity: number;
  building: { id: number; name: string; location: { id: number; name: string } };
  purpose?: AuditoriumPurposeDto;
  features: FeatureDto[];
}
export interface AuditoriumCreateDto { name: string; capacity: number; buildingId: number; purposeId?: number; featureIds?: number[]; }

export interface EducatorDto {
  id: number; name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
}
export interface EducatorCreateDto { name: string; preferredDays?: DayOfWeek[]; preferredTimeSlots?: TimeSlotPair[]; compactSchedule?: boolean; }

export interface GroupDto { id: number; name: string; size: number; baseAuditorium?: { id: number; name: string }; }
export interface GroupCreateDto { name: string; size: number; baseAuditoriumId?: number; }

export interface DisciplineBriefDto { id: number; name: string; abbreviation: string; }
export interface DisciplineDto { id: number; name: string; abbreviation: string; courses?: DisciplineCourseDto[]; }

export interface DisciplineCourseDto { id: number; semester: number; discipline: DisciplineBriefDto; }
export interface DisciplineCourseCreateDto { disciplineId: number; studyPeriodId: number; semester: number; }

export interface ThemeLessonDto { id: number; themeNumber: string; title?: string; disciplineId: number; disciplineName?: string; }
export interface ThemeLessonCreateDto { themeNumber: string; title?: string; disciplineId: number; }

export interface CurriculumSlotDto {
  id: number; disciplineCourseId: number; position: number;
  kindOfStudy: KindOfStudy;
  themeLesson?: { id: number; themeNumber: string; title?: string };
  requiredAuditorium?: { id: number; name: string };
  priorityAuditorium?: { id: number; name: string };
  allowedAuditoriumPool?: { id: number; name: string };
}
export interface CurriculumSlotCreateDto {
  disciplineCourseId: number; position: number; kindOfStudy: KindOfStudy;
  themeLessonId?: number; requiredAuditoriumId?: number;
  priorityAuditoriumId?: number; allowedAuditoriumPoolId?: number;
}

export interface StudyPeriodDto { id: number; name: string; studyYear: number; periodType: PeriodType; startDate: string; endDate: string; }
export interface StudyPeriodCreateDto { name: string; studyYear: number; periodType: PeriodType; startDate: string; endDate: string; }

export interface StudyStreamDto {
  id: number; name: string; semester: number;
  groups: { id: number; name: string; size: number }[];
}
export interface StudyStreamCreateDto { name: string; semester: number; groupIds: number[]; }

export interface AuditoriumPoolDto {
  id: number; name: string; description?: string;
  auditoriums: { id: number; name: string; capacity: number }[];
}
export interface AuditoriumPoolCreateDto { name: string; description?: string; auditoriumIds?: number[]; }

export interface AssignmentDto {
  id: number;
  curriculumSlot: { id: number; position: number; kindOfStudyName: string };
  studyStream: { id: number; name: string };
  educators: { id: number; name: string }[];
}
export interface AssignmentCreateDto {
  curriculumSlotId: number;
  assignments: { studyStreamId: number; educatorIds: number[] }[];
}
export interface AssignmentUpdateDto { studyStreamId: number; educatorIds: number[]; }

export interface SlotChainDto {
  id: number;
  slotA: { id: number; position: number; kindOfStudyName: string };
  slotB: { id: number; position: number; kindOfStudyName: string };
}
export interface SlotChainCreateDto { slotAId: number; slotBId: number; }

// ── Constraint-сущности (пока напрямую entity, без DTO) ──────────
export interface EducatorConstraintDto {
  id: number;
  educatorId?: number; educator?: { id: number; name: string };
  kindOfConstraint: KindOfConstraints;
  startDate: string; endDate: string; description?: string;
}
export interface GroupConstraintDto {
  id: number;
  groupId?: number; group?: { id: number; name: string };
  kindOfConstraint: KindOfConstraints;
  startDate: string; endDate: string; description?: string;
}
export interface AuditoriumConstraintDto {
  id: number;
  auditoriumId?: number; auditorium?: { id: number; name: string };
  kindOfConstraint: KindOfConstraints;
  startDate: string; endDate: string; description?: string;
}

// ── Update/Create DTO (недостающие) ──────────────────────────────
export interface EducatorUpdateDto { name: string; preferredDays?: DayOfWeek[]; preferredTimeSlots?: TimeSlotPair[]; compactSchedule?: boolean; }
export interface GroupUpdateDto { name: string; size: number; baseAuditoriumId?: number; }
export interface AuditoriumUpdateDto { name: string; capacity: number; buildingId: number; purposeId?: number; featureIds?: number[]; }
export interface DisciplineCreateDto { name: string; abbreviation?: string; }
export interface DisciplineUpdateDto { name: string; abbreviation?: string; }
export interface StudyStreamUpdateDto { name: string; semester: number; groupIds: number[]; }

// ── DTO для результата генерации ────────────────────────────────
export interface ScheduledLessonDto {
  id: number;
  date: string;
  timeSlotPair: string;
  disciplineName: string;
  disciplineAbbreviation: string;
  kindOfStudy: string;
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
}

export interface ScheduleResultDto {
  status: string;
  lessons: ScheduledLessonDto[];
  placedCount: number;
  unplacedCount: number;
  startDate: string;
  endDate: string;
  totalSlots: number;
  usedSlots: number;
}

// ── Навигация ─────────────────────────────────────────────────────
export type Page =
  | 'dashboard'|'schedule'|'educators'|'groups'|'auditoriums'
  | 'disciplines'|'discipline-courses'|'curriculum-slots'
  | 'streams'|'assignments'|'slot-chains'
  | 'educator-constraints'|'group-constraints'|'auditorium-constraints'
  | 'locations'|'buildings'|'features'|'purposes'|'pools'
  | 'study-periods'|'settings';
