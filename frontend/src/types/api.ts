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
/**
 * Код вида ограничения. Не union конкретных значений: перечень ведёт пользователь
 * (`GET /api/constraint-kinds`), и фронт не может знать его заранее — новые виды заводятся
 * без релиза. Подписи и цвет берутся из справочника, см. `ConstraintKindDto`.
 */
export type KindOfConstraints = string;

/**
 * Что впускает интервал, закрытый ограничением. В отличие от кода вида, набор значений принадлежит
 * бэку (по нему ветвится проверка доступности) — поэтому это union, а не строка.
 *
 * - `BLOCKING` — запрещает всё (командировка, отпуск, наряд);
 * - `ASSESSMENT_WINDOW` — окно сессии: впускает аттестации, запланированные планом в сессию;
 * - `ASSESSMENT_WINDOW_OPEN` — плюс внеплановые аттестации («свободные дни группы»).
 */
export type ConstraintMode = 'BLOCKING' | 'ASSESSMENT_WINDOW' | 'ASSESSMENT_WINDOW_OPEN';

/** Вид ограничения из пользовательского справочника. */
export interface ConstraintKindDto {
  code: string;
  name: string;
  shortName: string;
  /** Ключ палитры (`amber`, `sky`…) — набор классов знает `constraintStyles.ts`. */
  color: string;
  sortOrder: number;
  /** Погашенный не предлагается в выборе, но уже проставленный показывается. */
  active: boolean;
  /** Пришёл из кода: удалить нельзя, название и цвет правятся. */
  system: boolean;
  /** Сколько ограничений размечено этим видом — цена удаления. */
  usageCount: number;
  /** Режим: что интервал впускает. По нему решается, считать ли занятие поверх конфликтом. */
  mode: ConstraintMode;
}

/** Тело создания/правки вида ограничения. */
export interface ConstraintKindFormDto {
  name: string;
  shortName: string;
  color?: string;
  sortOrder?: number;
  active?: boolean;
  mode?: ConstraintMode;
}

/**
 * Где сдаётся аттестация — норма учебного плана.
 *
 * `SESSION` = в экзаменационную сессию: нужны дни подготовки, генерация такое не размещает
 * (экзамен принимает лектор, и по группам он идёт лесенкой — раскладывает диспетчер).
 * `STUDY_TIME` = в учебное время, как обычное занятие; сюда же экзамен по физподготовке.
 */
export type AssessmentWindow = 'SESSION' | 'STUDY_TIME';
export type PeriodType = 'FALL_SEMESTER' | 'SPRING_SEMESTER' | 'FALL_EXAM_SESSION' | 'SPRING_EXAM_SESSION';
/** Уровень учёной степени. Отрасль науки — не здесь: она справочник, её ведёт пользователь. */
export type AcademicDegree = 'CANDIDATE' | 'DOCTOR';
/** Учёное звание. ⚠️ Не должность: «доцент кафедры» — другое, в модели пока отсутствует. */
export type AcademicTitle = 'ASSOCIATE_PROFESSOR' | 'PROFESSOR';

// ============ ENUM DTO ============
export interface EnumDto {
  value: string;
  label: string;
  abbreviation: string;
  extra?: string;
  /**
   * Категория значения, если у enum-а есть классификация (у видов занятий — LECTURE / PRACTICE /
   * PROGRESS_CHECK / ASSESSMENT). Приходит с бэка, чтобы фронт не повторял правила вида «экзамен и
   * зачёты — это аттестация»: владелец классификации один — Java-enum. Здесь решается только цвет.
   */
  category?: string;
}

// ============ RESOURCES ============
/**
 * Регалии преподавателя. Все поля необязательны — «не указано» законное состояние.
 * Степень задаётся ДВУМЯ полями (уровень + отрасль): готовой строки «ктн» в модели нет,
 * её собирает бэк.
 */
export interface EducatorCredentialsFields {
  /** Специальное (воинское) звание. */
  specialRankId?: number | null;
  /** Род службы к званию («юстиции»); печатается только вместе со званием. */
  rankServiceId?: number | null;
  /** Уровень учёной степени. */
  academicDegree?: AcademicDegree | null;
  /** Отрасль науки степени. */
  scienceBranchId?: number | null;
  /** Учёное звание — НЕ должность. */
  academicTitle?: AcademicTitle | null;
}

