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
  EnumDto,
  StudyStreamDto,
  CurriculumSlotDto,
  CurriculumSlotCreateDto,
  CurriculumSlotUpdateDto,
  ThemeLessonDto,
  SlotChainDto,
  AssignmentDto,
  AssignmentCreateDto,
  AssignmentUpdateDto,
  LocationDto,
  BuildingDto,
  FeatureDto,
  AuditoriumPurposeDto,
  AuditoriumPoolDto,
  StudyPeriodDto,
  EducatorConstraintDto,
  GroupConstraintDto,
  AuditoriumConstraintDto,
  ScheduleResultDto,
  StudyStreamCreateDto, StudyStreamUpdateDto
} from '../types/api';

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
  getBuildings: () => api.get<BuildingDto[]>('/buildings').then((r) => r.data),
  getFeatures: () => api.get<FeatureDto[]>('/features').then((r) => r.data),
  getAuditoriumPurposes: () => api.get<AuditoriumPurposeDto[]>('/auditorium-purposes').then((r) => r.data),
  getAuditoriumPools: () => api.get<AuditoriumPoolDto[]>('/auditorium-pools').then((r) => r.data),
  getStudyPeriods: () => api.get<StudyPeriodDto[]>('/study-periods').then((r) => r.data),

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

  getCourses: () => api.get<DisciplineCourseDto[]>('/discipline-courses').then((r) => r.data),
  getCourse: (id: number) => api.get<DisciplineCourseDto>(`/discipline-courses/${id}`).then((r) => r.data),
  getCoursesByDiscipline: (disciplineId: number) => api.get<DisciplineCourseDto[]>(`/discipline-courses/by-discipline/${disciplineId}`).then((r) => r.data),
  createCourse: (data: DisciplineCourseCreateDto) => api.post<DisciplineCourseDto>('/discipline-courses', data).then((r) => r.data),
  updateCourse: (id: number, data: DisciplineCourseUpdateDto) => api.put<DisciplineCourseDto>(`/discipline-courses/${id}`, data).then((r) => r.data),
  deleteCourse: (id: number) => api.delete(`/discipline-courses/${id}`).then(() => {}),

  getSlotsByCourse: (courseId: number) => api.get<CurriculumSlotDto[]>(`/curriculum-slots/by-course/${courseId}`).then((r) => r.data),
  getSlot: (id: number) => api.get<CurriculumSlotDto>(`/curriculum-slots/${id}`).then((r) => r.data),
  createSlot: (data: CurriculumSlotCreateDto) => api.post<CurriculumSlotDto>('/curriculum-slots', data).then((r) => r.data),
  updateSlot: (id: number, data: CurriculumSlotUpdateDto) => api.put<CurriculumSlotDto>(`/curriculum-slots/${id}`, data).then((r) => r.data),
  deleteSlot: (id: number) => api.delete(`/curriculum-slots/${id}`).then(() => {}),

  getThemesByDiscipline: (disciplineId: number) => api.get<ThemeLessonDto[]>(`/theme-lessons/by-discipline/${disciplineId}`).then((r) => r.data),
  getSlotChains: () => api.get<SlotChainDto[]>('/slot-chains').then((r) => r.data),
  getAssignmentsByCourse: (courseId: number) => api.get<AssignmentDto[]>(`/assignments/by-course/${courseId}`).then((r) => r.data).catch(() => []),
  createAssignment: (data: AssignmentCreateDto) => api.post<AssignmentDto>('/assignments', data).then((r) => r.data),
  updateAssignment: (id: number, data: AssignmentUpdateDto) => api.put<AssignmentDto>(`/assignments/${id}`, data).then((r) => r.data),
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
};

// ============ 5. ГЕНЕРАЦИЯ ============
// Генерация расписания выполняется через CQRSService.generateSchedule (Command Side).
// Здесь остаётся только загрузка уже сохранённого расписания из БД.
export const ScheduleService = {
  /**
   * Загрузить существующее расписание из БД.
   * Используется при старте приложения для отображения уже сгенерированного расписания.
   */
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
