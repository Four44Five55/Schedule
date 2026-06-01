import type {
  LocationDto, BuildingDto, FeatureDto, AuditoriumPurposeDto, AuditoriumDto,
  EducatorDto, GroupDto, DisciplineDto, ThemeLessonDto, StudyPeriodDto,
  DisciplineCourseDto, CurriculumSlotDto, StudyStreamDto, AuditoriumPoolDto,
  AssignmentDto, EducatorConstraintDto, GroupConstraintDto, AuditoriumConstraintDto,
} from '../types';

// ── Locations ──────────────────────────────────────
export const mockLocations: LocationDto[] = [
  { id:1, name:'Учебный городок «Северный»', address:'ул. Университетская, 1' },
  { id:2, name:'Корпус на Набережной', address:'Набережная, 45' },
];
export const mockBuildings: BuildingDto[] = [
  { id:1, name:'Корпус №1', location:{id:1,name:'Учебный городок «Северный»',address:'ул. Университетская, 1'} },
  { id:2, name:'Корпус №2', location:{id:1,name:'Учебный городок «Северный»',address:'ул. Университетская, 1'} },
  { id:3, name:'Корпус №3', location:{id:2,name:'Корпус на Набережной',address:'Набережная, 45'} },
];
export const mockPurposes: AuditoriumPurposeDto[] = [
  { id:1, name:'Лекционная' },{ id:2, name:'Практическая' },{ id:3, name:'Лаборатория' },
  { id:4, name:'Компьютерный класс' },
];
export const mockFeatures: FeatureDto[] = [
  { id:1, name:'Проектор', code:'PROJECTOR' },{ id:2, name:'Маркерная доска', code:'WHITEBOARD' },
  { id:3, name:'Компьютеры', code:'COMPUTERS' },{ id:4, name:'Интерактивная доска', code:'SMARTBOARD' },
];
export const mockPools: AuditoriumPoolDto[] = [
  { id:1, name:'Большие лекционные', description:'от 60 мест', auditoriums:[] },
  { id:2, name:'Компьютерные классы', auditoriums:[] },
];
export const mockAuditoriums: AuditoriumDto[] = [
  { id:1, name:'101', capacity:120, building:{id:1,name:'Корпус №1',location:{id:1,name:'Северный'}}, purpose:mockPurposes[0], features:[mockFeatures[0]] },
  { id:2, name:'201', capacity:30, building:{id:1,name:'Корпус №1',location:{id:1,name:'Северный'}}, purpose:mockPurposes[1], features:[mockFeatures[1]] },
  { id:3, name:'301', capacity:25, building:{id:2,name:'Корпус №2',location:{id:1,name:'Северный'}}, purpose:mockPurposes[3], features:[mockFeatures[2],mockFeatures[0]] },
  { id:4, name:'401', capacity:20, building:{id:2,name:'Корпус №2',location:{id:1,name:'Северный'}}, purpose:mockPurposes[2], features:[] },
  { id:5, name:'115', capacity:90, building:{id:3,name:'Корпус №3',location:{id:2,name:'Набережная'}}, purpose:mockPurposes[0], features:[mockFeatures[0],mockFeatures[3]] },
];

// ── People ──────────────────────────────────────────
export const mockEducators: EducatorDto[] = [
  { id:1, name:'Петров Иван Сергеевич', preferredDays:['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'], preferredTimeSlots:['FIRST','SECOND','THIRD'], compactSchedule:true },
  { id:2, name:'Иванова Мария Александровна', preferredDays:['MONDAY','WEDNESDAY','FRIDAY'], preferredTimeSlots:['FIRST','SECOND','THIRD','FOURTH'], compactSchedule:false },
  { id:3, name:'Сидоров Алексей Викторович', preferredDays:['TUESDAY','THURSDAY','SATURDAY'], preferredTimeSlots:['SECOND','THIRD','FOURTH'], compactSchedule:false },
  { id:4, name:'Козлова Елена Дмитриевна', preferredDays:['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'], preferredTimeSlots:['FIRST','SECOND'], compactSchedule:true },
  { id:5, name:'Новиков Дмитрий Петрович', preferredDays:['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY'], preferredTimeSlots:['FIRST','SECOND','THIRD','FOURTH'], compactSchedule:false },
  { id:6, name:'Морозова Ольга Игоревна', preferredDays:['MONDAY','WEDNESDAY','FRIDAY'], preferredTimeSlots:['THIRD','FOURTH'], compactSchedule:false },
];
export const mockGroups: GroupDto[] = [
  { id:1, name:'ПИ-101', size:28 },{ id:2, name:'ПИ-102', size:25 },
  { id:3, name:'ПИ-201', size:30 },{ id:4, name:'ПИ-202', size:27 },
  { id:5, name:'ИВТ-101', size:22 },{ id:6, name:'ИВТ-201', size:24 },
];

