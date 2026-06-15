# План архитектурного рефакторинга системы расписания

**Дата:** 2026-06-10  
**Текущая ветка:** `feature-front`  
**Статус:** Проектирование новой архитектуры

---

## Проблема: ScheduleMoveController реализован неправильно

### Обнаружено:

**В файле `ScheduleMoveController.java` (строка 29):**
```java
ScheduleWorkspace workspace = generationService.getWorkspace();
```

**В файле `ScheduleGenerationService.java`:**
- ❌ НЕТ метода `getWorkspace()`
- ✅ Есть только `generateForCourse()` и `generateForCourseList()`, которые возвращают **временный** workspace
- После возврата из этих методов workspace уничтожается (garbage collector)

**В файле `ScheduleGenerationController.java`:**
- POST `/api/schedule/generate` создает workspace, генерирует расписание, возвращает DTO
- После этого workspace **теряется** (нет персистентности)

### Суть проблемы:

`ScheduleMoveController` пытается вызвать **несуществующий метод** `getWorkspace()`, а workspace не сохраняется между запросами.

---

## Цели новой архитектуры

### Функциональные требования:

1. **Поэтапная генерация расписания:**
   - Сгенерировать лекции
   - Внести ручные правки
   - Сгенерировать практики для выбранных дисциплин
   - Снова внести правки

2. **Сохранение состояния между запросами:**
   - Workspace должен персистентно храниться
   - Возможность вернуться к редактированию позже
   - Multi-user поддержка

3. **История изменений:**
   - Кто, когда, какие изменения внес
   - Возможность Undo/Redo (опционально)
   - Аудит всех действий

4. **Гибкость и расширяемость:**
   - Легко добавлять новые типы команд
   - Соблюдение SOLID принципов
   - Тестируемость компонентов

---

## Архитектурные варианты

### Вариант 1: Простое сохранение в поле Service (Quick & Dirty) ❌

```java
@Service
public class ScheduleGenerationService {
    private ScheduleWorkspace currentWorkspace;
    
    public ScheduleWorkspace generateForCourseList(List<Integer> courseIds) {
        this.currentWorkspace = // ... генерация
        return currentWorkspace;
    }
    
    public ScheduleWorkspace getWorkspace() {
        return currentWorkspace;
    }
}
```

**Проблемы:**
- ❌ Не работает в multi-user окружении
- ❌ Нет персистентности при рестарте сервера
- ❌ Нет истории изменений

---

### Вариант 2: Repository Pattern (Баланс) ⚠️

```java
@Entity
public class ScheduleState {
    @Id UUID id;
    LocalDate startDate;
    LocalDate endDate;
    // Сериализуем grid в JSON/BLOB
    @Column String gridData; 
    @Column String resourcesData;
}

@Repository
public interface ScheduleStateRepository extends JpaRepository<ScheduleState, UUID> {
}
```

**Плюсы:**
- ✅ Персистентность
- ✅ Multi-user
- ✅ Просто реализовать

**Минусы:**
- ❌ Сложно сериализовать сложные объекты (ScheduleWorkspace)
- ❌ История изменений неявная (только текущее состояние)
- ❌ Сложно реализовать Undo/Redo

---

### Вариант 3: Command Pattern + Event Sourcing (Максимальная гибкость) ⭐

**Идея:** Каждое действие - команда. Сохраняем только команды (события). Восстанавливаем состояние, реплеируя команды.

```java
// Каждое действие - команда
public interface ScheduleCommand {
    ScheduleContext execute(ScheduleContext context);
    ScheduleContext undo(ScheduleContext context);
}

// Примеры команд
public class GenerateLecturesCommand implements ScheduleCommand {
    private List<Integer> courseIds;
    // ...
}

public class MoveLessonCommand implements ScheduleCommand {
    private Integer lessonId;
    private CellForLesson targetCell;
    // ...
}

public class GeneratePracticesCommand implements ScheduleCommand {
    private List<Integer> disciplineCourseIds;
    // ...
}

// Сохраняем только команды (события)
@Entity
public class ScheduleEvent {
    @Id UUID id;
    UUID scheduleId;
    String commandType; // "GENERATE_LECTURES", "MOVE_LESSON"
    String commandData; // JSON с параметрами
    LocalDateTime timestamp;
}

// Восстанавливаем состояние, реплеируя команды
public ScheduleContext restoreFromEvents(UUID scheduleId) {
    List<ScheduleEvent> events = eventRepository.findByScheduleIdOrderByTimestamp(scheduleId);
    ScheduleContext context = new ScheduleContext();
    for (ScheduleEvent event : events) {
        ScheduleCommand command = deserializeCommand(event);
        context = command.execute(context);
    }
    return context;
}
```

