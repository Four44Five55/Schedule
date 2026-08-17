import api from './apiClient';
import {
  EducatorDto,
  EducatorCreateDto,
  EducatorUpdateDto,
  AuditoriumDto,
  AuditoriumCreateDto,
  AuditoriumUpdateDto,
  GroupDto,
  GroupCreateDto,
  GroupUpdateDto,
  DisciplineDto,
  DisciplineCreateDto,
  DisciplineUpdateDto,
  DisciplineCourseDto,
  DisciplineCourseCreateDto,
  DisciplineCourseUpdateDto,
  CourseDeletionImpactDto,
  CourseCloneRequestDto,
  EnumDto,
  ConstraintKindDto,
  ConstraintKindFormDto,
  StudyStreamDto,
  CurriculumSlotDto,
  CurriculumSlotCreateDto,
  CurriculumSlotUpdateDto,
  ThemeLessonDto,
  ThemeLessonCreateDto,
  ThemeLessonUpdateDto,
  SlotChainDto,
  SlotChainCreateDto,
  AssignmentDto,
  AssignmentCreateDto,
  ApplyAssignmentToCourseDto,
  RemoveAssignmentsFromCourseDto,
  RemoveAssignmentsImpactDto,
  AssignmentUpdateDto,
  LocationDto,
  LocationCreateDto,
  LocationUpdateDto,
  LocationDeletionImpactDto,
  BuildingDto,
  BuildingCreateDto,
  BuildingUpdateDto,
  BuildingDeletionImpactDto,
  FeatureDto,
  AuditoriumPurposeDto,
  AuditoriumPoolDto,
  StudyPeriodDto,
  StudyPeriodCreateDto,
  EducatorConstraintDto,
  GroupConstraintDto,
  AuditoriumConstraintDto,
  EducatorConstraintCreateDto,
  GroupConstraintCreateDto,
  AuditoriumConstraintCreateDto,
  ScheduleResultDto,
  AuditoriumDeletionImpactDto,
  PeriodReadinessDto,
  ProjectionHealthDto,
  AuditoriumHealthDto,
  SlotDeletionImpactDto,
  PeriodScheduleQualityDto,
  PeriodAuditoriumLoadDto,
  GroupDensityDto,
  ExportAxis,
  StudyStreamCreateDto, StudyStreamUpdateDto,
  OrgUnitDto,
  OrgUnitCreateDto,
  OrgUnitUpdateDto,
  OrgUnitScopeDto,
  OrgUnitDeletionImpactDto,
  DictionaryEntryDto,
  DictionaryEntryFormDto,
  DictionaryKindDto,
  ImportReportDto,
  PlanReportDto,
  ScheduleWriteReportDto,
  ImportedSessionDto,
  RollbackImpactDto,
  ImportCreationReportDto,
  FolderInspectionReportDto
} from '../types/api';
import { downloadBlob, filenameFromContentDisposition } from '../utils/download';

// ============ 1. СПРАВОЧНИКИ (ENUMS) ============
export const EnumService = {
  getAll: () =>
      api
          .get<{
            kindOfStudy: EnumDto[];
            daysOfWeek: EnumDto[];
            timeSlots: EnumDto[];
            periodTypes: EnumDto[];
            orgUnitTypes: EnumDto[];
            academicDegrees: EnumDto[];
            academicTitles: EnumDto[];
          }>('/enums/all')
          .then((r) => r.data),
};

/**
 * Импорт расписания из сторонней программы.
 *
 * Пока здесь только пробный разбор: он отвечает на вопрос «что программа поняла из файла» и
 * **ничего не записывает**. Первый прогон импорта сущностей не создаёт намеренно — завести
 * преподавателя легко, а убрать (когда на него сошлётся назначение) уже нет.
 */
/**
 * Каким знаком писать суффикс номера группы у нас: «101/1» или «101-1».
 *
 * В выгрузке встречаются оба: дефис появляется там, где номер попал в ИМЯ ФАЙЛА — «/» в именах
 * файлов запрещён. На опознание группы выбор не влияет (оба написания — одна группа), только на то,
 * как мы её назовём.
 */
export type GroupNameStyle = 'SLASH' | 'DASH';

