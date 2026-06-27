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
 * Вариант для переноса занятия.
 * Бэкенд (ru.dto.moveLesson.MoveOptionDto) возвращает только дату и слот.
 */
export interface MoveOptionDto {
  date: string;                   // YYYY-MM-DD
  timeSlot: string;               // 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH'
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
  newAuditoriumIds: number[];     // имя поля как в бэкенд-DTO
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
  placementId: string;             // UUID размещения — надёжный уникальный ключ занятия
  rootEntityId: number;
  rootEntityType: string;          // 'EDUCATOR' | 'GROUP' | 'AUDITORIUM'
}

/**
 * Запрос на поиск стартовых ячеек для переноса цепочки.
 * Звенья — в порядке следования по времени (сверху вниз).
 */
export interface FindChainMoveOptionsRequest {
  placementIds: string[];
}

/**
 * Запрос на перенос цепочки занятий как единого целого.
 * Аудитории подбираются на бэке, поэтому их здесь нет.
 */
export interface MoveChainRequest {
  placementIds: string[];          // звенья в порядке следования по времени
  newStartDate: string;            // YYYY-MM-DD — день первого звена (весь день один)
  newStartSlot: string;            // пара первого звена; остальные — следом
  version: number;                 // оптимистичная блокировка
}
