# REST API Examples - CQRS Architecture

## 📋 Overview

Документ содержит примеры REST API запросов для **CQRS архитектуры**:
- **Query Side** - быстрые запросы на чтение
- **Command Side** - операции записи с оптимистичной блокировкой

**Базовый URL:** `http://localhost:8080/api/schedule`

---

## 🔍 QUERY SIDE: Чтение расписания

### 1. Расписание для студента (группы)

**Endpoint:** `GET /query/student/{streamId}`

**Параметры:**
- `streamId` (path) - ID потока/подгруппы
- `start` (query) - Начальная дата (YYYY-MM-DD)
- `end` (query) - Конечная дата (YYYY-MM-DD)

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/student/123?start=2025-01-11&end=2025-01-17" \
  -H "Accept: application/json"
```

**Ответ:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "scheduledDate": "2025-01-11",
    "timeSlot": "FIRST",
    "disciplineName": "Математический анализ",
    "disciplineAbbr": "МаТе",
    "educatorName": "Иванов И.И.",
    "educatorId": 456,
    "groupName": "ИБ-21",
    "studyStreamId": 123,
    "kindOfStudy": "LECTURE",
    "auditoriumName": "Аудитория 301",
    "auditoriumId": 789,
    "themeNumber": "1",
    "themeTitle": "Введение в анализ",
    "placementId": "550e8400-e29b-41d4-a716-446655440000",
    "lastUpdated": "2025-01-11T10:30:00"
  },
  // ... остальные занятия
]
```

**SQL (под капотом):**
```sql
SELECT * FROM schedule_view
WHERE study_stream_id = 123
  AND scheduled_date BETWEEN '2025-01-11' AND '2025-01-17'
ORDER BY scheduled_date, time_slot;
-- ✅ Использует индексы: idx_view_group, idx_view_date
-- ⏱️ Время: ~10ms
```

---

### 2. Расписание для преподавателя на дату

**Endpoint:** `GET /query/educator/{educatorId}`

**Параметры:**
- `educatorId` (path) - ID преподавателя
- `date` (query) - Дата (YYYY-MM-DD)

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/educator/456?date=2025-01-12" \
  -H "Accept: application/json"
```

**Ответ:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "scheduledDate": "2025-01-12",
    "timeSlot": "FIRST",
    "disciplineAbbr": "МаТе",
    "educatorName": "Иванов И.И.",
    "groupName": "ИБ-21",
    "auditoriumName": "Аудитория 301"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "scheduledDate": "2025-01-12",
    "timeSlot": "SECOND",
    "disciplineAbbr": "Алгебра",
    "educatorName": "Иванов И.И.",
    "groupName": "ИБ-22",
    "auditoriumName": "Аудитория 302"
  }
]
```

---

### 3. Расписание преподавателя на период

**Endpoint:** `GET /query/educator/{educatorId}/period`

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/educator/456/period?start=2025-01-11&end=2025-01-17" \
  -H "Accept: application/json"
```

---

### 4. Расписание в аудитории на дату

**Endpoint:** `GET /query/auditorium/{auditoriumId}`

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/auditorium/789?date=2025-01-12" \
  -H "Accept: application/json"
```

**Ответ:**
```json
[
  {
    "id": "...",
    "scheduledDate": "2025-01-12",
    "timeSlot": "FIRST",
    "disciplineAbbr": "МаТе",
    "educatorName": "Иванов И.И.",
    "groupName": "ИБ-21",
    "auditoriumName": "Аудитория 301"
  },
  {
    "id": "...",
    "scheduledDate": "2025-01-12",
    "timeSlot": "SECOND",
    "disciplineAbbr": "Физика",
    "educatorName": "Петров П.П.",
    "groupName": "ИБ-23",
    "auditoriumName": "Аудитория 301"
  }
]
```

---

### 5. Проверить свободность аудитории

**Endpoint:** `GET /query/check-auditorium`

**Параметры:**
- `auditoriumId` - ID аудитории
- `date` - Дата (YYYY-MM-DD)
- `slot` - Временной слот (FIRST, SECOND)

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/check-auditorium?auditoriumId=789&date=2025-01-12&slot=FIRST" \
  -H "Accept: application/json"
```

**Ответ:**
```json
true  // ✅ Свободна
```
или
```json
false  // ❌ Занята
```

---

### 6. Отчёт по загруженности аудиторий

**Endpoint:** `GET /query/reports/auditorium-utilization`

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/reports/auditorium-utilization" \
  -H "Accept: application/json"
```

**Ответ:**
```json
[
  [789, 15, "2025-01-11"],      // [auditoriumId, count, date]
  [790, 12, "2025-01-11"],
  [791, 18, "2025-01-11"],
  [789, 14, "2025-01-12"],
  // ...
]
```

---

### 7. Отчёт по загруженности преподавателей

**Endpoint:** `GET /query/reports/educator-load`

**Параметры:**
- `start` - Начальная дата
- `end` - Конечная дата

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/reports/educator-load?start=2025-01-11&end=2025-01-17" \
  -H "Accept: application/json"