/** Параметры прогона, которых в выгрузке нет: их называет человек. */
export interface ImportSettings {
  locationId?: number | null;
  groupSize: number;
  roomCapacity: number;
  groupNameStyle: GroupNameStyle;
  /**
   * Ручные привязки к подразделению: раздел отчёта → значение строки → id кафедры.
   *
   * Нужны там, где разбор кафедру не вывел: у преподавателя нет своего файла, номер группы не по
   * стандарту, кафедра в базе не заведена. Выбор человека сильнее вывода из файла — он для того и
   * делается, что файл ответа не дал. Раздел едет ключом: какие из них про преподавателей, а какие
   * про группы, знает бэк — фронт эту классификацию не повторяет.
   */
  orgUnits?: Record<string, Record<string, number>>;
  /**
   * Родитель, выбранный для самого подразделения: имя строки → имя родителя.
   *
   * Именем, а не id: спорный факультет может быть ещё не заведён — его создаёт этот же прогон,
   * и id у него нет. Выбор из одного справочника был бы вопросом, на который нечем ответить.
   */
  orgUnitParents?: Record<string, string>;
}

/**
 * Ручные привязки — JSON-строкой в параметре запроса.
 *
 * Ключ здесь — значение как оно показано в отчёте («п/п-к Астахов С.В.», «10073/19»), в нём законно
 * есть и пробелы, и точки, и косая черта: любой разделитель вида «ключ=значение» рано или поздно
 * попал бы внутрь ключа. `undefined` axios из параметров выбрасывает сам.
 */
/** Плоская карта имён — то же правило: пустую не шлём. */
const namesParam = (map?: Record<string, string>): string | undefined =>
  map && Object.keys(map).length > 0 ? JSON.stringify(map) : undefined;

const orgUnitsParam = (map?: Record<string, Record<string, number>>): string | undefined => {
  const filled = Object.entries(map ?? {}).filter(([, rows]) => Object.keys(rows).length > 0);
  return filled.length > 0 ? JSON.stringify(Object.fromEntries(filled)) : undefined;
};

