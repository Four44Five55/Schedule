import type {
  EducatorDto, EducatorCreateDto, EducatorUpdateDto,
  GroupDto, GroupCreateDto, GroupUpdateDto,
  AuditoriumDto, AuditoriumCreateDto, AuditoriumUpdateDto,
  DisciplineDto, DisciplineCreateDto, DisciplineUpdateDto,
  StudyPeriodDto, DisciplineCourseDto, CurriculumSlotDto,
  ThemeLessonDto, StudyStreamDto, StudyStreamCreateDto, StudyStreamUpdateDto,
  AssignmentDto, ScheduleResultDto,
} from '../types';

const BASE = '';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`API ${res.status}: ${text}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

// ── Educators ────────────────────────────────────
export const educatorsApi = {
  getAll: () => request<EducatorDto[]>('/api/educators'),
  getById: (id: number) => request<EducatorDto>(`/api/educators/${id}`),
  create: (dto: EducatorCreateDto) =>
      request<EducatorDto>('/api/educators', { method: 'POST', body: JSON.stringify(dto) }),
  update: (id: number, dto: EducatorUpdateDto) =>
      request<EducatorDto>(`/api/educators/${id}`, { method: 'PUT', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/educators/${id}`, { method: 'DELETE' }),
};

// ── Groups ───────────────────────────────────────
export const groupsApi = {
  getAll: () => request<GroupDto[]>('/api/groups'),
  getById: (id: number) => request<GroupDto>(`/api/groups/${id}`),
  create: (dto: GroupCreateDto) =>
      request<GroupDto>('/api/groups', { method: 'POST', body: JSON.stringify(dto) }),
  update: (id: number, dto: GroupUpdateDto) =>
      request<GroupDto>(`/api/groups/${id}`, { method: 'PUT', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/groups/${id}`, { method: 'DELETE' }),
};

// ── Auditoriums ─────────────────────────────────
export const auditoriumsApi = {
  getAll: () => request<AuditoriumDto[]>('/api/auditoriums'),
  create: (dto: AuditoriumCreateDto) =>
      request<AuditoriumDto>('/api/auditoriums', { method: 'POST', body: JSON.stringify(dto) }),
  update: (id: number, dto: AuditoriumUpdateDto) =>
      request<AuditoriumDto>(`/api/auditoriums/${id}`, { method: 'PUT', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/auditoriums/${id}`, { method: 'DELETE' }),
};

// ── Disciplines ─────────────────────────────────
export const disciplinesApi = {
  getAll: () => request<DisciplineDto[]>('/api/disciplines'),
  create: (dto: DisciplineCreateDto) =>
      request<DisciplineDto>('/api/disciplines', { method: 'POST', body: JSON.stringify(dto) }),
  update: (id: number, dto: DisciplineUpdateDto) =>
      request<DisciplineDto>(`/api/disciplines/${id}`, { method: 'PUT', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/disciplines/${id}`, { method: 'DELETE' }),
};

// ── Study Periods ───────────────────────────────
export const studyPeriodsApi = {
  getAll: () => request<StudyPeriodDto[]>('/api/study-periods'),
};

// ── Discipline Courses ──────────────────────────
export const disciplineCoursesApi = {
  getAll: () => request<any[]>('/api/discipline-courses'),
  getByDiscipline: (id: number) =>
      request<DisciplineCourseDto[]>(`/api/discipline-courses/by-discipline/${id}`),
};

// ── Curriculum Slots ────────────────────────────
export const curriculumSlotsApi = {
  getAll: () => request<any[]>('/api/curriculum-slots'),
  getByCourse: (id: number) =>
      request<CurriculumSlotDto[]>(`/api/curriculum-slots/by-course/${id}`),
};

// ── Theme Lessons ───────────────────────────────
export const themeLessonsApi = {
  getByDiscipline: (id: number) =>
      request<ThemeLessonDto[]>(`/api/theme-lessons/by-discipline/${id}`),
};

// ── Study Streams ───────────────────────────────
export const studyStreamsApi = {
  getAll: () => request<StudyStreamDto[]>('/api/study-streams'),
  create: (dto: StudyStreamCreateDto) =>
      request<StudyStreamDto>('/api/study-streams', { method: 'POST', body: JSON.stringify(dto) }),
  update: (id: number, dto: StudyStreamUpdateDto) =>
      request<StudyStreamDto>(`/api/study-streams/${id}`, { method: 'PUT', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/study-streams/${id}`, { method: 'DELETE' }),
};

// ── Locations, Buildings, Features, Purposes, Pools ──────────
export const locationsApi = {
  getAll: () => request<any[]>('/api/locations'),
  create: (dto: any) => request<any>('/api/locations', { method: 'POST', body: JSON.stringify(dto) }),
  update: (id: number, dto: any) => request<any>(`/api/locations/${id}`, { method: 'PUT', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/locations/${id}`, { method: 'DELETE' }),
};
export const buildingsApi = {
  getAll: () => request<any[]>('/api/buildings'),
  create: (dto: any) => request<any>('/api/buildings', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/buildings/${id}`, { method: 'DELETE' }),
};
export const featuresApi = {
  getAll: () => request<any[]>('/api/features'),
  create: (dto: any) => request<any>('/api/features', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/features/${id}`, { method: 'DELETE' }),
};
export const purposesApi = {
  getAll: () => request<any[]>('/api/auditorium-purposes'),
  create: (dto: any) => request<any>('/api/auditorium-purposes', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/auditorium-purposes/${id}`, { method: 'DELETE' }),
};
export const poolsApi = {
  getAll: () => request<any[]>('/api/auditorium-pools'),
  create: (dto: any) => request<any>('/api/auditorium-pools', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/auditorium-pools/${id}`, { method: 'DELETE' }),
};

// ── Assignments ─────────────────────────────────
export const assignmentsApi = {
  getAll: () => request<any[]>('/api/assignments'),
  getByCourse: (id: number) =>
      request<AssignmentDto[]>(`/api/assignments/by-course/${id}`),
};

// ── Constraints ─────────────────────────────────
export const educatorConstraintsApi = {
  getAll: () => request<any[]>('/api/educator-constraints'),
  create: (dto: any) => request<any>('/api/educator-constraints', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/educator-constraints/${id}`, { method: 'DELETE' }),
};
export const groupConstraintsApi = {
  getAll: () => request<any[]>('/api/group-constraints'),
  create: (dto: any) => request<any>('/api/group-constraints', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/group-constraints/${id}`, { method: 'DELETE' }),
};
export const auditoriumConstraintsApi = {
  getAll: () => request<any[]>('/api/auditorium-constraints'),
  create: (dto: any) => request<any>('/api/auditorium-constraints', { method: 'POST', body: JSON.stringify(dto) }),
  delete: (id: number) => request<void>(`/api/auditorium-constraints/${id}`, { method: 'DELETE' }),
};

// ── Schedule ────────────────────────────────────
export const scheduleApi = {
  generate: (courseIds: number[]) =>
      request<ScheduleResultDto>('/api/schedule/generate', { method: 'POST', body: JSON.stringify({ courseIds }) }),
};