```

**Ответ:**
```json
[
  [456, "Иванов И.И.", 20, "2025-01-11"],  // [educatorId, name, count, date]
  [457, "Петров П.П.", 18, "2025-01-11"],
  [458, "Сидоров С.С.", 22, "2025-01-11"],
  // ...
]
```

---

## 💾 COMMAND SIDE: Запись и редактирование

### 1. Создать новую сессию

**Endpoint:** `POST /api/schedule/sessions`

**Тело запроса:**
```json
{
  "name": "Расписание 2025 весна",
  "courseIds": [701, 702, 703]
}
```

**Пример:**
```bash
curl -X POST "http://localhost:8080/api/schedule/sessions" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Расписание 2025 весна",
    "courseIds": [701, 702, 703]
  }'
```

**Ответ:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Расписание 2025 весна",
  "status": "INITIALIZED",
  "createdAt": "2025-01-11T10:00:00",
  "createdBy": "admin",
  "version": 0
}
```

---

### 2. Генерация расписания (с персистентностью)

**Endpoint:** `POST /api/schedule/generate-with-persistence`

**Тело запроса:**
```json
{
  "name": "Расписание 2025 весна",
  "courseIds": [701, 702, 703]
}
```

**Пример:**
```bash
curl -X POST "http://localhost:8080/api/schedule/generate-with-persistence" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Расписание 2025 весна",
    "courseIds": [701, 702, 703]
  }'
```

**Ответ:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Расписание 2025 весна",
  "status": "READY_FOR_EDIT",
  "createdAt": "2025-01-11T10:00:00",
  "createdBy": "admin",
  "updatedAt": "2025-01-11T10:05:00",
  "updatedBy": "admin",
  "version": 1,
  "placementsCount": 150
}
```

**Процесс:**
```
1. Создаётся ScheduleSession (status=GENERATING)
2. Генерируется ScheduleWorkspace (существующий алгоритм)
3. Извлекаются LessonPlacement из workspace
4. Сохраняются в БД (Command Side)
5. ✅ Публикуется ScheduleGeneratedEvent
6. Query Side синхронизируется (асинхронно)
```

---

### 3. Перенести занятие (с optimistic lock)

**Endpoint:** `POST /api/schedule/sessions/{sessionId}/move-lesson`

**Тело запроса:**
```json
{
  "placementId": "550e8400-e29b-41d4-a716-446655440000",
  "newDate": "2025-01-15",
  "newSlot": "SECOND",
  "auditoriumIds": [789, 790],
  "version": 5
}
```

**Пример:**
```bash
curl -X POST "http://localhost:8080/api/schedule/sessions/550e8400-e29b-41d4-a716-446655440000/move-lesson" \
  -H "Content-Type: application/json" \
  -d '{
    "placementId": "550e8400-e29b-41d4-a716-446655440000",
    "newDate": "2025-01-15",
    "newSlot": "SECOND",
    "auditoriumIds": [789, 790],
    "version": 5
  }'
```

**Ответ (SUCCESS):**
```json
{
  "success": true,
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "newVersion": 6
}
```

**Ответ (CONFLICT):**
```json
{
  "success": false,
  "error": "CONFLICT",
  "message": "Расписание было изменено другим пользователем. Обновите страницу.",
  "currentVersion": 7
}
```

**Процесс:**
```
1. Загружается сессия с optimistic lock
2. Проверяется version (должна совпадать)
3. Обновляется LessonPlacement
4. Version автоматически увеличивается: 5 → 6
5. ✅ Публикуется PlacementChangedEvent
6. Query Side синхронизируется (асинхронно)
```

---

### 4. Найти варианты для переноса

**Endpoint:** `POST /api/schedule/sessions/{sessionId}/find-move-options`

**Тело запроса:**
```json
{
  "placementId": "550e8400-e29b-41d4-a716-446655440000",
  "rootEntityId": 456,
  "rootEntityType": "EDUCATOR"
}
```

**Пример:**
```bash
curl -X POST "http://localhost:8080/api/schedule/sessions/550e8400-e29b-41d4-a716-446655440000/find-move-options" \
  -H "Content-Type: application/json" \
  -d '{
    "placementId": "550e8400-e29b-41d4-a716-446655440000",
    "rootEntityId": 456,
    "rootEntityType": "EDUCATOR"
  }'
```

**Ответ:**
```json
[
  {
    "date": "2025-01-13",
    "timeSlot": "FIRST",
    "auditoriumIds": [789, 790],
    "score": 95
  },
  {
    "date": "2025-01-14",
    "timeSlot": "SECOND",
    "auditoriumIds": [791],
    "score": 88
  },
  // ... остальные варианты
]
```

---

### 5. Получить сессию

**Endpoint:** `GET /api/schedule/sessions/{sessionId}`

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/sessions/550e8400-e29b-41d4-a716-446655440000" \
  -H "Accept: application/json"
```

**Ответ:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Расписание 2025 весна",
  "status": "READY_FOR_EDIT",
  "createdAt": "2025-01-11T10:00:00",
  "createdBy": "admin",
  "updatedAt": "2025-01-11T10:05:00",
  "updatedBy": "admin",
  "version": 7,
  "placementsCount": 150,
  "hasWorkspaceSnapshot": true
}
```

---

### 6. Получить все сессии пользователя

**Endpoint:** `GET /api/schedule/sessions?createdBy={username}`

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/sessions?createdBy=admin" \
  -H "Accept: application/json"
```

