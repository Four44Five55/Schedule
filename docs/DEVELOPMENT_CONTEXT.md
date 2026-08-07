# Контекст разработки: Распределение занятий в расписании

**Дата обновления:** 2026-08-07 (сверка «Следующих шагов» с кодом; оргструктура — 2026-07-21; архитектурные разделы — от 2026-06-27)
**Основной класс:** `DistributionDiscipline` (оркестратор)
**Текущая ветка:** `feat-final-schedule-in-bd`
**Статус:** Оргструктура (подразделения) сделана; следующий этап — импорт расписания из сторонней программы

> Актуальный список задач и техдолга — в **[FOLLOWUPS.md](FOLLOWUPS.md)** (живой документ).
> Здесь — устройство системы и алгоритма.
>
> Смежное: полная карта архитектурных долгов — в
> [ARCHITECTURE_AUDIT_2026-07-18.md](ARCHITECTURE_AUDIT_2026-07-18.md) (там же в §0 — фактический
> пайплайн генерации, прослеженный по коду); замысел справедливой генерации — в
> [NORTH_STAR_FAIR_GENERATION.md](NORTH_STAR_FAIR_GENERATION.md); формат чужой выгрузки и правила
> импорта — в [IMPORT_FORMAT.md](IMPORT_FORMAT.md).

---

## Архитектура проекта

### Технологический стек

**Backend:**
- Spring Boot 3.2.0
- Java 21
- PostgreSQL (production), H2 (development)
- Liquibase (миграции)
- MapStruct (маппинг)
- Apache POI (экспорт в Excel)
- Lombok

**Frontend:**
- React 19.2.6
- TypeScript 5.9.3
- Vite 7.3.2 (сборщик)
- TailwindCSS 4.1.17
- Axios (HTTP-клиент)
- Lucide React (иконки)