export const ImportService = {
  /**
   * Пробный разбор файлов выгрузки (HTML любого разреза — группы, преподавателя, аудитории)
   * вместе со сверкой по справочникам. Не пишет ничего.
   *
   * Заголовок `Content-Type` **не задаём вручную**: границу multipart проставляет браузер, а
   * axios-клиент по умолчанию шлёт `application/json` — с ним бэк не разберёт тело.
   *
   * `locationId` — в какой локации искать аудитории. В файле локации нет вовсе, а корпус «3»
   * законно существует в нескольких кампусах; без параметра одноимённые комнаты вернутся как
   * «одноимённых несколько», а не будут выбраны наугад.
   */
  inspect: (
    files: File[],
    locationId?: number | null,
    groupNameStyle: GroupNameStyle = 'SLASH',
    periodId?: number | null,
  ): Promise<ImportReportDto> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api.post<ImportReportDto>('/import/inspect', form, {
      headers: { 'Content-Type': undefined },
      params: {
        groupNameStyle,
        ...(locationId != null ? { locationId } : {}),
        ...(periodId != null ? { periodId } : {}),
      },
    }).then((r) => r.data);
  },

  /**
   * Разобрать каталог выгрузки прямо на диске — путь для полного объёма.
   *
   * Файлы не загружаются: бэкенд локальный, выгрузка лежит на той же машине. Каталог должен быть
   * внутри `import.source-root`, иначе бэк ответит 400 с объяснением.
   */
  inspectFolder: (
    path: string,
    locationId?: number | null,
    groupNameStyle: GroupNameStyle = 'SLASH',
    periodId?: number | null,
  ): Promise<FolderInspectionReportDto> =>
    api.post<FolderInspectionReportDto>('/import/inspect-folder', null, {
      params: {
        path,
        groupNameStyle,
        ...(locationId != null ? { locationId } : {}),
        ...(periodId != null ? { periodId } : {}),
      },
    }).then((r) => r.data),

  /**
   * Завести ТОЛЬКО подразделения — шаг раньше всех остальных.
   *
   * Отдельно от `createMissing` потому, что на подразделения ссылаются и преподаватель, и группа,
   * и комната: незаведённая кафедра не отменяет заведение зависимых, а тихо оставляет их без
   * привязки. Разобрать спорное дерево дешевле до того, как появились сотни ссылающихся строк.
   *
   * Повторный запуск безопасен: уже заведённое пропускается по ключу.
   */
  createOrgUnits: (
    files: File[],
    parents?: Record<string, string>,
  ): Promise<ImportCreationReportDto> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api.post<ImportCreationReportDto>('/import/create-org-units', form, {
      headers: { 'Content-Type': undefined },
      params: { orgUnitParents: namesParam(parents) },
    }).then((r) => r.data);
  },

  /** То же по каталогу на диске. */
  createOrgUnitsFromFolder: (
    path: string,
    parents?: Record<string, string>,
  ): Promise<ImportCreationReportDto> =>
    api.post<ImportCreationReportDto>('/import/create-org-units-folder', null, {
      params: { path, orgUnitParents: namesParam(parents) },
    }).then((r) => r.data),

  /**
   * Завести в справочниках то, чего сверка не нашла. Плана и расписания не касается.
   *
   * Файлы шлются заново, а не хранятся на сервере: у отчёта нет серверной жизни между запросами,
   * иначе появилась бы «висящая заявка», которую надо протухать и синхронизировать.
   *
   * `groupSize` и `roomCapacity` — то, чего в выгрузке нет, а колонки `NOT NULL`. Вместимость
   * определяет будущий счёт «перебор в аудитории», поэтому число называет человек, а не константа.
   */
  createMissing: (
    files: File[],
    settings: ImportSettings,
  ): Promise<ImportCreationReportDto> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api.post<ImportCreationReportDto>('/import/create-missing', form, {
      headers: { 'Content-Type': undefined },
      params: {
        groupSize: settings.groupSize,
        roomCapacity: settings.roomCapacity,
        groupNameStyle: settings.groupNameStyle,
        orgUnits: orgUnitsParam(settings.orgUnits),
        orgUnitParents: namesParam(settings.orgUnitParents),
        ...(settings.locationId != null ? { locationId: settings.locationId } : {}),
      },
    }).then((r) => r.data);
  },

  /**
   * Что импорт сделает с учебным планом. Не пишет ничего.
   *
   * Отдельным шагом, а не вместе с разбором: план считается уже ПОСЛЕ заведения справочников (без
   * заведённой дисциплины и потока занятие не разрешается), а период здесь обязателен — от него
   * зависит семестр курса.
   */
  planPreview: (
    files: File[],
    periodId: number,
    groupNameStyle: GroupNameStyle = 'SLASH',
  ): Promise<PlanReportDto> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api.post<PlanReportDto>('/import/plan-preview', form, {
      headers: { 'Content-Type': undefined },
      params: { periodId, groupNameStyle },
    }).then((r) => r.data);
  },

  /**
   * Завести учебный план по тому же расчёту. Расписания не касается.
   *
   * Идёт ПОСЛЕ «Завести» (справочники): без заведённых дисциплины, группы и потока занятие в план
   * не разрешается. Повторный запуск безопасен — заведённое считается «уже есть».
   */
  createPlan: (
    files: File[],
    periodId: number,
    groupNameStyle: GroupNameStyle = 'SLASH',
  ): Promise<PlanReportDto> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api.post<PlanReportDto>('/import/create-plan', form, {
      headers: { 'Content-Type': undefined },
      params: { periodId, groupNameStyle },
    }).then((r) => r.data);
  },

  /** То же по каталогу на диске. */
  createPlanFromFolder: (
    path: string,
    periodId: number,
    groupNameStyle: GroupNameStyle = 'SLASH',
  ): Promise<PlanReportDto> =>
    api.post<PlanReportDto>('/import/create-plan-folder', null, {
      params: { path, periodId, groupNameStyle },
    }).then((r) => r.data),

  /** То же по каталогу на диске. */
  planPreviewFromFolder: (
    path: string,
    periodId: number,
    groupNameStyle: GroupNameStyle = 'SLASH',
  ): Promise<PlanReportDto> =>
    api.post<PlanReportDto>('/import/plan-preview-folder', null, {
      params: { path, periodId, groupNameStyle },
    }).then((r) => r.data),

  /** То же заведение, но по каталогу на диске — правила и сервис те же, отличается только вход. */
  createMissingFromFolder: (
    path: string,
    settings: ImportSettings,
  ): Promise<ImportCreationReportDto> =>
    api.post<ImportCreationReportDto>('/import/create-missing-folder', null, {
      params: {
        path,
        groupSize: settings.groupSize,
        roomCapacity: settings.roomCapacity,
        groupNameStyle: settings.groupNameStyle,
        orgUnits: orgUnitsParam(settings.orgUnits),
        orgUnitParents: namesParam(settings.orgUnitParents),
        ...(settings.locationId != null ? { locationId: settings.locationId } : {}),
      },
    }).then((r) => r.data),

  /**
   * Записать расписание: план и размещения в НОВУЮ сессию. Последний шаг импорта.
   *
   * Живое расписание не двигается — сессия всегда новая, и неудачный прогон сносится одной
   * командой. Размещения пишутся закреплёнными (`locked`), иначе первая же перегенерация их снесёт.
   *
   * `project` — писать ли read-модель. В экспериментальном периоде можно и полезно: импорт видно
   * в обычной сетке. В ЖИВОМ периоде нельзя, пока `schedule_view` не несёт `session_id`.
   */
  createSchedule: (
    files: File[],
    periodId: number,
    locationId: number | null,
    groupNameStyle: GroupNameStyle = 'SLASH',
    project = false,
  ): Promise<ScheduleWriteReportDto> => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api.post<ScheduleWriteReportDto>('/import/create-schedule', form, {
      headers: { 'Content-Type': undefined },
      params: { periodId, groupNameStyle, project, ...(locationId != null ? { locationId } : {}) },
    }).then((r) => r.data);
  },

  /** То же по каталогу на диске — путь для полного объёма. */
  createScheduleFromFolder: (
    path: string,
    periodId: number,
    locationId: number | null,
    groupNameStyle: GroupNameStyle = 'SLASH',
    project = false,
  ): Promise<ScheduleWriteReportDto> =>
    api.post<ScheduleWriteReportDto>('/import/create-schedule-folder', null, {
      params: { path, periodId, groupNameStyle, project, ...(locationId != null ? { locationId } : {}) },
    }).then((r) => r.data),

  /** Импортные сессии периода: без списка кнопка удаления теряет объект после F5. */
  importedSessions: (periodId: number): Promise<ImportedSessionDto[]> =>
    api.get<ImportedSessionDto[]>('/import/sessions', { params: { periodId } }).then((r) => r.data),

  /** Цена отката плана — ДО нажатия, а не по факту исчезнувшего расписания. */
  rollbackImpact: (periodId: number): Promise<RollbackImpactDto> =>
    api.get<RollbackImpactDto>('/import/rollback-impact', { params: { periodId } }).then((r) => r.data),

  /**
   * Снести учебный план периода: курсы → слоты → назначения → размещения и осиротевшие потоки.
   *
   * Нужен, когда кривым оказался разбор ПЛАНА. Если кривой оказалась только раскладка, достаточно
   * снести сессию — план переживёт.
   */
  rollbackPlan: (periodId: number): Promise<RollbackImpactDto> =>
    api.delete<RollbackImpactDto>('/import/plan', { params: { periodId } }).then((r) => r.data),
};

