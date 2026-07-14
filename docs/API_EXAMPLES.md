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

**Endpoint:** `GET /query/reports/auditorium-utilization?start=X&end=Y`

**Пример:**
```bash
curl -X GET "http://localhost:8080/api/schedule/query/reports/auditorium-utilization?start=2026-01-11&end=2026-01-17" \
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

### 8. Прочие query-эндпоинты

| Endpoint | Назначение |
|---|---|
| `GET /query/all?start=X&end=Y` | Всё расписание за период → `ScheduleResultDto` (плоский список + `grid` для отображения). Основная загрузка сетки на фронт |
| `GET /query/readiness?periodId=X` | Готовность периода → `PeriodReadinessDto {total, placed, unplaced}`. «Всего» берётся из `GenerationScopeResolver` (тот же набор, что идёт в генерацию, без запуска солвера) |
| `GET /query/reports/educator-quality?periodId=X` | Качество расписания преподавателей → `PeriodScheduleQualityDto` (компактность + равномерность, считается из `schedule_view`) |
| `GET /query/discipline/{abbr}` | Все занятия дисциплины по аббревиатуре |
| `GET /query/kind/{kind}` | Все занятия вида (`LECTURE`, `PRACTICAL_WORK`, …) |

---

## 💾 COMMAND SIDE: Запись и редактирование

> **Базовый путь:** `POST/GET/DELETE/PATCH /api/schedule/command/...` (см.
> `ru.controllers.command.ScheduleCommandController`). Поиск вариантов переноса живёт отдельно —
> на `/api/schedule/find-move-options` (`ScheduleMoveController`), а не под `/command`.
>
> Генерация period-first: тело несёт `studyPeriodId` (явный вход по учебному периоду), даты больше
> не хардкодятся. Ответ на все команды — **`ScheduleSessionDto`** (сессия с новой `version`);
> конфликты возвращаются телом `ConflictResponse` со статусом `409`.

### 1. Создать пустую сессию

**Endpoint:** `POST /api/schedule/command/sessions`

**Тело запроса** (`CreateScheduleSessionRequest`)**:**
```json
{ "name": "Расписание 2026 весна" }
```

**Ответ** (`ScheduleSessionDto`)**:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Расписание 2026 весна",
  "status": "INITIALIZED",
  "createdAt": "2026-01-11T10:00:00",
  "createdBy": "admin",
  "version": 0
}
```

---

### 2. Генерация расписания (period-first)

**Endpoint:** `POST /api/schedule/command/sessions/generate`

**Тело запроса** (`CreateScheduleSessionRequest`)**:**
```json
{
  "name": "Расписание 2026 весна",
  "studyPeriodId": 3,
  "courseIds": [701, 702, 703]
}
```

**Пример:**
```bash
curl -X POST "http://localhost:8080/api/schedule/command/sessions/generate" \
  -H "Content-Type: application/json" \
  -d '{ "name": "Расписание 2026 весна", "studyPeriodId": 3, "courseIds": [701, 702, 703] }'
```

Ответ — `ScheduleSessionDto` со `status: "READY_FOR_EDIT"` и увеличенной `version`. Query Side
(`schedule_view`) синхронизируется асинхронно (события `ScheduleGeneratedEvent` → `ScheduleSynchronizer`).

**Родственные команды генерации** (все возвращают `ScheduleSessionDto`):

| Endpoint | Назначение |
|---|---|
| `POST /command/sessions/{id}/regenerate` | Перегенерация, **сохраняя закреплённые** (`locked`) занятия; распределитель раскладывает остальное вокруг них |
| `POST /command/sessions/{id}/generate-course` | **Аддитивная** генерация одного курса: тело `GenerateCourseRequest {studyPeriodId, courseId, kinds?}`; существующее не удаляется, кладутся только неразмещённые занятия курса |
| `POST /command/sessions/{id}/clear` | Очистка размещений **кроме `locked`**; тело `ClearPlacementsRequest {courseId?, kinds?}` (оба опц.; пусто → вся сессия). Возвращает **число удалённых** |
| `POST /command/sessions/editable` | Получить/переоткрыть сессию «живого» расписания без перегенерации (204, если расписания ещё нет) |
| `POST /command/sessions/for-period/{studyPeriodId}` | Получить/создать рабочую сессию периода (Путь 2 — ручная раскладка будущего семестра) |