### Структура пакетов
```
ru/
├── entity/          - Сущности (Lesson, Educator, Group, Auditorium)
├── abstracts/       - Базовые классы
├── services/
│   ├── distribution/ - Алгоритмы распределения (РЕФАКТОРИНГ 2026-02-26)
│   ├── solver/ - Алгоритмы решения расписания
│   │   ├── ScheduleWorkspace (главное пространство планирования)
│   │   ├── model/
│   │   │   ├── ScheduleGrid (сетка расписания)
│   │   │   └── SchedulableResource (ресурс для планирования)
│   │   └── availability/ (управление доступностью)
│   ├── projection/  - Целостность read-модели (НОВОЕ 2026-07-13)
│   │   ├── ProjectionMaintenance    (единая дверь: «мои данные изменились»)
│   │   ├── ProjectionSource         (Strategy-enum: сущность → затронутые размещения)
│   │   └── ProjectionHealthService  (сверка Command ↔ Query для дашборда)
│   ├── session/     - ScheduleSessionGate (единая дверь «взять сессию на запись»: сверка
│   │                  expectedVersion + OPTIMISTIC_FORCE_INCREMENT, 2026-07-14)
│   ├── stream/      - Живые обновления по SSE (2026-07-14)
│   │   ├── ScheduleEventStream       (реестр SseEmitter'ов, в памяти процесса)
│   │   ├── ScheduleChangeNotifier    (звонок ПОСЛЕ записи проекции)
│   │   └── ScheduleChangedDto        ({sessionId, version} — сигнал, не данные)
│   ├── orgunit/     - Оргструктура (НОВОЕ 2026-07-21)
│   │   ├── OrgUnitService           (CRUD подразделений + цена удаления)
│   │   ├── OrgUnitHierarchyRule     (чистая функция: родитель, кольцо, ранг вида)
│   │   ├── OrgUnitSubtree           (чистая функция: разворот в поддерево, 2026-07-22)
│   │   ├── OrgUnitNodes             (общая сборка входа для обеих функций)
│   │   └── OrgUnitScopeResolver     (охват: поддерево → id преподавателей/групп)
│   └── MoveLessonSuggestionService - Поиск вариантов переноса (НОВОЕ 2026-06-10)
│       ├── DistributionDiscipline.java      (оркестратор, ~113 строк)
│       ├── core/                            (основные компоненты)
│       │   ├── DistributionContext          (контекст распределения)
│       │   └── EducatorPrioritizer          (сортировка преподавателей)
│       ├── finder/                          (поиск дат)
│       │   ├── DateFinder                   (интерфейс стратегии)
│       │   ├── SlidingWindowDateFinder      (скользящее окно)
│       │   └── DateFinderFactory            (фабрика стратегий)
│       ├── lecture/                         (фаза 1: лекции)
│       │   └── LectureDistributionHandler   (размещение лекций)
│       ├── practice/                        (фаза 2: практики)
│       │   ├── PracticeDistributionHandler  (размещение практик)
│       │   └── PracticeSwapService          (свап практик)
│       ├── placement/                       (базовое размещение)
│       │   ├── LessonPlacementService       (базовое размещение)
│       │   └── ChainPlacementHandler        (размещение цепочек)
│       ├── validator/                       (проверки доступности)
│       │   └── PlacementValidator           (валидация размещения)
│       ├── metrics/                         (метрики распределения)
│       │   └── DistributionMetrics          (вычисление метрик)
│       └── utils/                           (утилиты)
│           └── DistributionUtils            (вспомогательные методы)
├── dto/             - Data Transfer Objects
│   ├── moveLesson/  - DTO для переноса занятий (НОВОЕ 2026-06-10)
│   │   ├── MoveOptionDto
│   │   └── MoveSuggestionRequest
├── controllers/     - REST API контроллеры
│   └── ScheduleMoveController - API для переноса занятий (НОВОЕ 2026-06-10)
├── repository/      - Spring Data JPA
├── mapper/          - MapStruct мапперы
└── enums/           - KindOfStudy, DayOfWeek, TimeSlotPair

**Frontend структура:**
```
frontend/src/
├── components/      - Переиспользуемые компоненты UI
├── features/       - Функциональные модули
│   ├── constraints/ - Управление ограничениями
│   ├── curriculum/  - Учебные планы
│   ├── dashboard/    - Главная панель
│   ├── orgUnit/      - Оргструктура: дерево подразделений (НОВОЕ 2026-07-21)
│   ├── resources/    - Ресурсы (аудитории, преподаватели)
│   └── schedule/     - Расписание
├── hooks/          - React hooks
├── services/       - API сервисы
│   ├── apiClient.ts
│   └── apiServices.ts
├── types/          - TypeScript типы
└── utils/          - Утилиты
```
```

---

## Рефакторинг 2026-02-26

### Что было сделано:

**До рефакторинга:**
- `DistributionDiscipline`: 1740 строк, ~40 методов (God Class)
- Вся логика в одном классе
- Трудно поддерживать и тестировать

**После рефакторинга:**
- `DistributionDiscipline`: 113 строк (оркестратор)
- 14 специализированных классов (SRP)
- Чёткое разделение ответственности

### Новая архитектура

```
┌─────────────────────────────────────────────────────────────────────┐
│                      DistributionDiscipline                        │
│                           (оркестратор)                            │
│  distributeLessons() → lectureHandler.distributeLectures()         │
│                     → practiceHandler.distributePractices()        │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        DistributionContext                         │
│                   (общее состояние распределения)                  │
│  - workspace, lessons, educators, distributedLessons              │
└─────────────────────────────────────────────────────────────────────┘
```

### Созданные компоненты:

| Компонент | Назначение |
|-----------|------------|
| `DistributionContext` | Контекст с общим состоянием |
| `EducatorPrioritizer` | Сортировка преподавателей по приоритету |
| `DateFinder` (interface) | Стратегия поиска дат |
| `SlidingWindowDateFinder` | Поиск через скользящее окно |
| `DateFinderFactory` | Фабрика для выбора стратегии |
| `PlacementValidator` | Проверки доступности размещения |
| `LessonPlacementService` | Базовое размещение занятий |
| `ChainPlacementHandler` | Размещение цепочек занятий |
| `LectureDistributionHandler` | Фаза 1: распределение лекций |
| `PracticeDistributionHandler` | Фаза 2: распределение практик |
| `PracticeSwapService` | Свап практик при конфликтах |
| `DistributionMetrics` | Вычисление метрик |
| `DistributionUtils` | Утилитные методы |