/**
 * Справочник видов ограничений — командировка, отпуск, наряд и что угодно ещё.
 *
 * Отдельно от `EnumService`, потому что это уже не enum: перечень ведёт пользователь, а не код
 * (правило «кто владеет списком» — в docs/CONVENTIONS.md). Виды ЗАНЯТИЙ, наоборот, остаются в `/enums`.
 */
export const ConstraintKindService = {
  /** Все виды, включая погашенные: уже проставленное ограничение должно чем-то подписываться. */
  getAll: () => api.get<ConstraintKindDto[]>('/constraint-kinds').then((r) => r.data),
  create: (form: ConstraintKindFormDto) =>
      api.post<ConstraintKindDto>('/constraint-kinds', form).then((r) => r.data),
  update: (code: string, form: ConstraintKindFormDto) =>
      api.put<ConstraintKindDto>(`/constraint-kinds/${code}`, form).then((r) => r.data),
  /** 409, если видом что-то размечено или он системный — текст показываем пользователю. */
  remove: (code: string) => api.delete<void>(`/constraint-kinds/${code}`).then((r) => r.data),
};

/**
 * Справочники регалий преподавателя: специальные звания, роды службы, отрасли науки.
 *
 * Один набор методов на все виды — вид передаётся сегментом пути ({@code special-ranks}).
 * Копировать CRUD на каждый справочник незачем: они отличаются только таблицей, а будущая
 * должность подключится сюда же новым значением kind, без правки этого файла.
 *
 * Степень (кандидат/доктор) и учёное звание (доцент/профессор) сюда НЕ входят — у них по два
 * значения, они живут enum-ами и приезжают через EnumService.
 */
export type EducatorDictionaryKind = 'special-ranks' | 'rank-services' | 'science-branches';

export const EducatorDictionaryService = {
  getKinds: () => api.get<DictionaryKindDto[]>('/educator-dictionaries').then((r) => r.data),

  getAll: (kind: EducatorDictionaryKind) =>
      api.get<DictionaryEntryDto[]>(`/educator-dictionaries/${kind}`).then((r) => r.data),

  create: (kind: EducatorDictionaryKind, dto: DictionaryEntryFormDto) =>
      api.post<DictionaryEntryDto>(`/educator-dictionaries/${kind}`, dto).then((r) => r.data),

  update: (kind: EducatorDictionaryKind, id: number, dto: DictionaryEntryFormDto) =>
      api.put<DictionaryEntryDto>(`/educator-dictionaries/${kind}/${id}`, dto).then((r) => r.data),

  /** 409 с текстом причины, если на строку ссылаются преподаватели (FK RESTRICT). */
  delete: (kind: EducatorDictionaryKind, id: number) =>
      api.delete<void>(`/educator-dictionaries/${kind}/${id}`).then((r) => r.data),
};