**Плюсы:**
- ✅ Полная история изменений
- ✅ Undo/Redo (легко!)
- ✅ Аудит всех действий
- ✅ Отлично для отладки
- ✅ Можно восстановить любое состояние в любом моменте

**Минусы:**
- ❌ Сложность реализации
- ❌ Производительность при большом количестве событий (но можно оптимизировать snapshot-ами)
- ❌ Требует redesign существующего кода

---

## Рекомендуемый подход: Hybrid Approach ⭐⭐⭐

Комбинируем лучшие практики для вашей задачи:

### Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                  ScheduleWorkflowManager                    │
│              (Оркестрация всего процесса)                   │
└─────────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌──────────────────┐ ┌──────────────┐ ┌───────────────┐
│ ScheduleRepository│ │ CommandChain │ │EventPublisher  │
│   (Сохранение)   │ │  (Выполнение)│ │  (История)     │
└──────────────────┘ └──────────────┘ └───────────────┘
```

### Ключевые компоненты

#### 1. ScheduleState (Агрегат) - Хранение текущего состояния

```java
@Entity
public class ScheduleState {
    @Id
    private UUID id;
    
    private String name; // "Расписание 2026-осень"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Сериализуемое состояние
    @Lob
    private String workspaceSnapshot; // JSON Workspace
    
    // Метаданные для быстрого доступа
    private Set<Integer> courseIds;
    private int totalLessons;
    private int placedLessons;
    
    // Статус workflow
    @Enumerated(EnumType.STRING)
    private WorkflowStatus status; // GENERATING, READY_FOR_CORRECTIONS, FINAL
}
```

#### 2. Command Pattern (Flexibility) - Гибкость выполнения

```java
public interface ScheduleCommand {
    String getCommandType();
    ScheduleWorkspace execute(ScheduleWorkspace workspace);
    boolean canUndo();
}

// Примеры команд
@Component
public class GenerateLecturesCommand implements ScheduleCommand {
    private List<Integer> courseIds;
    private final ScheduleGenerationService generationService;
    
    @Override
    public ScheduleWorkspace execute(ScheduleWorkspace workspace) {
        // Логика генерации лекций
        return generationService.generateForCourseList(courseIds);
    }
    
    @Override
    public String getCommandType() {
        return "GENERATE_LECTURES";
    }
    
    @Override
    public boolean canUndo() {
        return false; // Генерацию нельзя отменить простой командой undo
    }
}

@Component
public class MoveLessonCommand implements ScheduleCommand {
    private Integer lessonId;
    private CellForLesson targetCell;
    
    @Override
    public ScheduleWorkspace execute(ScheduleWorkspace workspace) {
        // Логика переноса занятия
        Lesson lesson = findLesson(workspace, lessonId);
        CellForLesson oldCell = workspace.getCellForLesson(lesson);
        workspace.removePlacement(lesson);
        workspace.forcePlacement(lesson, targetCell, lesson.getAssignedAuditoriums());
        return workspace;
    }
    
    @Override
    public String getCommandType() {
        return "MOVE_LESSON";
    }
    
    @Override
    public boolean canUndo() {
        return true; // Перенос можно отменить
    }
}

@Component
public class GeneratePracticesCommand implements ScheduleCommand {
    private List<Integer> disciplineCourseIds;
    
    @Override
    public ScheduleWorkspace execute(ScheduleWorkspace workspace) {
        // Логика генерации практик для конкретных дисциплин
        // Используем существующий workspace, не пересоздавая
        return distributionService.generatePractices(workspace, disciplineCourseIds);
    }
    
    @Override
    public String getCommandType() {
        return "GENERATE_PRACTICES";
    }
    