---

## REST API Структура

### Основные контроллеры:
- `command.ScheduleCommandController` - генерация/перенос/ручная раскладка/пины (`/api/schedule/command/...`)
- `read.ScheduleQueryController` - чтение расписания и отчёты (`/api/schedule/query/...`)
- `read.ScheduleStreamController` - SSE-поток живых обновлений (`/api/schedule/query/stream/{sessionId}`, НОВОЕ 2026-07-14)
- `ScheduleMoveController` - поиск вариантов переноса (`/api/schedule/find-move-options`, НОВОЕ 2026-06-10)
- `AssignmentController` - управление назначениями
- `EducatorController` - управление преподавателями
- `GroupController` - управление группами
- `AuditoriumController` - управление аудиториями
- `DisciplineController` - управление дисциплинами
- `OrgUnitController` - подразделения: факультеты/кафедры/отделы (`/api/org-units`, НОВОЕ 2026-07-21;
  `/{id}/scope` — охват с учётом вложенности, 2026-07-22)
- `AuditoriumConstraintController` - ограничения аудиторий
- `EducatorConstraintController` - ограничения преподавателей
- `GroupConstraintController` - ограничения групп

### Новые endpoints (2026-06-10):

**ScheduleMoveController**
- `POST /api/schedule/find-move-options` - поиск вариантов переноса занятия

---

## Схема БД

Полный справочник схемы — в **[DATABASE.md](DATABASE.md)**: все таблицы, поля, типы, связи и индексы.

---

## Алгоритм распределения (двухфазный)

### Фаза 1: Распределение лекций

```
LectureDistributionHandler.distributeLectures()
├── EducatorPrioritizer.sortByPriority() - сортировка преподавателей
│   ├── Приоритет 1: количество групп в лекциях (по убыванию)
│   └── Приоритет 2: общее количество занятий (по убыванию)
│
└── Для каждого преподавателя:
    └── LectureDistributionHandler.distributeLecturesForEducator()
        ├── Подготовка списка занятий преподавателя
        ├── Расчёт необходимого количества дней
        ├── Выбор целевых дат (равномерное распределение)
        └── Главный цикл распределения:
            ├── ChainPlacementHandler.getChainForLesson() - получение цепочки
            └── ChainPlacementHandler.tryPlaceChainInDay() - размещение
```

### Фаза 2: Распределение практик

```
PracticeDistributionHandler.distributePractices()
└── Для каждого преподавателя:
    └── PracticeDistributionHandler.distributePracticesForEducator()
        ├── Фильтрация только практик
        ├── Сортировка через lessonSortingService.getSortedLessons()
        ├── Получение дат с лекциями (приоритетные)
        └── Для каждой практики:
            ├── ChainPlacementHandler.getChainForLesson()
            ├── LessonPlacementService.findMinDate() - мин. дата (после лекции)
            ├── DateFinder.findDate() - поиск доступной даты
            ├── placePracticeInDate() - размещение
            └── При неудаче: PracticeSwapService.trySwap()
```

---

## Ключевые компоненты

### ScheduleWorkspace
Главное пространство планирования (обновлено 2026-06-10):
- `grid` - ScheduleGrid (сетка расписания)
- `resourceManager` - ResourceAvailabilityManager (управление доступностью)
- `findPlacementOption()` - поиск варианта размещения
- `executePlacement()` - атомарное размещение занятия
- `removePlacement()` - атомарное удаление занятия
- `forcePlacement()` - принудительное размещение
- `findAvailableAuditoriumsFor()` - поиск доступных аудиторий
- `clear()` - очистка расписания и ресурсов

### DistributionContext
Содержит общее состояние для всех компонентов:
- `workspace` - ScheduleWorkspace
- `lessons` - список всех занятий
- `educators` - список преподавателей
- `distributedLessons` - список распределённых занятий
- `distributedLessonsSet` - для быстрой проверки O(1)