// ============ 2. РЕСУРСЫ ============
export const ResourceService = {
  getEducators: () => api.get<EducatorDto[]>('/educators').then((r) => r.data).catch(() => []),
  getEducator: (id: number) => api.get<EducatorDto>(`/educators/${id}`).then((r) => r.data),
  createEducator: (data: EducatorCreateDto) => api.post<EducatorDto>('/educators', data).then((r) => r.data),
  updateEducator: (id: number, data: EducatorUpdateDto) => api.put<EducatorDto>(`/educators/${id}`, data).then((r) => r.data),
  deleteEducator: (id: number) => api.delete(`/educators/${id}`).then(() => {}),

  getAuditoriums: () => api.get<AuditoriumDto[]>('/auditoriums').then((r) => r.data).catch(() => []),
  getAuditorium: (id: number) => api.get<AuditoriumDto>(`/auditoriums/${id}`).then((r) => r.data),
  createAuditorium: (data: AuditoriumCreateDto) => api.post<AuditoriumDto>('/auditoriums', data).then((r) => r.data),
  updateAuditorium: (id: number, data: AuditoriumUpdateDto) => api.put<AuditoriumDto>(`/auditoriums/${id}`, data).then((r) => r.data),
  /**
   * Цена удаления аудитории: сколько занятий останется без комнаты (и сколько из них закреплено),
   * не запрещает ли удаление учебный план (slotsRequiringIt > 0 → нельзя).
   */
  getAuditoriumDeleteImpact: (id: number) =>
      api.get<AuditoriumDeletionImpactDto>(`/auditoriums/${id}/delete-impact`).then((r) => r.data),
  deleteAuditorium: (id: number) => api.delete(`/auditoriums/${id}`).then(() => {}),

  getGroups: () => api.get<GroupDto[]>('/groups').then((r) => r.data).catch(() => []),
  getGroup: (id: number) => api.get<GroupDto>(`/groups/${id}`).then((r) => r.data),
  createGroup: (data: GroupCreateDto) => api.post<GroupDto>('/groups', data).then((r) => r.data),
  updateGroup: (id: number, data: GroupUpdateDto) => api.put<GroupDto>(`/groups/${id}`, data).then((r) => r.data),
  deleteGroup: (id: number) => api.delete(`/groups/${id}`).then(() => {}),

  getStreams: () => api.get<StudyStreamDto[]>('/study-streams').then((r) => r.data).catch(() => []),
  createStream: (data: StudyStreamCreateDto) => api.post<StudyStreamDto>('/study-streams', data).then((r) => r.data),
  updateStream: (id: number, data: StudyStreamUpdateDto) => api.put<StudyStreamDto>(`/study-streams/${id}`, data).then((r) => r.data),
  deleteStream: (id: number) => api.delete(`/study-streams/${id}`).then(() => {}),
  getLocations: () => api.get<LocationDto[]>('/locations').then((r) => r.data),
  createLocation: (data: LocationCreateDto) => api.post<LocationDto>('/locations', data).then((r) => r.data),
  updateLocation: (id: number, data: LocationUpdateDto) => api.put<LocationDto>(`/locations/${id}`, data).then((r) => r.data),
  getLocationDeleteImpact: (id: number) =>
      api.get<LocationDeletionImpactDto>(`/locations/${id}/delete-impact`).then((r) => r.data),
  deleteLocation: (id: number) => api.delete(`/locations/${id}`).then(() => {}),
  // --- Оргструктура: плоский список, дерево собирает фронт (см. useOrgUnits) ---
  getOrgUnits: () => api.get<OrgUnitDto[]>('/org-units').then((r) => r.data),
  createOrgUnit: (data: OrgUnitCreateDto) => api.post<OrgUnitDto>('/org-units', data).then((r) => r.data),
  updateOrgUnit: (id: number, data: OrgUnitUpdateDto) =>
      api.put<OrgUnitDto>(`/org-units/${id}`, data).then((r) => r.data),
  /** Цена удаления: ссылки (дети/преподаватели/группы) удаление запрещают — `deletable` с бэка. */
  /**
   * Охват подразделения с учётом вложенности (id преподавателей/групп поддерева).
   * Разворот дерева живёт на бэке намеренно — фронт свой обход для этого не заводит.
   */
  getOrgUnitScope: (id: number) =>
      api.get<OrgUnitScopeDto>(`/org-units/${id}/scope`).then((r) => r.data),
  getOrgUnitDeleteImpact: (id: number) =>
      api.get<OrgUnitDeletionImpactDto>(`/org-units/${id}/delete-impact`).then((r) => r.data),
  deleteOrgUnit: (id: number) => api.delete(`/org-units/${id}`).then(() => {}),

  getBuildings: () => api.get<BuildingDto[]>('/buildings').then((r) => r.data),
  createBuilding: (data: BuildingCreateDto) => api.post<BuildingDto>('/buildings', data).then((r) => r.data),
  updateBuilding: (id: number, data: BuildingUpdateDto) => api.put<BuildingDto>(`/buildings/${id}`, data).then((r) => r.data),
  getBuildingDeleteImpact: (id: number) =>
      api.get<BuildingDeletionImpactDto>(`/buildings/${id}/delete-impact`).then((r) => r.data),
  deleteBuilding: (id: number) => api.delete(`/buildings/${id}`).then(() => {}),
  getFeatures: () => api.get<FeatureDto[]>('/features').then((r) => r.data),
  getAuditoriumPurposes: () => api.get<AuditoriumPurposeDto[]>('/auditorium-purposes').then((r) => r.data),
  getAuditoriumPools: () => api.get<AuditoriumPoolDto[]>('/auditorium-pools').then((r) => r.data),
  getStudyPeriods: () => api.get<StudyPeriodDto[]>('/study-periods').then((r) => r.data),
  createStudyPeriod: (data: StudyPeriodCreateDto) =>
      api.post<StudyPeriodDto>('/study-periods', data).then((r) => r.data),

  /**
   * Получить активный учебный период (содержит сегодняшнюю дату).
   */
  getActiveStudyPeriod: () =>
      api.get<StudyPeriodDto>('/study-periods/active')
        .then((r) => r.data)
        .catch(() => null),
};