---

### 3. Перенести занятие (с optimistic lock)

**Endpoint:** `POST /api/schedule/command/sessions/{sessionId}/move-lesson`

**Тело запроса** (`MoveLessonRequest`)**:**
```json
{
  "placementId": "550e8400-e29b-41d4-a716-446655440000",
  "newDate": "2026-01-15",
  "newSlot": "SECOND",
  "version": 5
}
```

> ⚠️ Аудитории подбираются **на бэке** (`LessonMoveService` пересоздаёт workspace и валидирует слот
> через `findPlacementOption`). Поле `newAuditoriumIds` в контракте осталось мёртвым — не используется.

**Ответ (SUCCESS):** `MoveLessonResponse` — сессия с новой `version` + флаги пересортировки:
```json
{
  "session": { "id": "…", "version": 6, "status": "READY_FOR_EDIT" },
  "problems": [ { "placementId": "…", "reason": "CHAIN_BROKEN" } ]
}
```
> **Пересортировка трека входит в команду** (одна транзакция): перенесённое занятие «пузырьком»
> встаёт на плановое место, соседи сдвигаются. Отдельного `POST /placements/{id}/reorder`
> **больше нет** — раньше его звал фронт вторым запросом, и между двумя транзакциями оставалось
> окно, в котором расписание побывало «перенесено, но не пересортировано» (а сверить версию в
> `reorder` было нельзя: её только что сдвинул сам перенос).

**Ответ (CONFLICT, HTTP 409, тело `ConflictResponse`):**
```json
{
  "error": "RESOURCE_CONFLICT",
  "message": "Невозможно перенести занятие: ... . Обновите данные и выберите другой слот.",
  "currentVersion": 6
}
```
`error` = `CONFLICT` при устаревшей `version` (optimistic lock), либо `RESOURCE_CONFLICT` при
занятости ресурса (`LessonMoveConflictException`).

**Перенос цепочки целиком:** `POST /command/sessions/{sessionId}/move-chain` — тело
`MoveChainRequest {placementIds[], newStartDate, newStartSlot, version}` (аудитории тоже с бэка).

---

### 4. Найти варианты для переноса

**Endpoint:** `POST /api/schedule/find-move-options` (контроллер `ScheduleMoveController`, **без** `/command`)