    @Override
    public boolean canUndo() {
        return false;
    }
}
```

#### 3. Repository Pattern (Persistence) - Сохранение состояния

```java
@Repository
public interface ScheduleStateRepository extends JpaRepository<ScheduleState, UUID> {
    Optional<ScheduleState> findTopByOrderByUpdatedAtDesc();
    List<ScheduleState> findByStatus(WorkflowStatus status);
    Optional<ScheduleState> findByIdAndStatus(UUID id, WorkflowStatus status);
}
```

#### 4. Workflow Manager (Orchestration) - Оркестрация процесса

```java
@Service
public class ScheduleWorkflowManager {
    
    private final ScheduleStateRepository stateRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    // Создать новый workspace
    public ScheduleState createWorkspace(String name, List<Integer> courseIds) {
        ScheduleState state = new ScheduleState(name, courseIds);
        state.setStatus(WorkflowStatus.INITIALIZED);
        return stateRepository.save(state);
    }
    
    // Применить команду к workspace
    public ScheduleState applyCommand(UUID stateId, ScheduleCommand command) {
        ScheduleState state = stateRepository.findById(stateId)
            .orElseThrow(() -> new NotFoundException("ScheduleState not found"));
        
        // Десериализуем текущий workspace
        ScheduleWorkspace workspace = deserializeWorkspace(state.getWorkspaceSnapshot());
        
        // Выполняем команду
        ScheduleWorkspace updatedWorkspace = command.execute(workspace);
        
        // Сериализуем и сохраняем
        state.updateWorkspaceSnapshot(serializeWorkspace(updatedWorkspace));
        state.setUpdatedAt(LocalDateTime.now());
        
        // Публикуем событие (для истории)
        eventPublisher.publishEvent(new ScheduleCommandEvent(stateId, command));
        
        return stateRepository.save(state);
    }
    
    // Получить текущий workspace
    public ScheduleWorkspace getCurrentWorkspace(UUID stateId) {
        ScheduleState state = stateRepository.findById(stateId)
            .orElseThrow(() -> new NotFoundException("ScheduleState not found"));
        return deserializeWorkspace(state.getWorkspaceSnapshot());
    }
    
    // Вспомогательные методы для сериализации/десериализации
    private String serializeWorkspace(ScheduleWorkspace workspace) {
        try {
            // TODO: Реализовать сериализацию ScheduleWorkspace в JSON
            return objectMapper.writeValueAsString(workspace);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize workspace", e);
        }
    }
    
    private ScheduleWorkspace deserializeWorkspace(String json) {
        try {
            // TODO: Реализовать десериализацию JSON в ScheduleWorkspace
            return objectMapper.readValue(json, ScheduleWorkspace.class);
        } catch (JsonProcessingException e) {
            throw new DeserializationException("Failed to deserialize workspace", e);
        }
    }
}
```

#### 5. Event Sourcing (опционально) - История изменений

```java
@Entity
public class ScheduleCommandEvent {
    @Id
    private UUID id;
    
    private UUID scheduleStateId;
    private String commandType;
    private String commandData; // JSON команды
    private LocalDateTime timestamp;
    private String executedBy; // Пользователь
}

@Repository
public interface ScheduleCommandEventRepository extends JpaRepository<ScheduleCommandEvent, UUID> {
    List<ScheduleCommandEvent> findByScheduleStateIdOrderByTimestampAsc(UUID stateId);
}
```

---

## Пример использования (REST API)

```java
@RestController
@RequestMapping("/api/schedule/workflow")
public class ScheduleWorkflowController {
    
    private final ScheduleWorkflowManager workflowManager;
    
    // 1. Создать новый workflow
    @PostMapping
    public ResponseEntity<UUID> createWorkflow(@RequestBody CreateWorkflowRequest request) {
        UUID stateId = workflowManager.createWorkspace(
            request.getName(), 
            request.getCourseIds()
        ).getId();
        return ResponseEntity.ok(stateId);
    }
    