### EducatorPrioritizer
Сортирует преподавателей по приоритету:
1. Количество групп в лекциях (по убыванию)
2. Общее количество занятий (по убыванию)

### DateFinder (стратегия)
Интерфейс для поиска дат размещения:
- `SlidingWindowDateFinder` - скользящее окно с приоритетами
- Планируется: `CompactDateFinder` - компактное распределение

### PlacementValidator
Проверяет возможность размещения:
- `canPlacePractice()` - проверка даты для практики
- `canPlaceChain()` - проверка даты для цепочки
- `isDayViable()` - быстрая проверка дня
- `isCellFree()` - проверка ячейки

### ChainPlacementHandler
Работа с цепочками занятий:
- `getChainForLesson()` - получение цепочки
- `tryPlaceChainInDay()` - размещение цепочки в день

### LectureDistributionHandler
Фаза 1 - распределение лекций:
- `distributeLectures()` - для всех преподавателей
- `distributeLecturesForEducator()` - для конкретного

### PracticeDistributionHandler
Фаза 2 - распределение практик:
- `distributePractices()` - для всех преподавателей
- `distributePracticesForEducator()` - для конкретного
- `placePracticeInDate()` - размещение практики
- `rollbackPractices()` - откат практик

### PracticeSwapService
Свап практик при конфликтах:
- `trySwap()` - попытка обмена
- `canSwap()` - проверка возможности обмена
- `performSwap()` - выполнение обмена

### DistributionMetrics
Вычисление метрик:
- `countPracticesInDate()` - количество практик в дате
- `calculateCompactnessBonus()` - бонус компактности
- `countLessonsInDate()` - количество занятий в дате
- `countNearbyDaysWithLessons()` - дней с занятиями поблизости

---

## Логи для отладки

### Порядок распределения преподавателей:
```
=== Порядок распределения преподавателей ===
1. Иванов И.И. - групп в лекциях: 5, всего занятий: 15
2. Петров П.П. - групп в лекциях: 3, всего занятий: 12
```

### Размещение практики:
```
✓ Практика размещена: ЛР/1 [43, 44] №-8 дата 2026-03-03,SECOND размер цепочки: 1
```

### Размещение цепочки:
```
✓ Цепочка размещена: ЛР/2 tema-1 [43, 44] №-5 дата 2026-03-03,FIRST размер цепочки: 2
```

---

## Система переноса занятий (НОВОЕ 2026-06-10)

### Архитектура

```
ScheduleMoveController (REST API)
└── POST /api/schedule/find-move-options
    └── MoveLessonSuggestionService
        ├── findLesson() - поиск занятия в Workspace
        ├── removePlacement() - виртуальное изъятие занятия
        ├── Каскадная фильтрация:
        │   ├── ШАГ 1: Фильтр по корневой сущности
        │   ├── ШАГ 2: Фильтр по участникам занятия
        │   └── ШАГ 3: Фильтр по аудиториям
        └── forcePlacement() - восстановление занятия
```

### Каскадная фильтрация

**ШАГ 1: Фильтр по корневой сущности**
- Самый быстрый фильтр O(1)
- Если смотрим расписание Группы А → убираем ячейки где Группа А занята
- Использует `SchedulableResource.isFree(cell)`

**ШАГ 2: Фильтр по участникам**
- Проверяет доступность преподавателей и других групп
- Для каждого участника: `candidates.removeIf(cell -> !participant.isFree(cell))`

**ШАГ 3: Фильтр по аудиториям**
- Самый тяжелый фильтр
- Для оставшихся ячеек проверяет наличие подходящей аудитории
- Использует `workspace.findAvailableAuditoriumsFor(lesson, cell)`

### API Endpoints

**POST /api/schedule/find-move-options**
```json
// Request
{
  "lessonId": 123,
  "rootEntityId": 456,
  "rootEntityType": "EDUCATOR" // or "GROUP", "AUDITORIUM"
}

// Response
[
  { "date": "2026-06-15", "timeSlot": "FIRST" },
  { "date": "2026-06-16", "timeSlot": "SECOND" }
]
```

### Ключевые компоненты