// ============ 3. УЧЕБНЫЙ ПЛАН ============
export const CurriculumService = {
  getDisciplines: () => api.get<DisciplineDto[]>('/disciplines').then((r) => r.data).catch(() => []),
  getDiscipline: (id: number) => api.get<DisciplineDto>(`/disciplines/${id}`).then((r) => r.data),
  createDiscipline: (data: DisciplineCreateDto) => api.post<DisciplineDto>('/disciplines', data).then((r) => r.data),
  updateDiscipline: (id: number, data: DisciplineUpdateDto) => api.put<DisciplineDto>(`/disciplines/${id}`, data).then((r) => r.data),
  deleteDiscipline: (id: number) => api.delete(`/disciplines/${id}`).then(() => {}),

  getCourses: (studyPeriodId?: number) =>
      api.get<DisciplineCourseDto[]>('/discipline-courses', {
        params: studyPeriodId != null ? { studyPeriodId } : undefined,
      }).then((r) => r.data),
  getCourse: (id: number) => api.get<DisciplineCourseDto>(`/discipline-courses/${id}`).then((r) => r.data),
  getCoursesByDiscipline: (disciplineId: number) => api.get<DisciplineCourseDto[]>(`/discipline-courses/by-discipline/${disciplineId}`).then((r) => r.data),
  createCourse: (data: DisciplineCourseCreateDto) => api.post<DisciplineCourseDto>('/discipline-courses', data).then((r) => r.data),
  updateCourse: (id: number, data: DisciplineCourseUpdateDto) => api.put<DisciplineCourseDto>(`/discipline-courses/${id}`, data).then((r) => r.data),
  getCourseDeletionImpact: (id: number) =>
    api.get<CourseDeletionImpactDto>(`/discipline-courses/${id}/deletion-impact`).then((r) => r.data),
  deleteCourse: (id: number) => api.delete(`/discipline-courses/${id}`).then(() => {}),
  cloneCourses: (data: CourseCloneRequestDto) => api.post<DisciplineCourseDto[]>('/discipline-courses/clone', data).then((r) => r.data),

  getSlotsByCourse: (courseId: number) => api.get<CurriculumSlotDto[]>(`/curriculum-slots/by-course/${courseId}`).then((r) => r.data),
  getSlot: (id: number) => api.get<CurriculumSlotDto>(`/curriculum-slots/${id}`).then((r) => r.data),
  createSlot: (data: CurriculumSlotCreateDto) => api.post<CurriculumSlotDto>('/curriculum-slots', data).then((r) => r.data),
  updateSlot: (id: number, data: CurriculumSlotUpdateDto) => api.put<CurriculumSlotDto>(`/curriculum-slots/${id}`, data).then((r) => r.data),
  /**
   * Цена удаления занятия плана: сколько назначений и уже размещённых занятий уйдёт каскадом
   * и сколько из них закреплено вручную (потеря ручной раскладки).
   */
  getSlotDeleteImpact: (id: number) =>
      api.get<SlotDeletionImpactDto>(`/curriculum-slots/${id}/delete-impact`).then((r) => r.data),
  deleteSlot: (id: number) => api.delete(`/curriculum-slots/${id}`).then(() => {}),

  getThemesByDiscipline: (disciplineId: number) => api.get<ThemeLessonDto[]>(`/theme-lessons/by-discipline/${disciplineId}`).then((r) => r.data),
  createTheme: (data: ThemeLessonCreateDto) => api.post<ThemeLessonDto>('/theme-lessons', data).then((r) => r.data),
  updateTheme: (id: number, data: ThemeLessonUpdateDto) => api.put<ThemeLessonDto>(`/theme-lessons/${id}`, data).then((r) => r.data),
  getSlotChains: () => api.get<SlotChainDto[]>('/slot-chains').then((r) => r.data),
  createChain: (data: SlotChainCreateDto) => api.post<SlotChainDto>('/slot-chains', data).then((r) => r.data),
  deleteChain: (id: number) => api.delete(`/slot-chains/${id}`).then(() => {}),
  getAssignmentsByCourse: (courseId: number) => api.get<AssignmentDto[]>(`/assignments/by-course/${courseId}`).then((r) => r.data).catch(() => []),
  createAssignment: (data: AssignmentCreateDto) => api.post<AssignmentDto[]>('/assignments', data).then((r) => r.data),
  applyAssignmentToCourse: (data: ApplyAssignmentToCourseDto) => api.post<AssignmentDto[]>('/assignments/apply-to-course', data).then((r) => r.data),
  getRemoveAssignmentsImpact: (data: RemoveAssignmentsFromCourseDto) =>
    api.post<RemoveAssignmentsImpactDto>('/assignments/remove-from-course/impact', data).then((r) => r.data),
  removeAssignmentsFromCourse: (data: RemoveAssignmentsFromCourseDto) =>
    api.post<number>('/assignments/remove-from-course', data).then((r) => r.data),
  updateAssignment: (id: number, data: AssignmentUpdateDto) => api.put<AssignmentDto>(`/assignments/${id}`, data).then((r) => r.data),
  getDeleteAssignmentImpact: (id: number) =>
    api.get<RemoveAssignmentsImpactDto>(`/assignments/${id}/delete-impact`).then((r) => r.data),
  deleteAssignment: (id: number) => api.delete(`/assignments/${id}`).then(() => {}),
};