    // 2. Сгенерировать лекции (Phase 1)
    @PostMapping("/{stateId}/lectures")
    public ResponseEntity<Void> generateLectures(
        @PathVariable UUID stateId, 
        @RequestBody GenerateLecturesRequest request
    ) {
        GenerateLecturesCommand command = new GenerateLecturesCommand(request.getCourseIds());
        workflowManager.applyCommand(stateId, command);
        return ResponseEntity.ok().build();
    }
    
    // 3. Перенести занятие (ручная правка)
    @PostMapping("/{stateId}/move")
    public ResponseEntity<Void> moveLesson(
        @PathVariable UUID stateId, 
        @RequestBody MoveLessonRequest request
    ) {
        MoveLessonCommand command = new MoveLessonCommand(
            request.getLessonId(), 
            request.getTargetCell()
        );
        workflowManager.applyCommand(stateId, command);
        return ResponseEntity.ok().build();
    }
    
    // 4. Сгенерировать практики для конкретных дисциплин (Phase 2)
    @PostMapping("/{stateId}/practices")
    public ResponseEntity<Void> generatePractices(
        @PathVariable UUID stateId, 
        @RequestBody GeneratePracticesRequest request
    ) {
        GeneratePracticesCommand command = new GeneratePracticesCommand(
            request.getDisciplineIds()
        );
        workflowManager.applyCommand(stateId, command);
        return ResponseEntity.ok().build();
    }
    
    // 5. Получить текущее расписание
    @GetMapping("/{stateId}")
    public ResponseEntity<ScheduleResultDto> getSchedule(@PathVariable UUID stateId) {
        ScheduleWorkspace workspace = workflowManager.getCurrentWorkspace(stateId);
        return ResponseEntity.ok(convertToDto(workspace));
    }
    
    // 6. Получить историю изменений
    @GetMapping("/{stateId}/history")
    public ResponseEntity<List<ScheduleCommandEventDto>> getHistory(@PathVariable UUID stateId) {
        List<ScheduleCommandEvent> events = eventRepository
            .findByScheduleStateIdOrderByTimestampAsc(stateId);
        return ResponseEntity.ok(convertToDto(events));
    }
    
