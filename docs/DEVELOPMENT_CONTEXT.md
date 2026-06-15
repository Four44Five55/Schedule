# Контекст разработки: Распределение занятий в расписании

**Дата обновления:** 2026-06-10
**Основной класс:** `DistributionDiscipline` (оркестратор)
**Текущая ветка:** `feature-front`
**Статус:** Активная разработка фронтенда и системы переноса занятий

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
- `ScheduleController` - генерация расписания
- `ScheduleMoveController` - перенос занятий (НОВОЕ 2026-06-10)
- `AssignmentController` - управление назначениями
- `EducatorController` - управление преподавателями
- `GroupController` - управление группами
- `AuditoriumController` - управление аудиториями
- `DisciplineController` - управление дисциплинами
- `AuditoriumConstraintController` - ограничения аудиторий
- `EducatorConstraintController` - ограничения преподавателей
- `GroupConstraintController` - ограничения групп

### Новые endpoints (2026-06-10):

**ScheduleMoveController**
- `POST /api/schedule/find-move-options` - поиск вариантов переноса занятия

---

## Схема БД

### Основные таблицы:

```
educator (преподаватели)
├── id, name
├── compact_schedule (флаг компактности)
└── preferences (educator_day_priority, educator_slot_priority)

study_stream (потоки/подгруппы)
├── id, name, semester
└── groups (через stream_groups)

curriculum_slot (слоты учебного плана)
├── id, position, kind_of_study
├── discipline_course_id → discipline → discipline_course
├── required_auditorium_id, priority_auditorium_id
└── allowed_pool_id

assignment (назначения)
├── curriculum_slot_id
├── study_stream_id
└── educators (через assignment_educators)
```

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

## Следующие шаги

### В разработке (feature-front):
1. **Система переноса занятий** (2026-06-10)
   - ✅ ScheduleMoveController - REST API для поиска вариантов переноса
   - ✅ MoveLessonSuggestionService - сервис поиска доступных мест
   - ✅ Каскадная фильтрация по ресурсам
   - 🔄 Frontend интеграция (в процессе)
   - ⏳ Тестирование новой функциональности
   - ⏳ Оптимизация кэширования Workspace

### Планируемые улучшения:
2. **Frontend разработка**
   - 🔄 Интеграция новых API endpoints
   - ⏳ UI для переноса занятий
   - ⏳ Dashboard для мониторинга расписания

3. **Оптимизация бэкенда**
   - ⏳ Кэширование Workspace в сессии
   - ⏳ Unit тесты для новых компонентов
   - ⏳ Интеграционные тесты для оркестратора

4. **Архитектурные улучшения**
   - ⏳ CompactDateFinder - альтернативная стратегия поиска дат
   - ⏳ Swagger/OpenAPI спецификация
   - ⏳ Оптимизация работы с цепочками

---

## Текущие изменения (git status)

**Новые файлы (в разработке):**
- ✅ `ScheduleMoveController.java` - REST API для переноса занятий
- ✅ `MoveOptionDto.java` - DTO для ответа с вариантами переноса
- ✅ `MoveSuggestionRequest.java` - DTO для запроса на перенос
- ✅ `MoveLessonSuggestionService.java` - сервис поиска доступных мест

**Измененные файлы:**
- 🔄 `ScheduleWorkspace.java` - оптимизация логики поиска аудиторий

**Ключевая функциональность:**
Система поиска доступных слотов для переноса занятий с каскадной фильтрацией:
1. По корневой сущности (группа/преподаватель/аудитория)
2. По остальным участникам занятия
3. По инфраструктуре (наличие подходящей аудитории)

---

*Документ обновлён 2026-06-10 (обновление технологического стека и статуса разработки)*
*Текущая ветка: feature-front*
*Последний коммит: c3f9774 - feat Добавить сетку расписания*
