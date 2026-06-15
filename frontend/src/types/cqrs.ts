// ============ CQRS TYPES ============
// TypeScript типы для CQRS архитектуры (Command Query Responsibility Segregation)

/**
 * Статус сессии расписания
 */
export type SessionStatus =
  | 'INITIALIZED'      // Сессия создана, но не содержит данных
  | 'GENERATING'      // Идёт генерация расписания
  | 'READY_FOR_EDIT'  // Расписание сгенерировано, готово к редактированию
  | 'FINAL'            // Расписание финализировано
  | 'ARCHIVED';        // Сессия архивирована

/**
 * DTO сессии расписания (Command Side)
 */
export interface ScheduleSessionDto {
  id: string;
  name: string;
  status: SessionStatus;
  createdAt: string;           // ISO datetime
  createdBy: string;
  updatedAt: string;           // ISO datetime
  updatedBy: string;
  version: number;              // Оптимистичная блокировка
  placementsCount: number;      // Количество размещений
  hasWorkspaceSnapshot: boolean; // Есть ли snapshot workspace
}

/**
 * DTO размещения занятия (Command Side)
 */
export interface LessonPlacementDto {
  id: string;
  sessionId: string;
  assignmentId: number;
  scheduledDate: string;         // YYYY-MM-DD
  scheduledSlot: string;         // 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH'
  auditoriumIds: number[];
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

/**
 * DTO представления расписания (Query Side)
 * Денормализованные данные для быстрого чтения
 */
export interface ScheduleViewDto {
  id: string;
  scheduledDate: string;         // YYYY-MM-DD
  timeSlot: string;               // 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH'
  disciplineName: string;
  disciplineAbbr: string;
  educatorName: string;
  educatorId: number;
  groupName: string;
  studyStreamId: number;
  auditoriumName: string;
  auditoriumId: number;
  kindOfStudy: string;            // 'LECTURE' | 'PRACTICAL_WORK' | ...
  themeNumber: string;
  themeTitle: string;
  placementId: string;           // Ссылка на Command Side
  lastUpdated: string;            // ISO datetime
}

/**
 * Ответ при конфликте optimistic lock
 */
export interface ConflictResponse {
  error: string;                 // Например: 'OPTIMISTIC_LOCK_CONFLICT'
  message: string;               // Сообщение для пользователя
  currentVersion: number;       // Актуальная версия в БД
}

/**
 * Вариант для переноса занятия
 */
export interface MoveOptionDto {
  date: string;                   // YYYY-MM-DD
  timeSlot: string;               // 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH'
  auditoriumIds: number[];
  score: number;                  // Оценка качества (0-100)
}

/**
 * Запрос на создание сессии расписания
 */
export interface CreateScheduleSessionRequest {
  name: string;
  courseIds: number[];
}

/**
 * Запрос на перенос занятия
 */
export interface MoveLessonRequest {
  placementId: string;
  newDate: string;                // YYYY-MM-DD
  newSlot: string;                // 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH'
  auditoriumIds: number[];
  version: number;                // Оптимистичная блокировка
}

/**
 * Результат операции переноса
 */
export interface MoveLessonResult {
  success: boolean;
  newVersion?: number;
  conflict?: ConflictResponse;
}

/**
 * Запрос на поиск вариантов переноса
 */
export interface FindMoveOptionsRequest {
  sessionId: string;
  lessonId: number;
  rootEntityId: number;
  rootEntityType: string;          // 'EDUCATOR' | 'GROUP' | 'AUDITORIUM'
}