    // 7. Undo последней команды (опционально)
    @PostMapping("/{stateId}/undo")
    public ResponseEntity<Void> undoLastCommand(@PathVariable UUID stateId) {
        workflowManager.undo(stateId);
        return ResponseEntity.ok().build();
    }
}
```

---

## Преимущества рекомендуемого подхода

### SOLID принципы:

✅ **S (Single Responsibility):**
- `ScheduleState` - хранение состояния
- `ScheduleCommand` - выполнение действия
- `ScheduleWorkflowManager` - оркестрация
- `ScheduleStateRepository` - персистентность

✅ **O (Open/Closed):**
- Легко добавлять новые команды без изменения существующего кода
- Просто добавить `GenerateSeminarsCommand`, `OptimizeScheduleCommand`

✅ **L (Liskov Substitution):**
- Все команды взаимозаменяемы через интерфейс `ScheduleCommand`

✅ **I (Interface Segregation):**
- Разные интерфейсы для разных уровней абстракции
- Команды не зависят от деталей реализации WorkflowManager

✅ **D (Dependency Inversion):**
- Зависимости от абстракций (`ScheduleCommand`), не от конкретики
- Легко мокать для тестирования

### Функциональные преимущества:

✅ **Гибкость:**
- Генерация поэтапная: лекции → правки → практики
- Возможность прервать и продолжить позже
- Mix автоматических и ручных изменений

✅ **Масштабируемость:**
- Multi-user (разные workspace для разных пользователей)
- История изменений (через Event Sourcing)
- Undo/Redo (если нужно)

✅ **Тестируемость:**
- Каждая команда тестируется отдельно
- Легко мокать зависимости
- Интеграционные тесты для WorkflowManager

---

## План реализации (Phased Approach)

### Phase 1: MVP (Minimum Viable Product) ⏰ 2-3 недели

**Цель:** Базовая функциональность с сохранением состояния

1. **Создать инфраструктуру:**
   - ✅ `ScheduleState` entity
   - ✅ `ScheduleStateRepository` 
   - ✅ `ScheduleWorkflowManager`
   - ✅ Базовая сериализация/десериализация ScheduleWorkspace

2. **Рефакторинг существующего кода:**
   - ✅ Создать `GenerateLecturesCommand`
   - ✅ Создать `GeneratePracticesCommand`
   - ✅ Создать `MoveLessonCommand`
   - ✅ Адаптировать существующие сервисы под Command Pattern

3. **REST API:**
   - ✅ POST `/api/schedule/workflow` - создать workflow
   - ✅ POST `/api/schedule/workflow/{id}/lectures` - генерация лекций
   - ✅ POST `/api/schedule/workflow/{id}/move` - перенос занятия
   - ✅ GET `/api/schedule/workflow/{id}` - получить расписание
   - ✅ DELETE `/api/schedule/workflow/{id}` - удалить workflow

4. **Тестирование:**
   - ✅ Unit тесты для каждой команды
   - ✅ Интеграционные тесты для WorkflowManager
   - ✅ REST API тесты

---

### Phase 2: Enhanced Features ⏰ 2-3 недели

**Цель:** Улучшенная функциональность

1. **Event Sourcing:**
   - ✅ `ScheduleCommandEvent` entity
   - ✅ `ScheduleCommandEventRepository`
   - ✅ Публикация событий при выполнении команд
   - ✅ API для получения истории изменений

2. **Advanced Commands:**
   - ✅ `UndoCommand` - отмена последнего действия
   - ✅ `RedoCommand` - повтор отмененного действия
   - ✅ `OptimizeScheduleCommand` - оптимизация расписания
   - ✅ `ValidateScheduleCommand` - валидация расписания

3. **Валидация:**
   - ✅ Предварительная валидация команд перед выполнением
   - ✅ Проверка конфликтов перед применением
   - ✅ Предупреждения о потенциальных проблемах

4. **UI улучшения:**
   - ✅ Индикаторы статуса генерации
   - ✅ Прогресс-бар для долгих операций
   - ✅ Визуализация истории изменений

---

### Phase 3: Advanced Features ⏰ 3-4 недели

**Цель:** Продвинутые возможности

1. **Multi-user:**
   - ✅ Конфликты при multi-user редактировании
   - ✅ Блокировка workspace при редактировании
   - ✅ Реальное время обновлений (WebSocket)

2. **Performance:**
   - ✅ Оптимизация сериализации Workspace
   - ✅ Кэширование текущего workspace
   - ✅ Асинхронное выполнение долгих команд
   - ✅ Snapshot-ы в Event Sourcing (оптимизация реплея)

3. **Мониторинг:**
   - ✅ Метрики выполнения команд
   - ✅ Логирование операций
   - ✅ Аналитика использования

4. **Advanced UI:**
   - ✅ Drag & Drop для переноса занятий
   - ✅ Визуализация конфликтов
   - ✅ Suggestions для оптимизации

---

## Что делать завтра? 🎯

### Рекомендуемый порядок действий:

1. **Proof of Concept (2-3 часа):**
   - Попробовать сериализовать/десериализовать ScheduleWorkspace в JSON
   - Проверить, все ли данные корректно сохраняются
   - Выявить проблемы с циклическими ссылками

2. **Создать базовую инфраструктуру (1-2 дня):**
   - `ScheduleState` entity
   - `ScheduleStateRepository`
   - `ScheduleWorkflowManager` (базовая версия)
   - `CreateWorkflowRequest` DTO

3. **Рефакторинг первой команды (2-3 дня):**
   - Вынести логику генерации лекций в `GenerateLecturesCommand`
   - Адаптировать `ScheduleGenerationService` для работы с существующим workspace
   - Тестирование

4. **Рефакторинг команды переноса (1-2 дня):**
   - Исправить `ScheduleMoveController` с использованием новой архитектуры
   - Создать `MoveLessonCommand`
   - Тестирование

5. **Интеграция и тестирование (2-3 дня):**
   - Интеграционные тесты
   - REST API тесты
   - Фронтенд интеграция

---

## Технические детали для реализации

### Сериализация ScheduleWorkspace

**Проблема:** ScheduleWorkspace содержит сложные объекты с циклическими ссылками.

**Решение 1: Custom Serializer**
```java
public class WorkspaceSerializer {
    public String serialize(ScheduleWorkspace workspace) {
        // Сохраняем только нужные данные
        WorkspaceDto dto = new WorkspaceDto();
        dto.setStartDate(workspace.getGrid().getStartDate());
        dto.setEndDate(workspace.getGrid().getEndDate());
        dto.setLessons(extractLessons(workspace));
        dto.setAssignments(extractAssignments(workspace));
        // ... другие нужные данные
        return objectMapper.writeValueAsString(dto);
    }
    