**Тело запроса** (`MoveSuggestionRequest`)**:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "placementId": "550e8400-e29b-41d4-a716-446655440000",
  "rootEntityId": 456,
  "rootEntityType": "EDUCATOR"
}
```

**Ответ** (`List<MoveOptionDto>`)**:**
```json
[
  { "date": "2026-01-13", "timeSlot": "FIRST" },
  { "date": "2026-01-14", "timeSlot": "SECOND" }
]
```

> Варианты для цепочки — `POST /api/schedule/find-chain-move-options` (тело
> `ChainMoveSuggestionRequest {placementIds[]}`).

---

### 5. Ручная раскладка (Фаза B) и пины

> 🔒 **Все мутирующие команды несут `version`** (optimistic lock, с 2026-07-14). Устаревшая версия →
> **409** `ConflictResponse {error: "CONFLICT", currentVersion}`. Клиент обязан подхватить
> `currentVersion` из тела, перечитать данные и повторить — иначе вкладка залипнет на старой версии
> (push-уведомлений пока нет, о чужих правках она узнаёт только через отказ).
> Сверка — в одном месте на бэке: `ScheduleSessionGate` (см. CQRS_ARCHITECTURE.md).

| Endpoint | Назначение |
|---|---|
| `GET /command/sessions/{id}/unplaced?courseIds=1,2,3` | Неразмещённые занятия курсов (палитра) → `List<UnplacedLessonDto>` |
| `POST /command/sessions/{id}/placement-options` | Куда можно поставить занятие (подсветка ячеек); тело `PlacementOptionsRequest {assignmentId, rootEntityType, rootEntityId, studyPeriodId}` → `List<MoveOptionDto>` |
| `POST /command/sessions/{id}/placements` | Ручная установка (создаёт `MANUAL`/`locked` размещение); тело `ManualPlacementRequest {assignmentId, date, slot, studyPeriodId, version}` → `ScheduleSessionDto` или `409` |
| `DELETE /command/placements/{placementId}?version=N` | Снять размещение (вернуть в палитру). Версия — **query-параметром**: у DELETE тело не принято |
| `PATCH /command/placements/{placementId}/lock` | Закрепить/открепить (пин); тело `LockPlacementRequest {locked, placementIds?, version}` — закрепляет цепочку (или её подмножество) |

> ~~`POST /command/placements/{placementId}/reorder`~~ — **удалён.** Пересортировка трека в порядок
> плана теперь выполняется внутри команды переноса (см. выше), в той же транзакции.

---

### 6. Доска раскладки, счётчики, порядок изучения, ремонт проекции

| Endpoint | Назначение |
|---|---|
| `GET /command/sessions/{id}/placement-board?courseIds=1,2,3&axis=GROUP\|EDUCATOR` | «Доска раскладки» → `PlacementBoardDto` (дерево сущность→дисциплина→занятие со счётчиками total/placed/unplaced на каждом уровне). Ось — стратегия `BoardAxis`. ⚠️ Тяжёлая: ~2.3 МБ, кандидат на расщепление (см. FOLLOWUPS) |
| `GET /command/sessions/{id}/placement-counts?courseIds=1,2,3` | Лёгкие счётчики «распределено N/M» по каждому курсу → `List<CoursePlacementCountDto>` (вкладка «Генерация») |
| `GET /command/sessions/{id}/order-violations` | **Порядок изучения** → `List<OrderViolationDto>` — занятия, стоящие раньше предшествующей им по плану лекции (`kind=BEFORE_LECTURE`) либо слишком далеко после неё (`kind=FAR_FROM_LECTURE`, `gapDays`; порог — property `schedule.order.max-lecture-gap-days`, дефолт 14). **Подсказка, а не запрет:** ячейки не фильтруются, перенос не блокируется. Одним запросом на всё расписание |
| `POST /command/sessions/{id}/reproject` | Ремонтная пересборка read-модели (`schedule_view`) сессии → число перепроецированных строк |

**Пример ответа `order-violations`:**
```json
[
  {
    "placementId": "550e8400-e29b-41d4-a716-446655440000",
    "lecturePlacementId": "6f1c2d3e-...",
    "groupId": 208,
    "kind": "FAR_FROM_LECTURE",
    "gapDays": 19
  }
]
```
> Контракт несёт только семантику (`kind`) — как её показывать, решает UI.

---

### 7. Получить сессию / размещения / удалить

| Endpoint | Назначение |
|---|---|
| `GET /command/sessions/{sessionId}` | Сессия по ID → `ScheduleSessionDto` |
| `GET /command/sessions/{sessionId}/placements` | Все размещения сессии → `List<LessonPlacementDto>` |
| `DELETE /command/sessions/{sessionId}` | Удалить сессию (каскадно placements) |

---

## 🎯 ОХВАТ ГЕНЕРАЦИИ И ОЧИСТКИ (2026-07-13)

Обе операции сужаются одинаково: курс → виды занятий → преподаватели. Охват «по преподавателю»
означает в них одно и то же (занятие берётся, если его ведёт кто-то из указанных; совместное
занятие двух преподавателей попадает в охват каждого).

```bash
# Разложить только практики преподавателя 317 в рамках дисциплины 711
curl -X POST "http://localhost:8080/api/schedule/command/sessions/{sessionId}/generate-course" \
  -H "Content-Type: application/json" \
  -d '{"studyPeriodId": 502, "courseId": 711, "kinds": ["PRACTICAL_WORK"], "educatorIds": [317]}'

# Снять их же (кроме закреплённых)
curl -X POST "http://localhost:8080/api/schedule/command/sessions/{sessionId}/clear" \
  -H "Content-Type: application/json" \
  -d '{"courseId": 711, "kinds": ["PRACTICAL_WORK"], "educatorIds": [317]}'
```
> ⚠️ Чем уже охват, тем меньше «кругозор» распределителя: равномерность и интервалы между лекциями
> он считает только по взятым занятиям, остальные для него — неподвижные обстоятельства.

**`GET /command/sessions/{id}/placement-counts?courseIds=711`** отдаёт счётчик курса **и разбивку
по преподавателям** (для раскрытия дисциплины в UI):
```json
[{ "courseId": 711, "total": 84, "placed": 84,
   "educators": [{ "educatorId": 317, "educatorName": "Барская В.М.", "total": 24, "placed": 24 }] }]
