# Контекст разработки: Распределение занятий в расписании

**Дата:** 2026-02-26
**Основной класс:** `DistributionDiscipline` (оркестратор)

---

## Архитектура проекта

### Технологический стек
- Spring Boot 3.2.0
- Java 21
- PostgreSQL (production), H2 (development)
- Liquibase (миграции)
- MapStruct (маппинг)

### Структура пакетов
```
ru/
├── entity/          - Сущности (Lesson, Educator, Group, Auditorium)
├── abstracts/       - Базовые классы
├── services/
│   └── distribution/ - Алгоритмы распределения (РЕФАКТОРИНГ 2026-02-26)
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
├── repository/      - Spring Data JPA
├── mapper/          - MapStruct мапперы
└── enums/           - KindOfStudy, DayOfWeek, TimeSlotPair
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

1. **Добавить тесты для новых компонентов**
   - Unit тесты для каждого компонента
   - Интеграционные тесты для оркестратора

2. **Реализовать CompactDateFinder**
   - Альтернативная стратегия поиска дат
   - Более агрессивное уплотнение

3. **Оптимизировать Performance**
   - Кэширование результатов поиска
   - Оптимизация работы с цепочками

---

*Документ обновлён 2026-02-26 (после рефакторинга)*
*Коммит: 32f9de1 - refactor (DistributionDiscipline) Разделить God Class на специализированные компоненты*
