// ============ CQRS API SERVICE ============
// API сервис для работы с CQRS архитектурой
// Command Side: запись, редактирование, оптимистичная блокировка
// Query Side: быстрое чтение, денормализованные данные

import api from './apiClient';
import {
  ScheduleSessionDto,
  LessonPlacementDto,
  ScheduleViewDto,
  ConflictResponse,
  MoveOptionDto,
  CreateScheduleSessionRequest,
  MoveLessonRequest,
  MoveLessonResult,
  FindMoveOptionsRequest,
  FindChainMoveOptionsRequest,
  MoveChainRequest,
  UnplacedLessonDto,
  PlacementBoardDto
} from '../types/cqrs';

/**
 * CQRS API Service
 *
 * Предоставляет методы для работы с Command Side (запись) и Query Side (чтение).
 * Все методы возвращают Promise с типизированными данными.
 */
export const CQRSService = {
  // ============================================================
  // COMMAND SIDE (Запись, Редактирование)
  // ============================================================

  /**
   * Создать новую пустую сессию расписания
   *
   * @param request - данные для создания сессии
   * @returns созданная сессия со статусом INITIALIZED
   */
  createSchedule: (request: CreateScheduleSessionRequest): Promise<ScheduleSessionDto> => {
    return api
      .post<ScheduleSessionDto>('/schedule/command/sessions', request)
      .then(r => r.data);
  },

  /**
   * Генерация расписания с сохранением в БД (CQRS Command Side)
   *
   * Процесс:
   * 1. Создаётся сессия со статусом GENERATING
   * 2. Генерируется workspace (существующий алгоритм)
   * 3. Извлекаются placements и сохраняются в БД
   * 4. Обновляется статус → READY_FOR_EDIT
   * 5. Публикуется событие для синхронизации Query Side
   *
   * @param request - название и курсы для генерации
   * @returns созданная сессия со статусом READY_FOR_EDIT
   */
  generateSchedule: (request: CreateScheduleSessionRequest): Promise<ScheduleSessionDto> => {
    return api
      .post<ScheduleSessionDto>('/schedule/command/sessions/generate', request)
      .then(r => r.data);
  },

  /**
   * Перегенерация расписания с сохранением закреплённых занятий (Фича 2, Фаза A).
   *
   * Закреплённые (locked) занятия остаются на местах, распределитель
   * перераскладывает остальное «вокруг» них.
   *
   * @param sessionId - ID перегенерируемой сессии (источник пинов)
   * @param request - период и курсы (как при обычной генерации)
   * @returns сессия со статусом READY_FOR_EDIT
   */
  regenerateKeepingLocked: (
    sessionId: string,
    request: CreateScheduleSessionRequest
  ): Promise<ScheduleSessionDto> => {
    return api
      .post<ScheduleSessionDto>(`/schedule/command/sessions/${sessionId}/regenerate`, request)
      .then(r => r.data);
  },

  /**
   * Закрепить/открепить занятие (пин, Фича 2). По умолчанию закрепляет всю цепочку занятия.
   *
   * @param placementId - UUID размещения (якорь)
   * @param locked - true закрепить, false открепить
   * @param placementIds - опционально: сузить действие до подмножества цепочки (эфемерный
   *   разрыв сцепки на фронте через detachedBoundaries/buildChain в AcademicGridSchedule).
   *   Бэк проверяет, что каждый id реально принадлежит цепочке якоря — просто игнорирует
   *   остальное; если не передано — старое поведение (вся цепочка).
   * @returns сессия-владелец (с актуальной version)
   */
  setLock: (placementId: string, locked: boolean, placementIds?: string[]): Promise<ScheduleSessionDto> => {
    return api
      .patch<ScheduleSessionDto>(`/schedule/command/placements/${placementId}/lock`, { locked, placementIds })
      .then(r => r.data);
  },

  /**
   * Аддитивная генерация одного курса (дисциплины): раскладывает только неразмещённые занятия
   * «вокруг» уже стоящих (инкрементальная сборка). Существующее не трогается.
   */
  generateCourse: (sessionId: string, studyPeriodId: number, courseId: number, kinds?: string[]): Promise<ScheduleSessionDto> => {
    return api
      .post<ScheduleSessionDto>(`/schedule/command/sessions/${sessionId}/generate-course`, { studyPeriodId, courseId, kinds })
      .then(r => r.data);
  },

  /**
   * Очистка размещений сессии, КРОМЕ закреплённых. courseId/kinds опциональны (пусто → всё).
   * @returns количество удалённых размещений
   */
  clearPlacements: (sessionId: string, body: { courseId?: number; kinds?: string[] }): Promise<number> => {
    return api
      .post<number>(`/schedule/command/sessions/${sessionId}/clear`, body)
      .then(r => r.data);
  },

  /**
   * Куда можно поставить ещё не размещённое занятие из палитры (Фича 2, Фаза B).
   * Зеркало findMoveOptions, но по assignmentId — занятие ещё не в сетке.
   */
  getPlacementOptions: (sessionId: string, request: {
    assignmentId: number;
    rootEntityType: 'GROUP' | 'EDUCATOR' | 'AUDITORIUM';
    rootEntityId?: number;
    studyPeriodId: number;
  }): Promise<MoveOptionDto[]> => {
    return api
      .post<MoveOptionDto[]>(`/schedule/command/sessions/${sessionId}/placement-options`, request)
      .then(r => r.data);
  },

  /**
   * Ручная установка занятия из палитры в слот (Фича 2, Фаза B).
   * Создаёт MANUAL/locked размещение; конфликт слота — HTTP 409 (AxiosError, ловить в компоненте).
   */
  createPlacement: (sessionId: string, request: {
    assignmentId: number;
    date: string;
    slot: string;
    studyPeriodId: number;
  }): Promise<ScheduleSessionDto> => {
    return api
      .post<ScheduleSessionDto>(`/schedule/command/sessions/${sessionId}/placements`, request)
      .then(r => r.data);
  },

  /**
   * Снять размещение (вернуть занятие в палитру неразмещённых).
   */
  deletePlacement: (placementId: string): Promise<ScheduleSessionDto> => {
    return api
      .delete<ScheduleSessionDto>(`/schedule/command/placements/${placementId}`)
      .then(r => r.data);
  },

  /**
   * Получить/создать рабочую сессию для учебного периода (Путь 2, Фаза B).
   *
   * Возвращает живую сессию периода или создаёт пустой черновик — «вход» для
   * ручной раскладки семестра, для которого расписание ещё не генерировалось.
   */
  getSessionForPeriod: (studyPeriodId: number): Promise<ScheduleSessionDto> => {
    return api
      .post<ScheduleSessionDto>(`/schedule/command/sessions/for-period/${studyPeriodId}`)
      .then(r => r.data);
  },

  /**
   * Неразмещённые занятия выбранных курсов (палитра ручной раскладки, Фаза B).
   */
  getUnplaced: (sessionId: string, courseIds: number[]): Promise<UnplacedLessonDto[]> => {
    return api
      .get<UnplacedLessonDto[]>(`/schedule/command/sessions/${sessionId}/unplaced`, {
        params: { courseIds: courseIds.join(',') }
      })
      .then(r => r.data);
  },

  /**
   * Доска раскладки: все сущности выбранных курсов со счётчиками total/placed/unplaced
   * (сущность → дисциплина → занятие). Показывает и полностью размещённые/сгенерированные
   * сущности, в отличие от getUnplaced (только очередь). Ось = группы/преподаватели.
   */
  getPlacementBoard: (
    sessionId: string, courseIds: number[], axis: 'GROUP' | 'EDUCATOR'
  ): Promise<PlacementBoardDto> => {
    return api
      .get<PlacementBoardDto>(`/schedule/command/sessions/${sessionId}/placement-board`, {
        params: { courseIds: courseIds.join(','), axis }
      })
      .then(r => r.data);
  },

  /**
   * Получить сессию по ID
   *
   * @param sessionId - уникальный идентификатор сессии
   * @returns данные сессии
   */
  getSession: (sessionId: string): Promise<ScheduleSessionDto> => {
    return api
      .get<ScheduleSessionDto>(`/schedule/command/sessions/${sessionId}`)
      .then(r => r.data);
  },

  /**
   * Получить сессию для редактирования «живого» расписания.
   *
   * Находит сессию текущего расписания и при необходимости переоткрывает её
   * для редактирования — без повторной генерации. Возвращает null, если
   * расписания ещё нет (HTTP 204).
   */
  getEditableSession: (): Promise<ScheduleSessionDto | null> => {
    return api
      .post<ScheduleSessionDto>('/schedule/command/sessions/editable')
      .then(r => (r.status === 204 ? null : r.data))
      .catch(() => null);
  },

  /**
   * Перенести занятие с optimistic lock (версионированием)
   *
   * ВАЖНО: Всегда передавайте актуальную версию (session.version)
   *
   * @param sessionId - ID сессии
   * @param request - данные для переноса с версией
   * @returns результат операции:
   *   - success: true, newVersion: N - успешный перенос
   *   - success: false, conflict: ConflictResponse - конфликт версий
   */
  moveLesson: async (
    sessionId: string,
    request: MoveLessonRequest
  ): Promise<MoveLessonResult> => {
    try {
      // Бэкенд возвращает ScheduleSessionDto (с актуальной version), а НЕ {success}.
      // Переводим успешный 2xx-ответ в контракт MoveLessonResult здесь, в сервисном
      // слое — иначе result.success === undefined и UI не узнаёт об успехе переноса.
      const response = await api.post<ScheduleSessionDto>(
        `/schedule/command/sessions/${sessionId}/move-lesson`,
        request
      );

      return { success: true, newVersion: response.data.version };
    } catch (error: any) {
      // Обработка HTTP 409 Conflict
      if (error.response?.status === 409) {
        return {
          success: false,
          conflict: error.response.data as ConflictResponse
        };
      }
      throw error;
    }
  },

  /**
   * Пересортировка трека в порядок плана — вызывается ПОСЛЕ переноса. Занятия класса
   * якорного размещения (тот же курс+поток+преподаватели) возвращаются в порядок изучения:
   * перенесённое «пузырьком» встаёт на плановое место, соседи сдвигаются на ячейку. Меняются
   * только даты — тема едет с занятием.
   *
   * @param placementId - только что перенесённое размещение (определяет класс)
   * @returns сессия (с актуальной version) и список распавшихся сцепок (problems)
   */
  reorder: (
    placementId: string
  ): Promise<{ session: ScheduleSessionDto; problems: { placementId: string; reason: string }[] }> => {
    return api
      .post<{ session: ScheduleSessionDto; problems: { placementId: string; reason: string }[] }>(
        `/schedule/command/placements/${placementId}/reorder`
      )
      .then(r => r.data);
  },

  /**
   * Получить все размещения сессии
   *
   * @param sessionId - ID сессии
   * @returns список всех размещений в сессии
   */
  getPlacements: (sessionId: string): Promise<LessonPlacementDto[]> => {
    return api
      .get<LessonPlacementDto[]>(`/schedule/command/sessions/${sessionId}/placements`)
      .then(r => r.data);
  },

  /**
   * Удалить сессию
   *
   * @param sessionId - ID сессии для удаления
   */
  deleteSession: (sessionId: string): Promise<void> => {
    return api
      .delete(`/schedule/command/sessions/${sessionId}`);
  },

  // ============================================================
  // QUERY SIDE (Чтение, быстрые запросы)
  // ============================================================

  /**
   * Получить расписание для студента/группы (быстро!)
   *
   * Использует schedule_view с индексами (8-12ms)
   *
   * @param streamId - ID потока/группы
   * @param startDate - начальная дата (YYYY-MM-DD)
   * @param endDate - конечная дата (YYYY-MM-DD)
   * @returns расписание группы (денормализованные данные)
   */
  getStudentSchedule: (
    streamId: number,
    startDate: string,
    endDate: string
  ): Promise<ScheduleViewDto[]> => {
    return api
      .get<ScheduleViewDto[]>(
        `/schedule/query/student/${streamId}?start=${startDate}&end=${endDate}`
      )
      .then(r => r.data);
  },

  /**
   * Получить расписание преподавателя на дату
   *
   * @param educatorId - ID преподавателя
   * @param date - дата (YYYY-MM-DD)
   * @returns расписание преподавателя на дату
   */
  getEducatorSchedule: (
    educatorId: number,
    date: string
  ): Promise<ScheduleViewDto[]> => {
    return api
      .get<ScheduleViewDto[]>(
        `/schedule/query/educator/${educatorId}?date=${date}`
      )
      .then(r => r.data);
  },

  /**
   * Проверить свободность аудитории
   *
   * @param auditoriumId - ID аудитории
   * @param date - дата (YYYY-MM-DD)
   * @param slot - временной слот ('FIRST' | 'SECOND' | 'THIRD' | 'FOURTH')
   * @returns true если аудитория свободна, false если занята
   */
  checkAuditoriumFree: (
    auditoriumId: number,
    date: string,
    slot: string
  ): Promise<boolean> => {
    return api
      .get<boolean>(
        `/schedule/query/check-auditorium?auditoriumId=${auditoriumId}&date=${date}&slot=${slot}`
      )
      .then(r => r.data);
  },

  // ============================================================
  // MOVE OPTIONS (Поиск вариантов переноса)
  // ============================================================

  /**
   * Найти варианты для переноса занятия
   *
   * Восстанавливает workspace из сессии и ищет свободные слоты
   * с учётом всех ограничений преподавателя/группы/аудитории.
   *
   * @param request - параметры поиска
   * @returns отсортированные варианты переноса (по score)
   */
  findMoveOptions: (request: FindMoveOptionsRequest): Promise<MoveOptionDto[]> => {
    return api
      .post<MoveOptionDto[]>('/schedule/find-move-options', request)
      .then(r => r.data);
  },

  /**
   * Найти стартовые ячейки, куда помещается вся цепочка занятий.
   */
  findChainMoveOptions: (request: FindChainMoveOptionsRequest): Promise<MoveOptionDto[]> => {
    return api
      .post<MoveOptionDto[]>('/schedule/find-chain-move-options', request)
      .then(r => r.data);
  },

  /**
   * Перенести цепочку занятий как единое целое (с optimistic lock).
   * Возвращает контракт MoveLessonResult: success+newVersion либо conflict (409).
   */
  moveChain: async (
    sessionId: string,
    request: MoveChainRequest
  ): Promise<MoveLessonResult> => {
    try {
      const response = await api.post<ScheduleSessionDto>(
        `/schedule/command/sessions/${sessionId}/move-chain`,
        request
      );
      return { success: true, newVersion: response.data.version };
    } catch (error: any) {
      if (error.response?.status === 409) {
        return { success: false, conflict: error.response.data as ConflictResponse };
      }
      throw error;
    }
  },
};

/**
 * Вспомогательные функции для форматирования дат
 */
export const dateUtils = {
  /**
   * Конвертировать Date в YYYY-MM-DD
   */
  formatDate: (date: Date): string => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  },

  /**
   * Конвертировать строку YYYY-MM-DD в Date
   */
  parseDate: (dateStr: string): Date => {
    const [year, month, day] = dateStr.split('-').map(Number);
    return new Date(year, month - 1, day);
  },
};