// ============ 4. ОГРАНИЧЕНИЯ ============
export const ConstraintsService = {
  getEducatorConstraints: () => api.get<EducatorConstraintDto[]>('/educator-constraints').then((r) => r.data).catch(() => []),
  getEducatorConstraintsByEducator: (id: number) => api.get<EducatorConstraintDto[]>(`/educator-constraints/by-educator/${id}`).then((r) => r.data).catch(() => []),

  getGroupConstraints: () => api.get<GroupConstraintDto[]>('/group-constraints').then((r) => r.data).catch(() => []),
  getGroupConstraintsByGroup: (id: number) => api.get<GroupConstraintDto[]>(`/group-constraints/by-group/${id}`).then((r) => r.data).catch(() => []),

  getAuditoriumConstraints: () => api.get<AuditoriumConstraintDto[]>('/auditorium-constraints').then((r) => r.data).catch(() => []),
  getAuditoriumConstraintsByAuditorium: (id: number) => api.get<AuditoriumConstraintDto[]>(`/auditorium-constraints/by-auditorium/${id}`).then((r) => r.data).catch(() => []),

  createEducatorConstraint: (data: EducatorConstraintCreateDto) => api.post<EducatorConstraintDto>('/educator-constraints', data).then((r) => r.data),
  deleteEducatorConstraint: (id: number) => api.delete(`/educator-constraints/${id}`).then(() => {}),

  createGroupConstraint: (data: GroupConstraintCreateDto) => api.post<GroupConstraintDto>('/group-constraints', data).then((r) => r.data),
  deleteGroupConstraint: (id: number) => api.delete(`/group-constraints/${id}`).then(() => {}),

  createAuditoriumConstraint: (data: AuditoriumConstraintCreateDto) => api.post<AuditoriumConstraintDto>('/auditorium-constraints', data).then((r) => r.data),
  deleteAuditoriumConstraint: (id: number) => api.delete(`/auditorium-constraints/${id}`).then(() => {}),
};