```
> Сумма по преподавателям может превышать `total` курса — совместное занятие считается у каждого.

---

## 🩺 ЗДОРОВЬЕ ПРОЕКЦИИ И ЦЕНА УДАЛЕНИЯ (2026-07-13)

Инварианты read-модели и то, как они видны наружу — см. [CQRS_ARCHITECTURE.md](CQRS_ARCHITECTURE.md).

### Сверка Command Side ↔ Query Side

**`GET /api/schedule/query/projection-health?periodId=502`**

```json
{ "sessionId": "370b0350-…", "placements": 1693, "projected": 1693, "missing": 0 }
```

`missing > 0` — часть занятий не доехала до сетки (асинхронная проекция отстала или сорвалась;
сами занятия целы). Дашборд показывает баннер и кнопку «Восстановить отображение» →
`POST /api/schedule/command/sessions/{id}/reproject` (расписание не двигается: даты, пары и замки
берутся из тех же размещений). Обратная поломка — строка без размещения — невозможна: её запрещает
FK (миграция 017).

### Цена удаления — до подтверждения

Каскады БД уносят размещения **молча** и про `locked` ничего не знают, поэтому цену называет бэк.

| Endpoint | Что отдаёт |
|---|---|
| `GET /api/assignments/{id}/delete-impact` | `RemoveAssignmentsImpactDto` — назначений / размещено / из них закреплено |
| `GET /api/auditoriums/{id}/delete-impact` | `AuditoriumDeletionImpactDto` — см. ниже |
| `GET /api/curriculum-slots/{id}/delete-impact` | `SlotDeletionImpactDto` — назначений / размещено / закреплено |

**`GET /api/auditoriums/12/delete-impact`**
```json
{
  "auditoriumId": 12,
  "name": "204-3",
  "deletable": false,
  "placedLessons": 37,
  "lockedLessons": 4,
  "slotsRequiringIt": 2,
  "groupsUsingAsBase": 1
}
```
- `placedLessons` — столько занятий останется **без комнаты** (связь уходит каскадом; подобрать
  новую можно только генерацией или переносом), `lockedLessons` — из них закреплено вручную.
- `deletable` — **решение бэка**, а не вывод фронта: аудиторию, на которую ссылается учебный план
  (`slotsRequiringIt > 0`), БД удалить не даст. `DELETE /api/auditoriums/{id}` в этом случае
  отвечает **409** с текстом причины (раньше был сырой 500).

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
       | POST /command/sessions/{id}/move-lesson |
       | { version: 5 }                      |
       |                                     |
       | ✅ SUCCESS!                         |
       | version: 5 → 6                      |
       |                                     |
09:15  |                                     | Пытается перенести
       |                                     | POST /command/sessions/{id}/move-lesson
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

**HTTP Response (Conflict):** тело `ConflictResponse`
```json
HTTP/1.1 409 Conflict
{
  "error": "CONFLICT",
  "message": "Расписание было изменено другим пользователем. Обновите страницу.",
  "currentVersion": 6
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

# 3. Сгенерировать расписание (period-first)
curl -X POST "http://localhost:8080/api/schedule/command/sessions/generate" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Session", "studyPeriodId": 3, "courseIds": [701]}'

# 4. Получить сессию
curl "http://localhost:8080/api/schedule/command/sessions/{sessionId}"
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
      `/api/schedule/command/sessions/${sessionId}/move-lesson`,
      { placementId, newDate, newSlot, version } // аудиторию подберёт бэк
    );
    return response.data; // ScheduleSessionDto (новая version)
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
| `POST /command/sessions` | ~50ms | ~200 |
| `POST /command/sessions/generate` | ~500ms | ~20 |
| `POST /command/sessions/{id}/move-lesson` | ~100ms | ~100 |

---

## 🔗 Related Documentation

- [CQRS Architecture](CQRS_ARCHITECTURE.md)
- [Development Context](DEVELOPMENT_CONTEXT.md)
- [Схема БД](DATABASE.md)
- [Follow-ups / техдолг](FOLLOWUPS.md)

---

*Изначально: CQRS Implementation Team, 2025-01-11*
*Приведено в соответствие с текущими контроллерами: 2026-07-07*
