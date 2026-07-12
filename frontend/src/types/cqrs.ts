// ============ CQRS TYPES ============
// TypeScript типы для CQRS архитектуры (Command Query Responsibility Segregation)

import type { TimeSlotPair } from './api';

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
 * Неразмещённое занятие для палитры ручной раскладки (Фаза B).
 */
export interface UnplacedLessonDto {
  assignmentId: number;
  curriculumSlotId: number;
  courseId: number;
  disciplineName: string;
  disciplineAbbreviation: string;
  kindOfStudy: string;
  kindOfStudyName: string;
  kindOfStudyAbbr: string;
  position: number;
  themeNumber?: string;
  themeTitle?: string;
  studyStreamId: number;
  streamName: string;
  groupIds: number[];
  groupNames: string[];
  educatorIds: number[];
  educatorNames: string[];
}

/**
 * Занятие на «доске раскладки» (Фаза B). Поля размещения (placementId/date/slot/locked/source)
 * заполнены у размещённых и null у тех, что ещё в очереди — фронт делит список по placementId.
 */
export interface BoardLessonDto {
  assignmentId: number;
  courseId: number;
  curriculumSlotId: number;
  kindOfStudy: string;
  kindOfStudyAbbr: string;
  position: number;
  themeNumber?: string | null;
  themeTitle?: string | null;
  studyStreamId: number;
  streamName: string;
  groupIds: number[];
  groupNames: string[];
  educatorIds: number[];
  educatorNames: string[];
  placementId?: string | null;
  date?: string | null;
  slot?: string | null;
  locked?: boolean | null;
  source?: string | null;
}

/** Узел «дисциплина» доски раскладки: счётчики + занятия сущности по дисциплине. */
export interface DisciplinePlacementDto {
  courseId: number;
  abbreviation: string;
  name: string;
  total: number;
  placed: number;
  unplaced: number;
  lessons: BoardLessonDto[];
}

/** Узел «сущность» (группа/преподаватель) доски раскладки: счётчики + разбивка по дисциплинам. */
export interface EntityPlacementDto {
  id: number;
  name: string;
  total: number;
  placed: number;
  unplaced: number;
  disciplines: DisciplinePlacementDto[];
}

/**
 * «Доска раскладки» — дерево сущность→дисциплина→занятие со счётчиками total/placed/unplaced.
 * Строится на бэке из полного набора назначений курсов, поэтому показывает и полностью
 * размещённые/сгенерированные сущности; заголовочные счётчики считают назначения (не зависят от оси).
 */
export interface PlacementBoardDto {
  total: number;
  placed: number;
  unplaced: number;
  entities: EntityPlacementDto[];
}

/** Лёгкий счётчик «распределено N/M» по курсу (вкладка генерации). */
export interface CoursePlacementCountDto {
  courseId: number;
  total: number;
  placed: number;
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
  timeSlot: TimeSlotPair;         // 'FIRST' | 'SECOND' | 'THIRD' | 'FOURTH'
}

/**
 * Запрос на создание сессии расписания
 */
export interface CreateScheduleSessionRequest {
  name: string;
  /** Учебный период генерации — источник дат и набора курсов. */
  studyPeriodId: number;
  /** Опциональный поднабор курсов периода; пусто — все курсы периода. */
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

/**
 * Вид находки правила порядка изучения.
 * - BEFORE_LECTURE — занятие стоит РАНЬШЕ предшествующей ему по плану лекции (ошибка).
 * - FAR_FROM_LECTURE — занятие стоит слишком ДАЛЕКО после неё (предупреждение; порог —
 *   настройка бэка `schedule.order.max-lecture-gap-days`, аттестации из проверки исключены).
 */
export type OrderViolationKind = 'BEFORE_LECTURE' | 'FAR_FROM_LECTURE';

/**
 * Находка правила порядка изучения (лекция поз. 20 → практика поз. 21).
 *
 * Это подсказка, а не запрет: расписание валидно по ресурсам, перенос/установка не
 * блокируются — сетка лишь штрихует занятие (красным ошибку, янтарным отрыв).
 */
export interface OrderViolationDto {
  placementId: string;         // занятие
  lecturePlacementId: string;  // лекция-причина
  groupId: number;             // группа, в чьей дорожке видна находка
  kind: OrderViolationKind;
  gapDays: number;             // дней от лекции (осмысленно для FAR_FROM_LECTURE)
}

/** Находка по занятию — то, что сетка держит в памяти для подсветки. */
export interface OrderFinding {
  kind: OrderViolationKind;
  gapDays: number;
}