**MoveLessonSuggestionService**
- `findMoveSuggestions()` - основной метод поиска
- `getRootResource()` - получает корневой ресурс по типу
- `getParticipantsExceptRoot()` - получает всех участников кроме корневого
- `findLesson()` - поиск занятия в сетке Workspace

**PlacementOption**
- `isPossible()` - проверка возможности размещения
- `assignedAuditoriums()` - список назначенных аудиторий
- `score()` - оценка качества размещения

---

## Важные entity-классы

### Lesson
```java
- CurriculumSlot curriculumSlot (position, kindOfStudy)
- Set<Educator> educators
- StudyStream studyStream
  └── Set<Group> groups
```

### Educator
```java
- Integer id
- String name
- boolean compactSchedule  // Флаг компактности расписания
- Set<DayOfWeek> preferredDays
- Set<TimeSlotPair> preferredTimeSlots
```

---

## Отображение и ввод ограничений + единый каркас сетки (2026-06-27)

### Единый каркас сетки (DRY/SOLID)
Презентационный скелет «день × пара × неделя» вынесен в общий компонент
`frontend/src/components/grid/AcademicGridShell.tsx`:
- **SRP:** отвечает только за каркас (шапки месяцев/недель, sticky-колонки дни/пары, зум,
  fullscreen). Содержимое ячейки задаётся снаружи через render-prop `renderCell(ctx)` (Strategy/OCP).
- Экспортирует единые `DAYS`, `SLOTS`, `zoomFontClasses`, типы `GridCellContext`/`ZoomLevel`.
- На него переведены **обе** сетки: расписание (`AcademicGridSchedule`) и ограничения
  (`ConstraintsGridSchedule`) — дублирование скелета убрано.

### Ограничения: данные
- `KindOfConstraints` (enum) отдаёт `abbreviation`/`fullName`; теперь они **выходят в API** —
  в `EducatorConstraintDto`/`GroupConstraintDto`/`AuditoriumConstraintDto` (заполняются в `toDto`
  контроллеров). На фронте — те же поля в `types/api.ts`.
- Ограничения — **глобальные master-данные** (не привязаны к сессии). При генерации и при
  пересоздании workspace (перенос) их собирает `ConstraintServiceImpl.loadAllConstraints()`,
  разворачивая диапазоны дат в ячейки → занятые ограничением ячейки недоступны для сущности.

### Ограничения: фронтенд
- `features/constraints/hooks/useConstraintLookup.ts` — чистый хук: диапазоны дат → `Map<dateStr, ConstraintDto[]>`.
- `features/constraints/constraintStyles.ts` — презентационная мапа вида → цвет (UI-слой).
- `ConstraintsGridSchedule` — сетка ограничений одной сущности; аббревиатура в каждой паре дня,
  тултип = полное имя + период + описание.
- `ConstraintsManager` (раздел «Ограничения») — выбор сущности (тип + объект) + период, сетка,
  легенда, **создание** (`ConstraintFormModal`) и **удаление** ограничений (список под сеткой).
- `ConstraintFormModal` — переиспользуемая модалка создания (вид из `EnumContext`, даты, описание);
  `ConstraintsService` получил `create*`/`delete*` для трёх типов.

### Ограничения в сетке расписания
- На пустой ячейке дня с ограничением — **чёрная аббревиатура** (раньше была иконка).
- **Конфликт** «занятие + ограничение в одной ячейке» (быть не должно) помечается ошибкой:
  красный фон + красная обводка + значок `AlertTriangle` + строка `⚠ КОНФЛИКТ…` в тултипе.
  Это и есть индикатор для ограничений, добавленных уже после генерации.

### Что дальше по ограничениям
- ✅ **B: ввод ограничений перед генерацией** — сделано 2026-06-29 (вкладка «Ограничения» в
  планировщике, `ConstraintsWorkspace` со счётчиком «Учтено за период»).
- ✅ **Рисование мышью** — сделано 2026-07-03 (режим «Кисть» в сетке и Гант с drag-диапазоном).
- ⏳ **Рекуррентность в модели** (`weekday` на `*_constraint`): «все понедельники» до сих пор
  материализуются как десятки однодневных строк, фронт прячет это эвристикой. См. FOLLOWUPS.