// ── Disciplines & Themes ─────────────────────────────
export const mockDisciplines: DisciplineDto[] = [
  { id:1, name:'Программирование', abbreviation:'Прогр.' },
  { id:2, name:'Математический анализ', abbreviation:'Матем.ан.' },
  { id:3, name:'Базы данных', abbreviation:'БД' },
  { id:4, name:'Физика', abbreviation:'Физ.' },
  { id:5, name:'Алгоритмы и структуры данных', abbreviation:'АСД' },
];
export const mockThemes: ThemeLessonDto[] = [
  { id:1, themeNumber:'1', title:'Введение в программирование', disciplineId:1, disciplineName:'Программирование' },
  { id:2, themeNumber:'2', title:'Типы данных', disciplineId:1, disciplineName:'Программирование' },
  { id:3, themeNumber:'3', title:'Циклы и массивы', disciplineId:1, disciplineName:'Программирование' },
  { id:4, themeNumber:'1', title:'Предел и непрерывность', disciplineId:2, disciplineName:'Математический анализ' },
  { id:5, themeNumber:'2', title:'Производная', disciplineId:2, disciplineName:'Математический анализ' },
  { id:6, themeNumber:'1', title:'Реляционная модель', disciplineId:3, disciplineName:'Базы данных' },
  { id:7, themeNumber:'2', title:'SQL — основы', disciplineId:3, disciplineName:'Базы данных' },
  { id:8, themeNumber:'1', title:'Механика. Кинематика', disciplineId:4, disciplineName:'Физика' },
  { id:9, themeNumber:'1', title:'Сложность алгоритмов', disciplineId:5, disciplineName:'Алгоритмы' },
  { id:10, themeNumber:'2', title:'Сортировка и поиск', disciplineId:5, disciplineName:'Алгоритмы' },
];

// ── Study Periods & Courses ────────────────────────────
export const mockPeriods: StudyPeriodDto[] = [
  { id:1, name:'Осенний семестр 2025/2026', studyYear:2025, periodType:'FALL_SEMESTER', startDate:'2025-09-01', endDate:'2025-12-31' },
  { id:2, name:'Весенний семестр 2025/2026', studyYear:2026, periodType:'SPRING_SEMESTER', startDate:'2026-02-09', endDate:'2026-06-30' },
];

function makeSlot(id:number, dcId:number, pos:number, kind:any, theme?:ThemeLessonDto): CurriculumSlotDto {
  return { id, disciplineCourseId:dcId, position:pos, kindOfStudy:kind,
    themeLesson: theme ? { id:theme.id, themeNumber:theme.themeNumber, title:theme.title } : undefined };
}
export const mockCourses: DisciplineCourseDto[] = [
  { id:1, semester:1, discipline:mockDisciplines[0] },
  { id:2, semester:1, discipline:mockDisciplines[1] },
  { id:3, semester:3, discipline:mockDisciplines[2] },
  { id:4, semester:1, discipline:mockDisciplines[3] },
  { id:5, semester:3, discipline:mockDisciplines[4] },
];
// Распишем слоты отдельно
export const mockCurriculumSlots: CurriculumSlotDto[] = [
  makeSlot(1,1,1,'LECTURE',mockThemes[0]),
  makeSlot(2,1,2,'PRACTICAL_WORK',mockThemes[1]),
  makeSlot(3,1,3,'LAB_WORK',mockThemes[2]),
  makeSlot(4,2,1,'LECTURE',mockThemes[3]),
  makeSlot(5,2,2,'PRACTICAL_WORK',mockThemes[4]),
  makeSlot(6,3,1,'LECTURE',mockThemes[5]),
  makeSlot(7,3,2,'LAB_WORK',mockThemes[6]),
  makeSlot(8,4,1,'LECTURE',mockThemes[7]),
  makeSlot(9,4,2,'LAB_WORK'),
  makeSlot(10,5,1,'LECTURE',mockThemes[8]),
  makeSlot(11,5,2,'PRACTICAL_WORK',mockThemes[9]),
];

// ── Streams ─────────────────────────────────────────────
export const mockStreams: StudyStreamDto[] = [
  { id:1, name:'Поток ПИ-1', semester:1, groups:[mockGroups[0],mockGroups[1]] },
  { id:2, name:'Поток ПИ-2', semester:3, groups:[mockGroups[2],mockGroups[3]] },
  { id:3, name:'Поток ИВТ-1', semester:1, groups:[mockGroups[4]] },
  { id:4, name:'Поток ИВТ-2', semester:3, groups:[mockGroups[5]] },
];

// ── Assignments ─────────────────────────────────────────
export const mockAssignments: AssignmentDto[] = mockSlotsToAssignments();

function mockSlotsToAssignments(): AssignmentDto[] {
  const edu = mockEducators;
  return mockCurriculumSlots.map((s,i) => ({
    id:i+1,
    curriculumSlot:{ id:s.id, position:s.position, kindOfStudyName:KIND_OF_STUDY_LABELS[s.kindOfStudy] },
    studyStream:{ id:mockStreams[i%mockStreams.length].id, name:mockStreams[i%mockStreams.length].name },
    educators:[{ id:edu[i%edu.length].id, name:edu[i%edu.length].name }],
  }));
}
import { KIND_OF_STUDY_LABELS } from '../types';

// ── Constraints ─────────────────────────────────────────
export const mockEducatorConstraints: EducatorConstraintDto[] = [
  { id:1, educatorId:1, kindOfConstraint:'BUSINESS_TRIP', startDate:'2025-11-10', endDate:'2025-11-14', description:'Конференция' },
  { id:2, educatorId:3, kindOfConstraint:'VACATION', startDate:'2025-10-01', endDate:'2025-10-15', description:'Отпуск' },
];
export const mockGroupConstraints: GroupConstraintDto[] = [
  { id:1, groupId:1, kindOfConstraint:'OTHER', startDate:'2025-11-05', endDate:'2025-11-05', description:'Праздник' },
];
export const mockAuditoriumConstraints: AuditoriumConstraintDto[] = [
  { id:1, auditoriumId:1, kindOfConstraint:'LIBRARY', startDate:'2025-10-20', endDate:'2025-10-24', description:'Ремонт' },
];
