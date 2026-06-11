import api from './apiClient';
import {
  EducatorDto,
  AuditoriumDto,
  GroupDto,
  DisciplineDto,
  EnumDto,
  StudyStreamDto,
  CurriculumSlotDto,
  DisciplineCourseDto,
  ThemeLessonDto,
  SlotChainDto,
  AssignmentDto,
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
  getEducators: () => api.get<EducatorDto[]>('/educators').then((r) => r.data),
  getEducator: (id: number) => api.get<EducatorDto>(`/educators/${id}`).then((r) => r.data),
  createEducator: (data: Partial<EducatorDto>) => api.post<EducatorDto>('/educators', data).then((r) => r.data),
  updateEducator: (id: number, data: Partial<EducatorDto>) => api.put<EducatorDto>(`/educators/${id}`, data).then((r) => r.data),
  deleteEducator: (id: number) => api.delete(`/educators/${id}`),

  getAuditoriums: () => api.get<AuditoriumDto[]>('/auditoriums').then((r) => r.data),
  getAuditorium: (id: number) => api.get<AuditoriumDto>(`/auditoriums/${id}`).then((r) => r.data),
  createAuditorium: (data: any) => api.post<AuditoriumDto>('/auditoriums', data).then((r) => r.data),
  updateAuditorium: (id: number, data: any) => api.put<AuditoriumDto>(`/auditoriums/${id}`, data).then((r) => r.data),
  deleteAuditorium: (id: number) => api.delete(`/auditoriums/${id}`),

  getGroups: () => api.get<GroupDto[]>('/groups').then((r) => r.data),
  getGroup: (id: number) => api.get<GroupDto>(`/groups/${id}`).then((r) => r.data),
  createGroup: (data: any) => api.post<GroupDto>('/groups', data).then((r) => r.data),
  updateGroup: (id: number, data: any) => api.put<GroupDto>(`/groups/${id}`, data).then((r) => r.data),
  deleteGroup: (id: number) => api.delete(`/groups/${id}`),

  getStreams: () => api.get<StudyStreamDto[]>('/study-streams').then((r) => r.data),
  getLocations: () => api.get<LocationDto[]>('/locations').then((r) => r.data),
  getBuildings: () => api.get<BuildingDto[]>('/buildings').then((r) => r.data),
  getFeatures: () => api.get<FeatureDto[]>('/features').then((r) => r.data),
  getAuditoriumPurposes: () => api.get<AuditoriumPurposeDto[]>('/auditorium-purposes').then((r) => r.data),
  getAuditoriumPools: () => api.get<AuditoriumPoolDto[]>('/auditorium-pools').then((r) => r.data),
  getStudyPeriods: () => api.get<StudyPeriodDto[]>('/study-periods').then((r) => r.data),
};

// ============ 3. УЧЕБНЫЙ ПЛАН ============
export const CurriculumService = {
  getDisciplines: () => api.get<DisciplineDto[]>('/disciplines').then((r) => r.data),
  getCourses: () => api.get<DisciplineCourseDto[]>('/discipline-courses').then((r) => r.data),
  getCoursesByDiscipline: (disciplineId: number) => api.get<DisciplineCourseDto[]>(`/discipline-courses/by-discipline/${disciplineId}`).then((r) => r.data),
  getSlotsByCourse: (courseId: number) => api.get<CurriculumSlotDto[]>(`/curriculum-slots/by-course/${courseId}`).then((r) => r.data),
  getThemesByDiscipline: (disciplineId: number) => api.get<ThemeLessonDto[]>(`/theme-lessons/by-discipline/${disciplineId}`).then((r) => r.data),
  getSlotChains: () => api.get<SlotChainDto[]>('/slot-chains').then((r) => r.data),
  getAssignmentsByCourse: (courseId: number) => api.get<AssignmentDto[]>(`/assignments/by-course/${courseId}`).then((r) => r.data),
};

// ============ 4. ОГРАНИЧЕНИЯ ============
export const ConstraintsService = {
  getEducatorConstraints: () => api.get<EducatorConstraintDto[]>('/educator-constraints').then((r) => r.data),
  getEducatorConstraintsByEducator: (id: number) => api.get<EducatorConstraintDto[]>(`/educator-constraints/by-educator/${id}`).then((r) => r.data),

  getGroupConstraints: () => api.get<GroupConstraintDto[]>('/group-constraints').then((r) => r.data),
  getGroupConstraintsByGroup: (id: number) => api.get<GroupConstraintDto[]>(`/group-constraints/by-group/${id}`).then((r) => r.data),

  getAuditoriumConstraints: () => api.get<AuditoriumConstraintDto[]>('/auditorium-constraints').then((r) => r.data),
  getAuditoriumConstraintsByAuditorium: (id: number) => api.get<AuditoriumConstraintDto[]>(`/auditorium-constraints/by-auditorium/${id}`).then((r) => r.data),
};

// ============ 5. ГЕНЕРАЦИЯ ============
export const ScheduleService = {
  generateBatch: (courseIds: number[]) =>
      api.post<ScheduleResultDto>('/schedule/generate', { courseIds }).then((r) => r.data),
  generateSingle: (courseId: number) =>
      api.post<ScheduleResultDto>(`/schedule/generate/${courseId}`).then((r) => r.data),
};