export interface EducatorDto extends EducatorCredentialsFields {
  id: number;
  name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
  /** Подразделение (кафедра или отдел); null — ещё не распределён. */
  orgUnitId?: number | null;
  orgUnitName?: string | null;
  specialRankName?: string | null;
  rankServiceName?: string | null;
  scienceBranchName?: string | null;
  /**
   * Готовая подпись «п-к юст Иванов И.И., ктн, доц» — собрана на бэке.
   * Фронт её НЕ склеивает сам: тот же текст нужен бланку выгрузки, и вторая склейка
   * неминуемо разошлась бы с первой. Без регалий равна ФИО.
   */
  titleLine?: string | null;
}

export interface EducatorCreateDto extends EducatorCredentialsFields {
  name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
  orgUnitId?: number | null;
}

export interface EducatorUpdateDto extends EducatorCredentialsFields {
  name: string;
  preferredDays: DayOfWeek[];
  preferredTimeSlots: TimeSlotPair[];
  compactSchedule: boolean;
  /** null — открепить от подразделения. */
  orgUnitId?: number | null;
}

/** Строка справочника регалий (звание, род службы, отрасль науки). */
export interface DictionaryEntryDto {
  id: number;
  name: string;
  /** Сокращение без точек («п-к»); подпись собирается тоже без них («ктн») — склейка на бэке. */
  shortName: string;
  sortOrder: number;
  active: boolean;
  /** Сколько преподавателей ссылается на строку. */
  educatorCount: number;
  /** Решение считает бэк (FK стоят с RESTRICT), фронт его только показывает. */
  deletable: boolean;
}

export interface DictionaryEntryFormDto {
  name: string;
  shortName: string;
  sortOrder?: number | null;
  active?: boolean | null;
}

/** Вид справочника регалий; slug — сегмент пути API. */
export interface DictionaryKindDto {
  value: string;
  slug: string;
  label: string;
}

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY';

// Все лейблы enum-ов загружаются с бэкенда через GET /api/enums/all
// Никакого хардкода — единый источник правды на сервере

export interface GroupDto {
  id: number;
  name: string;
  size: number;
  baseAuditorium?: { id: number; name: string };
  /** Год набора (поступления); null — не указан. */
  enrollmentYear?: number | null;
  /** Подразделение (кафедра или факультет); null — ещё не распределена. */
  orgUnitId?: number | null;
  orgUnitName?: string | null;
}

export interface GroupCreateDto {
  name: string;
  size: number;
  baseAuditoriumId?: number | null;
  enrollmentYear?: number | null;
  orgUnitId?: number | null;
}

export interface GroupUpdateDto {
  name: string;
  size: number;
  baseAuditoriumId?: number | null;
  /** null — снять год набора. */
  enrollmentYear?: number | null;
  /** null — открепить от подразделения. */
  orgUnitId?: number | null;
}

// ============ ОРГСТРУКТУРА (ПОДРАЗДЕЛЕНИЯ) ============

/**
 * Вид подразделения. Лейблы («Кафедра», «Каф.») сюда НЕ дублируются — они приходят с бэкенда
 * через `GET /api/enums/org-unit-type` (единый источник правды, как у остальных enum-ов).
 */
export type OrgUnitType = 'INSTITUTE' | 'FACULTY' | 'DIVISION' | 'DEPARTMENT';

/**
 * Подразделение приходит плоским списком: дерево из него собирает фронт (`useOrgUnits`),
 * потому что форма дерева — презентация, а не данные.
 */
export interface OrgUnitDto {
  id: number;
  name: string;
  shortName?: string | null;
  type: OrgUnitType;
  /** Родитель; null — верхний уровень (кафедра вне факультета — легитимный случай). */
  parentId?: number | null;
  parentName?: string | null;
  active: boolean;
}

/**
 * Охват подразделения: кто в него попадает С УЧЁТОМ ВЛОЖЕННОСТИ (кафедры факультета и т.д.).
 *
 * <p>Разворот поддерева считает бэк (`OrgUnitScopeResolver` — единственный владелец рекурсии по
 * дереву), поэтому фронт не повторяет обход: дерево он строит только для отображения.</p>
 *
 * <p>Приходят id, а не карточки: списки преподавателей и групп у фронта уже есть.</p>
 */
export interface OrgUnitScopeDto {
  orgUnitId: number;
  name: string;
  unitIds: number[];
  educatorIds: number[];
  groupIds: number[];
}

export interface OrgUnitCreateDto {
  name: string;
  shortName?: string | null;
  type: OrgUnitType;
  parentId?: number | null;
}

export interface OrgUnitUpdateDto {
  name: string;
  shortName?: string | null;
  type: OrgUnitType;
  parentId?: number | null;
  active: boolean;
}

/**
 * Цена удаления подразделения. Каскада нет: любая ссылка (дочерние узлы, преподаватели, группы)
 * удаление запрещает — `deletable` считает бэк, фронт его показывает, а не выводит.
 */