- ⏳ **Bulk-эндпоинты** для кисти/ластика (сейчас N запросов с фронта).

---

## Следующие шаги

> **Живой список задач и техдолга — в [FOLLOWUPS.md](FOLLOWUPS.md).** Он обновляется по ходу
> работы; здесь только крупными мазками, чтобы этот раздел не расходился с реальностью.

Сделано с момента прошлой правки этого документа: перенос занятий (включая цепочки) с фронтом,
ручная раскладка и пины, генерация по периодам и инкрементальная по дисциплинам, ограничения до
генерации (сетка + гант + кисть), доска раскладки, экспорт в Excel, отчёты (готовность периода,
плотность групп, качество расписания преподавателей), подсветка нарушений порядка изучения (шаг 1).

**2026-07-13 — целостность read-модели** (после бага «неудаляемое занятие-призрак»): каскады БД
сносили размещения мимо приложения, и в `schedule_view` оставались строки без размещения. Закрыто
на двух уровнях: инвариант «строка не переживает своё размещение» ушёл в схему (FK, миграция 017),
а инвариант «снимок не врёт» получил владельца в коде — пакет **`ru.services.projection`**
(`ProjectionMaintenance` — единая дверь для мутаторов master-данных, `ProjectionSource` — Strategy
как enum «от сущности → к затронутым размещениям», `ProjectionHealthService` — сверка Command/Query
для баннера на дашборде). Плюс предпросмотр последствий удаления аудитории и занятия плана.
Устройство и границы — в [CQRS_ARCHITECTURE.md](CQRS_ARCHITECTURE.md), раздел «Инварианты read-модели».

**2026-07-21 — новый виток: оргструктура и импорт.** Появилось расписание, сгенерированное
сторонней программой (от неё пока не отказываемся) — его надо импортировать и править у нас.
Предусловие — подразделения: `org_unit` самоссылочным деревом (Composite), политика вложенности
рангом вида в `OrgUnitType`, привязка преподавателей и групп одной nullable FK, год набора у
группы. Устройство, решения и остаток — в [FOLLOWUPS.md](FOLLOWUPS.md), схема — в
[DATABASE.md](DATABASE.md), контракты — в [API_EXAMPLES.md](API_EXAMPLES.md).

Ближайшее по приоритету:
- ✅ **Optimistic lock ПОЧИНЕН (2026-07-14)** — было: `@Version` не рос при мутациях (версию
  поднимали только генерационные из **восьми** путей записи в `lesson_placement`), конкурентная
  защита была декоративна. Закрыто единой дверью **`ScheduleSessionGate`** (`ru.services.session`):
  сверка `expectedVersion` + `@Lock(OPTIMISTIC_FORCE_INCREMENT)` неразделимо, 409 через
  `CommandExceptionHandler`. Заодно `move`+`reorder` слиты в одну транзакцию. Подтверждено живым
  прогоном (два окна: `74→75`, второе → 409). Устройство — в
  [CQRS_ARCHITECTURE.md](CQRS_ARCHITECTURE.md), остаток проверок — в FOLLOWUPS.
- ✅ **Свежесть чтения — SSE (2026-07-14)** — было: push/поллинга нет вообще, вторая вкладка живёт
  со снимком до F5. Сделано: `GET /api/schedule/query/stream/{sessionId}` (`ru.services.stream`,
  `ScheduleStreamController`), звонок `{sessionId, version}` шлётся **после** записи в
  `schedule_view` (`ScheduleProjectedEvent`), передаются не данные, а сигнал; пачки схлопываются.
  `setTimeout(1000)`-костыли убраны (остались запасным путём при обрыве потока). ⚠️ Граница:
  реестр эмиттеров — в памяти процесса (несколько инстансов → шина PG `LISTEN/NOTIFY`).
