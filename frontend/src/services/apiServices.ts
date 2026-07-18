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
  StudyStreamCreateDto, StudyStreamUpdateDto
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
            kindOfConstraints: EnumDto[];
            periodTypes: EnumDto[];
          }>('/enums/all')
          .then((r) => r.data),
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
   * Выгрузка расписания периода в Excel (из schedule_view — то, что реально размещено).
   * Без entityId выгружаются все сущности оси (лист на каждую). Бэк отдаёт файл вложением —
   * здесь запускаем скачивание браузером; имя берём из Content-Disposition.
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