export interface OrgUnitDeletionImpactDto {
  orgUnitId: number;
  name: string;
  deletable: boolean;
  childUnits: number;
  educators: number;
  groups: number;
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
  /** Где сдаётся аттестация; у обычных занятий всегда `STUDY_TIME`. */
  assessmentWindow: AssessmentWindow;
  themeLesson?: { id: number; themeNumber: string; title: string };
  requiredAuditorium?: { id: number; name: string };
  priorityAuditorium?: { id: number; name: string };
  allowedAuditoriumPool?: { id: number; name: string };
}

export interface CurriculumSlotCreateDto {
  disciplineCourseId: number;
  position: number;
  kindOfStudy: KindOfStudy;
  /** Не передан — бэк ставит `STUDY_TIME`. */
  assessmentWindow?: AssessmentWindow;
  themeLessonId?: number;
  requiredAuditoriumId?: number;
  priorityAuditoriumId?: number;
  allowedAuditoriumPoolId?: number;
}

export interface CurriculumSlotUpdateDto {
  kindOfStudy: KindOfStudy; // на бэке @NotNull
  /** Не передан — бэк ставит `STUDY_TIME` (правка слота затрёт прежнее значение!). */
  assessmentWindow?: AssessmentWindow;
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

// ============ ЗДОРОВЬЕ АУДИТОРИЙ ============
// Не стоят ли двое в одной комнате и все ли помещаются. Оба состояния система создаёт сама и
// не показывает: конфликт непредставим в модели занятости решателя (там ячейка → одно занятие),
// а строки schedule_view друг о друге не знают. Расписание с конфликтами выглядит нормальным.

/** Здоровье одной аудитории — строка разбивки. Причина обычно в самой комнате, отсюда разбивка. */
export interface RoomHealthDto {
  auditoriumId: number;
  name: string | null;
  capacity: number;
  conflictingCells: number;  // в скольких ячейках комната занята дважды
  doubleBooked: number;      // сколько занятий в этих ячейках стоит
  overCapacity: number;      // сколько занятий не помещается
  maxExcess: number;         // максимальный перебор по людям
}

/**
 * Две метрики намеренно разной силы:
 * doubleBooked — физика (две группы не войдут в одну дверь), допустимо только 0;
 * overCapacity — суждение (перебор на пару человек — рабочая ситуация, на десятки — фикция),
 * поэтому перебор отдаётся числом (maxExcess), а не флагом: границу проводит диспетчер.
 */
export interface AuditoriumHealthDto {
  sessionId: string | null;
  placements: number;
  conflictingCells: number;
  doubleBooked: number;
  overCapacity: number;
  rooms: RoomHealthDto[];
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

/** Загрузка одной аудитории за период (утилизация, не компактность — у комнаты нет «окон/штрафа»). */
export interface AuditoriumLoadDto {
  auditoriumId: number;
  name: string;
  capacity: number;
  occupiedPairs: number;   // занятых ячеек дата×пара (двойное бронирование = одна ячейка)
  freePairs: number;
  loadPercent: number;     // загрузка по времени: занято / доступно × 100
  daysUsed: number;
  avgPairsPerDay: number;
  fourthPairs: number;     // дней с занятой 4-й парой
  saturdayPairs: number;
}

/** Загрузка аудиторий за период: сводка + строки по комнатам (самые загруженные первыми). */
export interface PeriodAuditoriumLoadDto {
  totalRooms: number;
  roomsUsed: number;
  idleRooms: number;
  availablePairs: number;   // доступных ячеек периода (знаменатель загрузки)
  avgLoadPercent: number;
  auditoriums: AuditoriumLoadDto[];
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

// ============ ИМПОРТ РАСПИСАНИЯ ИЗ СТОРОННЕЙ ПРОГРАММЫ ============

/**
 * Разрез выгрузки: чьё это расписание. Определяется бэком ПО ШАПКЕ файла, а не по имени —
 * имена файлов у заказчика («911», «ГоряиновР.И.») ничего не гарантируют.
 */
export type ImportCutKind = 'GROUP' | 'EDUCATOR' | 'AUDITORIUM' | 'UNKNOWN';

/** Занятие, как оно прочитано из ячейки: ничего не сопоставлено с нашей базой. */
export interface ImportLessonEntryDto {
  date: string | null;
  slot: TimeSlotPair;
  kind: string | null;       // «Л», «ЛР», «ПЗ»; null в преподавательском разрезе
  theme: string | null;      // «Т.4»; только групповой разрез
  discipline: string | null; // обозначение: «АСКС»
  /** Групп может быть несколько — потоковое занятие в одной ячейке. */
  groups: string[];
  /** Аудиторий тоже: занятие делится по кабинетам. */
  rooms: string[];
  educator: string | null;   // только преподавательский разрез
}

/**
 * Строка подвала группового файла: дисциплина с преподавателями.
 * `code` — то, чем дисциплина подписана в ячейках, он же ключ склейки с занятиями.
 * Подписи преподавателей отдаются как в файле — со званием и степенью, без разбора.
 */
export interface ImportFooterRowDto {
  code: string;
  name: string;
  department: string;
  lecturers: string[];
  practicians: string[];
  hours: string;   // «18-30» — как напечатано, не разбирается
  report: string;  // «ЗО», «ЗЧ», «ЭКЗ» либо пусто
  stream: string;
}

/**
 * Что стало со значением из файла. Заводить можно только `MISSING`:
 * `AMBIGUOUS` — это незнание, а не отсутствие, и новая строка сделала бы его вечным.
 */
export type ImportMatchStatus = 'MATCHED' | 'MISSING' | 'AMBIGUOUS' | 'UNREADABLE';

/** Строка сверки: одно значение из файла и его судьба в нашей базе. */
export interface ImportMatchRowDto {
  source: string;              // как в файле: «АСКС», «п/п-к Чащин С.В.», «252-3»
  detail: string | null;       // что понял разбор: «корпус 3», «ф. 9 · каф. 91 · набор 2025»
  status: ImportMatchStatus;
  matchedId: number | null;
  matchedName: string | null;  // как называется у нас — может отличаться, это и надо увидеть
  note: string | null;         // причина отказа либо расхождение при совпадении
}

/** Раздел сверки по одной категории; несопоставленные строки идут первыми. */
export interface ImportMatchSectionDto {
  title: string;
  hint: string;    // чем сопоставляли
  total: number;
  matched: number;
  rows: ImportMatchRowDto[];
}

/** Сверка со справочниками по всей пачке файлов. Ничего не записывает. */
export interface ImportMatchReportDto {
  sections: ImportMatchSectionDto[];
}

/**
 * Сводка по одному разобранному файлу — что программа поняла ДО всякого сопоставления.
 * Ничего не записывается: первый прогон импорта сущностей не создаёт (решение И-10).
 */
export interface SheetInspectionDto {
  file: string;
  cut: ImportCutKind;
  owner: string | null;
  faculty: string | null;
  department: string | null;
  studyYear: number | null;
  semester: string | null;
  lessons: number;
  markers: number;
  firstDate: string | null;
  lastDate: string | null;
  disciplines: string[];
  groups: string[];
  rooms: string[];
  markerCodes: string[];
  footer: ImportFooterRowDto[];
  problems: string[];
  sample: ImportLessonEntryDto[];
}

/**
 * Ответ пробной загрузки: сводки по файлам плюс одна сверка на всю пачку.
 * Вместе, потому что дисциплина из подвала одного файла встречается в ячейках другого.
 */
export interface ImportReportDto {
  files: SheetInspectionDto[];
  matching: ImportMatchReportDto;
}

/**
 * Итог разбора каталога на диске — сводка по пачке, а не карточка на файл.
 * В живой выгрузке 1792 файла: полторы тысячи карточек весили бы десятки мегабайт JSON.
 */
export interface FolderInspectionReportDto {
  path: string;
  files: number;
  parsed: number;
  failed: number;
  byCut: Partial<Record<ImportCutKind, number>>;
  lessons: number;
  markers: number;
  firstDate: string | null;
  lastDate: string | null;
  /**
   * Замечания схлопнуты по тексту: одинаковая кривизна в 300 файлах — одна строка с числом.
   * `example` — имя одного из таких файлов: без него замечание про ячейку («Вт 1 колонка 5»)
   * нечем проверить, искать её пришлось бы вручную по всему каталогу.
   */
  problems: { message: string; count: number; example: string | null }[];
  problemsTotal: number;
  matching: ImportMatchReportDto;
}

/** Строка заведения: `id === null` — пропущено, причина в `note`. */
export interface ImportCreatedRowDto {
  source: string;
  id: number | null;
  name: string | null;
  note: string | null;
}

export interface ImportCreationSectionDto {
  title: string;
  created: number;
  skipped: number;
  rows: ImportCreatedRowDto[];
}

/**
 * Что импорт завёл в справочниках. Плана и расписания не касается: справочники заводятся
 * отдельным шагом, пока на них никто не ссылается и строки ещё удаляемы.
 */
export interface ImportCreationReportDto {
  sections: ImportCreationSectionDto[];
}