- ⏳ **Кэш workspace** — пересоздание стоит 120–165 мс на КАЖДУЮ подсветку (замеры и готовое
  решение — в FOLLOWUPS). Самая большая оставшаяся победа по скорости; **главный блокер снят** —
  версия честная с 2026-07-14. Ключ = `(sessionId, version)` **плюс** подписка на
  `ProjectionStaleEvent` (каскады БД идут мимо приложения и версию не двигают) и `evictAll()` при
  правке ограничений. Предусловия: `try/finally` в обоих подборах, кэш ячеек внутрь workspace,
  снять глотание исключений в `ScheduleMoveController`.
- 🔴 **Живой баг: Пт-4 открыта** — `ScheduleDaysSlotsConfig` мапит пятницу на `WEEKDAY`, хотя
  правило заказчика — 4-я пара в пятницу не грузится; в осеннем расписании на неё встало 5 занятий.
  Готовое правило `BlockedDaysAndSlots.FRIDAY` существует, но **не подключено**. Фикс тривиален,
  см. [NORTH_STAR_FAIR_GENERATION.md](NORTH_STAR_FAIR_GENERATION.md) §2 и §8.
- ⏳ **Импорт расписания из сторонней программы** — следующий крупный этап; кода нет, спецификация
  и принятые решения готовы ([IMPORT_FORMAT.md](IMPORT_FORMAT.md),
  [IMPORT_DISCUSSION_WIP.md](IMPORT_DISCUSSION_WIP.md)).
- ✅ **Аудитории (2026-07-16…17)** — комната стала ресурсом (`AuditoriumResource`), политика подбора
  вынесена в `AuditoriumSelector` (область → `isFree` жёстко → предпочтение сортировкой), появился
  датчик (`auditorium-health` + баннеры) и **смена комнаты вручную** (`auditorium-options` + `PATCH
  .../auditorium`, набор комнат). Резервная ветка больше не раздаёт занятые и тесные комнаты вслепую.
  Замеры, решения (занятость жёсткая / вместимость мягкая) и остаток — в FOLLOWUPS.
- ✅/⏳ **Пересортировка и комнаты** — фаза 1 в коде (ждёт живого прогона): комната больше не едет с
  занятием вслепую. Взаимозаменяемая остаётся с ячейкой (провабельно ноль новых конфликтов), жёсткая
  проверяется на занятость **в памяти** (перестановка биективна → workspace не нужен) и при занятости
  уходит в базовую + флаг `AUDITORIUM_CONFLICT`. Новый контракт `ReorderRoomResolver` (шов под фазу 2
  — переподбор через `AuditoriumSelector`). Детали — в FOLLOWUPS.
- ⏳ Доска раскладки весит 2.3 МБ — расщепить на счётчики + занятия по требованию.
- ⏳ `LocalSearchOptimizer` (Фаза 3) выключен; `CompactDateFinder` не написан. **Возвращать Фазу 3
  в прежнем виде не надо:** она оптимизировала величину, которая на насыщенной сетке не растёт
  (суммарную компактность), случайными перестановками — разбор и правильная цель (минимакс по
  преподавателю, прицельные обмены) в [NORTH_STAR_FAIR_GENERATION.md](NORTH_STAR_FAIR_GENERATION.md).
- ⏳ **Экзамены генерацией не размещаются вовсе** — `distributeExams()` в `DistributionDiscipline`
  это TODO-заглушка, экзамены отфильтровываются. Следствие видно в экспорте: колонка «Отчёт» не
  получает ЭКЗ.

Swagger/OpenAPI — **уже есть** (`springdoc`, `http://localhost:8080/swagger-ui.html`), см.
[TYPES_AND_SWAGGER.md](TYPES_AND_SWAGGER.md).

---

*Документ обновлён 2026-08-07: раздел «Следующие шаги» сверен с кодом — оптимистичный лок и SSE
переведены в ✅ (были 🔴/⏳ с 2026-07-12, тогда как починены 2026-07-14), добавлены живой баг Пт-4,
незакрытые экзамены и ссылки на аудит / north-star / спецификацию импорта. Оргструктура (контроллер,
пакеты, раздел фронта) — 2026-07-21; архитектурные разделы выше — от 2026-06-27.*
*Текущая ветка: feat-final-schedule-in-bd*