// ============ 5. ГЕНЕРАЦИЯ ============
// Генерация расписания выполняется через CQRSService.generateSchedule (Command Side).
// Здесь остаётся только загрузка уже сохранённого расписания из БД.
export const ScheduleService = {
  /**
   * Загрузить существующее расписание из БД.
   * Используется при старте приложения для отображения уже сгенерированного расписания.
   */
  /**
   * Готовность периода: всего к размещению / размещено / не размещено.
   * «Всего» бэк берёт из набора генерации (query-сторона знает только размещённое).
   */
  getReadiness: (periodId: number): Promise<PeriodReadinessDto> =>
      api.get<PeriodReadinessDto>('/schedule/query/readiness', { params: { periodId } })
      .then((r) => r.data)
      .catch(() => ({ total: 0, placed: 0, unplaced: 0 })),

  /**
   * Здоровье проекции: сходится ли read-модель с write-стороной за период.
   * missing > 0 → часть занятий не доехала до сетки (асинхронная проекция отстала или упала);
   * лечится перепроекцией сессии (CQRSService.reproject).
   */
  getProjectionHealth: (periodId: number): Promise<ProjectionHealthDto | null> =>
      api.get<ProjectionHealthDto>('/schedule/query/projection-health', { params: { periodId } })
      .then((r) => r.data)
      .catch(() => null),

  /**
   * Здоровье аудиторий: не стоят ли двое в одной комнате и все ли помещаются.
   * Кнопки «починить» тут нет и быть не может: конфликт разрешается только переносом занятия
   * или сменой комнаты — это решение диспетчера, а не операция.
   */
  getAuditoriumHealth: (periodId: number): Promise<AuditoriumHealthDto | null> =>
      api.get<AuditoriumHealthDto>('/schedule/query/auditorium-health', { params: { periodId } })
      .then((r) => r.data)
      .catch(() => null),

  /** Качество расписания преподавателей за период: компактность + равномерность (суббота). */
  getEducatorQuality: (periodId: number): Promise<PeriodScheduleQualityDto | null> =>
      api.get<PeriodScheduleQualityDto>('/schedule/query/reports/educator-quality', { params: { periodId } })
      .then((r) => r.data)
      .catch(() => null),

  /** Загрузка аудиторий за период (утилизация): формат как у преподавателей. */
  getAuditoriumLoad: (periodId: number): Promise<PeriodAuditoriumLoadDto | null> =>
      api.get<PeriodAuditoriumLoadDto>('/schedule/query/reports/auditorium-load', { params: { periodId } })
      .then((r) => r.data)
      .catch(() => null),

  /**
   * Плотность групп в парах 1–3: спрос/размещено/остаток и ЧЕСТНАЯ свободная ёмкость
   * (закрытые пары и групповые ограничения учтены на бэке). Самые «забитые» группы первыми.
   */
  getGroupDensity: (periodId: number): Promise<GroupDensityDto[]> =>
      api.get<GroupDensityDto[]>('/schedule/query/reports/group-density', { params: { periodId } })
      .then((r) => r.data)
      .catch(() => []),

  /**
   * Выгрузка расписания периода из schedule_view (то, что реально размещено). С entityId — одна
   * книга .xlsx по сущности; без него — все сущности оси раздельными файлами (книга на каждую)
   * в ZIP-архиве. Бэк отдаёт файл вложением — здесь запускаем скачивание браузером; имя и тип
   * (xlsx/zip) берём из Content-Disposition, downloadBlob расширение не навязывает.
   */
  exportSchedule: async (periodId: number, axis: ExportAxis = 'GROUP', entityId?: number): Promise<void> => {
      const response = await api.get('/schedule/query/export', {
        params: { periodId, axis, ...(entityId != null ? { entityId } : {}) },
        responseType: 'blob',
      });
      const filename = filenameFromContentDisposition(response.headers['content-disposition']) ?? 'schedule.xlsx';
      downloadBlob(response.data as Blob, filename);
  },

  loadExisting: (startDate: string, endDate: string): Promise<ScheduleResultDto> =>
      api.get<ScheduleResultDto>('/schedule/query/all', {
        params: { start: startDate, end: endDate }
      })
      .then((r) => r.data)
      .catch(() => {
        // Если данных нет или ошибка - возвращаем пустой результат
        return {
          status: "empty",
          lessons: [],
          grid: {},
          placedCount: 0,
          unplacedCount: 0,
          startDate,
          endDate,
          totalSlots: 0,
          usedSlots: 0
        };
      }),
};