**Ответ:**
```json
[
  {
    "id": "...",
    "name": "Расписание 2025 весна",
    "status": "READY_FOR_EDIT",
    "version": 7,
    "createdAt": "2025-01-11T10:00:00"
  },
  {
    "id": "...",
    "name": "Расписание 2025 осень",
    "status": "FINAL",
    "version": 15,
    "createdAt": "2024-09-01T10:00:00"
  }
]
```

---

## 🔒 ПРИМЕР: Optimistic Locking

### Сценарий: Конкурентное редактирование

```
ВРЕМЯ | Диспетчер А                         Диспетчер Б
-------|-------------------------------------|-------------------------------------
09:00  | Открывает расписание                |
       | GET /sessions/{id}                  |
       | version = 5 ✅                      |
       |                                     |
09:05  |                                     | Открывает расписание
       |                                     | GET /sessions/{id}
       |                                     | version = 5 ✅
       |                                     |
09:10  | Переносит занятие на понедельник     |
       | POST /sessions/{id}/move-lesson     |
       | { version: 5 }                      |
       |                                     |
       | ✅ SUCCESS!                         |
       | version: 5 → 6                      |
       |                                     |
09:15  |                                     | Пытается перенести
       |                                     | POST /sessions/{id}/move-lesson
       |                                     | { version: 5 }
       |                                     |
       |                                     | ❌ CONFLICT!
       |                                     | "Расписание было изменено.
       |                                     |  Текущая версия: 6"
       |                                     |
09:16  |                                     | Обновляет страницу
       |                                     | GET /sessions/{id}
       |                                     | version = 6 ✅
       |                                     |
09:17  |                                     | Переносит занятие на вторник
       |                                     | POST /sessions/{id}/move-lesson
       |                                     | { version: 6 }
       |                                     |
       |                                     | ✅ SUCCESS!
       |                                     | version: 6 → 7
```

**HTTP Response (Conflict):**
```json
HTTP/1.1 409 Conflict
{
  "success": false,
  "error": "OPTIMISTIC_LOCK_CONFLICT",
  "message": "Расписание было изменено другим пользователем. Обновите страницу.",
  "currentVersion": 6,
  "expectedVersion": 5
}
```

---

## 🧪 ТЕСТИРОВАНИЕ API

### Quick Test (cURL)

```bash
# 1. Проверить Query Side
curl "http://localhost:8080/api/schedule/query/student/123?start=2025-01-11&end=2025-01-17"

# 2. Проверить свободность аудитории
curl "http://localhost:8080/api/schedule/query/check-auditorium?auditoriumId=789&date=2025-01-12&slot=FIRST"

# 3. Создать сессию
curl -X POST "http://localhost:8080/api/schedule/sessions" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Session", "courseIds": [701]}'

# 4. Получить сессию
curl "http://localhost:8080/api/schedule/sessions/{sessionId}"
```

### Frontend Integration (TypeScript)

```typescript
// Query Side
const getStudentSchedule = async (streamId: number, start: string, end: string) => {
  const response = await apiClient.get(
    `/api/schedule/query/student/${streamId}?start=${start}&end=${end}`
  );
  return response.data; // ScheduleView[]
};

// Command Side
const moveLesson = async (sessionId: string, placementId: string, 
                         newDate: string, newSlot: string, version: number) => {
  try {
    const response = await apiClient.post(
      `/api/schedule/sessions/${sessionId}/move-lesson`,
      { placementId, newDate, newSlot, auditoriumIds: [], version }
    );
    return response.data; // { success: true, newVersion: ... }
  } catch (error) {
    if (error.response?.status === 409) {
      // ❌ Optimistic lock conflict
      alert("Расписание было изменено. Обновляем страницу...");
      await reloadSession(); // Перезагрузить сессию
      throw error;
    }
    throw error;
  }
};
```

---

## 📊 Performance Benchmarks

### Query Side

| Endpoint | Время | Requests/sec |
|----------|-------|--------------|
| `/query/student/{id}` | ~10ms | ~1000 |
| `/query/educator/{id}` | ~5ms | ~2000 |
| `/query/check-auditorium` | ~5ms | ~2000 |
| `/query/reports/educator-load` | ~50ms | ~200 |

### Command Side

| Endpoint | Время | Requests/sec |
|----------|-------|--------------|
| `POST /sessions` | ~50ms | ~200 |
| `POST /generate-with-persistence` | ~500ms | ~20 |
| `POST /sessions/{id}/move-lesson` | ~100ms | ~100 |

---

## 🔗 Related Documentation

- [CQRS Architecture](CQRS_ARCHITECTURE.md)
- [Development Context](DEVELOPMENT_CONTEXT.md)
- [Architecture Refactoring Plan](ARCHITECTURE_REFACTORING_PLAN.md)

---

*Автор: CQRS Implementation Team*
*Обновлено: 2025-01-11*