    public ScheduleWorkspace deserialize(String json) {
        WorkspaceDto dto = objectMapper.readValue(json, WorkspaceDto.class);
        // Восстанавливаем Workspace из DTO
        return restoreFromDto(dto);
    }
}
```

**Решение 2: Hibernate @JsonIgnore**
```java
@Entity
public class Lesson {
    // ...
    @JsonIgnore
    @ManyToOne
    private CurriculumSlot curriculumSlot;
    
    @JsonIgnore
    @ManyToMany
    private Set<Educator> educators;
}
```

### Адаптация ScheduleGenerationService

**Было:**
```java
public ScheduleWorkspace generateForCourseList(List<Integer> courseIds) {
    // Создает новый workspace каждый раз
    ScheduleWorkspace workspace = new ScheduleWorkspace(...);
    // ...
    return workspace;
}
```

**Станет:**
```java
public ScheduleWorkspace generateLectures(ScheduleWorkspace workspace, List<Integer> courseIds) {
    // Использует существующий workspace
    List<Lesson> lessonsToPlace = lessonFactory.createLessonsForCourses(courseIds);
    List<Lesson> lectures = lessonsToPlace.stream()
        .filter(l -> l.getKindOfStudy() == KindOfStudy.LECTURE)
        .collect(Collectors.toList());
    
    distributionDiscipline.distribute(workspace, lectures, educators);
    
    return workspace;
}
```

---

## Вопросы для обсуждения

1. **Сериализация:**
   - Какой подход использовать для сериализации ScheduleWorkspace?
   - Нужно ли сохранять все данные или только результаты?

2. **Валидация:**
   - Нужно ли проверять валидность команд перед выполнением?
   - Как обрабатывать конфликты при ручном редактировании?

3. **Производительность:**
   - Как оптимизировать сериализацию большого workspace?
   - Нужно ли кэшировать текущий workspace в памяти?

4. **Multi-user:**
   - Как обрабатывать одновременное редактирование?
   - Нужна ли блокировка workspace?

---

## Полезные ссылки и ресурсы

### Паттерны:
- [Command Pattern](https://refactoring.guru/design-patterns/command)
- [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Repository Pattern](https://martinfowler.com/eaaCatalog/repository.html)

### SOLID:
- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
- [SOLID in Spring](https://www.baeldung.com/spring-solid-principles)

### Java Serialization:
- [Jackson JSON](https://github.com/FasterXML/jackson)
- [Hibernate Lazy Loading](https://docs.jboss.org/hibernate/orm/6.2/userguide/html_single/Hibernate_User_Guide.html#fetching)

---

## Резюме

**Проблема:** ScheduleMoveController вызывает несуществующий метод `getWorkspace()`, workspace не сохраняется между запросами.

**Решение:** Hybrid Approach combining:
- Command Pattern для гибкости
- Repository Pattern для персистентности
- Event Sourcing для истории изменений (опционально)
- Workflow Manager для оркестрации

**Преимущества:**
- ✅ SOLID принципы соблюдены
- ✅ Гибкость и расширяемость
- ✅ Multi-user поддержка
- ✅ История изменений
- ✅ Undo/Redo (опционально)

**План реализации:**
1. Phase 1: MVP (2-3 недели) - базовая функциональность
2. Phase 2: Enhanced Features (2-3 недели) - Event Sourcing, Undo/Redo
3. Phase 3: Advanced Features (3-4 недели) - Multi-user, Performance

**Следующие шаги:**
1. Proof of Concept: сериализация ScheduleWorkspace
2. Создать базовую инфраструктуру
3. Рефакторинг существующего кода в команды
4. Тестирование и интеграция

---

*Документ создан: 2026-06-10*  
*Автор: Claude (AI Assistant)*  
*Статус: Ожидает обсуждения и утверждения подхода*
