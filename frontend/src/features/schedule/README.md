# Schedule Feature - Frontend

## 📋 Обзор

Feature для работы с расписанием с поддержкой CQRS архитектуры.

## 🎯 Возможности

### CQRS Integration

#### Command Side (запись)
- ✅ Создание сессий расписания
- ✅ Генерация расписания с сохранением в БД
- ✅ Перенос занятий с optimistic lock
- ✅ Управление версиями сессий
- ✅ Поиск вариантов для переноса занятий

#### Query Side (чтение)
- ✅ Быстрое получение расписания студентов (8-12ms)
- ✅ Получение расписания преподавателей
- ✅ Проверка свободности аудиторий
- ✅ Денормализованные данные без JOIN

## 📁 Структура

```
src/features/schedule/
├── components/
│   ├── ScheduleManager.tsx          # Главный компонент с CQRS панелью
│   ├── AcademicGridSchedule.tsx     # Академическая сетка (с переносом)
│   ├── CompactPaperSchedule.tsx     # Компактный вид (бумажный)
│   ├── MoveLessonDialog.tsx         # Диалог переноса занятий
│   ├── ConflictAlert.tsx             # Компоненты конфликтов
│   └── SessionStatusCard.tsx        # Карточки статуса сессии
├── services/
│   ├── apiServices.ts               # Базовые API сервисы
│   └── cqrsApiService.ts            # CQRS API сервис
├── types/
│   ├── api.ts                       # Базовые типы API
│   └── cqrs.ts                      # CQRS типы
└── README.md                        # Этот файл
```

## 🚀 Ключевые компоненты

### ScheduleManager

Главный компонент с полной поддержкой CQRS.

**Возможности:**
- Фильтрация по группам, преподавателям, аудиториям
- Переключение видов (академическая сетка / бумажный)
- CQRS панель управления:
  - Отображение статуса сессии
  - Генерация расписания
  - Режим редактирования
  - Обработка конфликтов версий

**Props:**
```typescript
interface ScheduleManagerProps {
  lessons: ScheduledLessonDto[];
  startDate: Date;
  endDate: Date;
  // TODO: добавить CQRS props
  currentSession?: ScheduleSessionDto;
  onSessionChange?: (session: ScheduleSessionDto) => void;
}
```

### AcademicGridSchedule

Академическая сетка с поддержкой переноса занятий.

**Возможности:**
- Академическая сетка (дни × пары)
- Масштабирование (MIN/MID/MAX)
- Полноэкранный режим
- **Режим редактирования** - клик для переноса занятий
- Интеграция с MoveLessonDialog

**Props:**
```typescript
interface AcademicGridScheduleProps {
  lessons: ScheduledLessonDto[];
  startDate: Date;
  endDate: Date;
  constraints?: any[];
  isEditMode?: boolean;           // NEW: режим редактирования
  sessionId?: string;             // NEW: ID сессии для переноса
  currentVersion?: number;        // NEW: версия для optimistic lock
  onMoveLesson?: (placementId: string) => void; // NEW: коллбэк переноса
}
```

### MoveLessonDialog

Диалоговое окно для поиска и переноса занятий.

**Возможности:**
- Поиск вариантов переноса через `findMoveOptions`
- Отображение вариантов с оценками (score)
- Перенос с optimistic lock
- Обработка конфликтов версий
- Автоматическое обновление данных

**Пример использования:**
```tsx
<MoveLessonDialog
  placement={selectedLesson}
  sessionId={session.id}
  currentVersion={session.version}
  onMoveSuccessful={() => {
    console.log('Занятие перенесено!');
    reloadSchedule();
  }}
  onCancel={() => setSelectedLesson(null)}
/>
```

### ConflictAlert

Компоненты для отображения конфликтов optimistic lock.

**Варианты:**
- `ConflictAlert` - полноформатное уведомление
- `ConflictBadge` - компактный бейдж

**Пример:**
```tsx
<ConflictAlert
  conflict={{
    error: 'OPTIMISTIC_LOCK_CONFLICT',
    message: 'Данные были изменены другим пользователем',
    currentVersion: 7
  }}
  onReload={() => reloadSession()}
/>
```

### SessionStatusCard

Компоненты для отображения статуса сессии.

**Варианты:**
- `SessionStatusCard` - карточка с детальной информацией
- `SessionStatusBadge` - компактный бейдж
- `SessionInfo` - детальная информация о сессии

**Статусы:**
- `INITIALIZED` - сессия создана
- `GENERATING` - идёт генерация
- `READY_FOR_EDIT` - готово к редактированию
- `FINAL` - финализировано
- `ARCHIVED` - архивировано

## 📊 CQRS API Service

### Command Side

```typescript
// Создать сессию
const session = await CQRSService.createSchedule({
  name: 'Расписание 2024 осень',
  courseIds: [1, 2, 3]
});

// Генерировать расписание
const generated = await CQRSService.generateSchedule({
  name: 'Расписание 2024 осень',
  courseIds: [1, 2, 3]
});

// Перенести занятие
const result = await CQRSService.moveLesson(sessionId, {
  placementId: 'placement-123',
  newDate: '2024-09-15',
  newSlot: 'SECOND',
  auditoriumIds: [1, 2],
  version: 5
});

// Найти варианты переноса
const options = await CQRSService.findMoveOptions({
  sessionId: 'session-123',
  lessonId: 456,
  rootEntityId: 1,
  rootEntityType: 'EDUCATOR'
});
```

### Query Side (быстро!)

```typescript
// Расписание студента - 8-12ms!
const schedule = await CQRSService.getStudentSchedule(
  streamId,
  '2024-09-01',
  '2024-12-31'
);

// Расписание преподавателя
const educatorSchedule = await CQRSService.getEducatorSchedule(
  educatorId,
  '2024-09-15'
);

// Проверить свободность аудитории
const isFree = await CQRSService.checkAuditoriumFree(
  auditoriumId,
  '2024-09-15',
  'SECOND'
);
```

## 🎨 Примеры использования

### Базовое использование

```tsx
import { ScheduleManager } from '@/features/schedule/components/ScheduleManager';
import { CQRSService } from '@/features/schedule/services/cqrsApiService';

function App() {
  const [lessons, setLessons] = useState<ScheduledLessonDto[]>([]);
  const [session, setSession] = useState<ScheduleSessionDto | null>(null);

  // Загрузить занятия
  useEffect(() => {
    CQRSService.getStudentSchedule(1, '2024-09-01', '2024-12-31')
      .then(setLessons);
  }, []);

  return (
    <ScheduleManager
      lessons={lessons}
      startDate={new Date('2024-09-01')}
      endDate={new Date('2024-12-31')}
      currentSession={session}
      onSessionChange={setSession}
    />
  );
}
```

### С генерацией расписания

```tsx
function ScheduleGenerator() {
  const [session, setSession] = useState<ScheduleSessionDto | null>(null);

  const handleGenerate = async () => {
    // Создаём сессию
    const newSession = await CQRSService.createSchedule({
      name: 'Расписание 2024 осень',
      courseIds: [1, 2, 3]
    });

    // Генерируем расписание
    const generated = await CQRSService.generateSchedule({
      name: 'Расписание 2024 осень',
      courseIds: [1, 2, 3]
    });

    setSession(generated);
  };

  return (
    <div>
      {!session ? (
        <button onClick={handleGenerate}>
          Сгенерировать расписание
        </button>
      ) : (
        <ScheduleManager
          lessons={[]} // загрузить из Query Side
          startDate={new Date('2024-09-01')}
          endDate={new Date('2024-12-31')}
          currentSession={session}
          onSessionChange={setSession}
        />
      )}
    </div>
  );
}
```

### С обработкой конфликтов

```tsx
function ScheduleEditor() {
  const [session, setSession] = useState<ScheduleSessionDto | null>(null);
  const [conflict, setConflict] = useState<ConflictResponse | null>(null);

  const handleMoveLesson = async (placementId: string) => {
    if (!session) return;

    try {
      // Переносим занятие
      await CQRSService.moveLesson(session.id, {
        placementId,
        newDate: '2024-09-15',
        newSlot: 'SECOND',
        auditoriumIds: [1],
        version: session.version
      });

      // Обновляем сессию
      const updated = await CQRSService.getSession(session.id);
      setSession(updated);
      setConflict(null);
    } catch (error: any) {
      if (error.response?.status === 409) {
        // Конфликт версий!
        setConflict(error.response.data);
      }
    }
  };

  const handleReload = async () => {
    if (!session) return;
    const updated = await CQRSService.getSession(session.id);
    setSession(updated);
    setConflict(null);
  };

  return (
    <div>
      {conflict && (
        <ConflictAlert
          conflict={conflict}
          onReload={handleReload}
        />
      )}

      <AcademicGridSchedule
        lessons={lessons}
        startDate={startDate}
        endDate={endDate}
        isEditMode={true}
        sessionId={session?.id}
        currentVersion={session?.version}
        onMoveLesson={handleMoveLesson}
      />
    </div>
  );
}
```

## 🔧 Конфигурация

### API Base URL

```typescript
// src/services/apiClient.ts
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
});

export default api;
```

### Оптимистичная блокировка

```typescript
// Автоматическая обработка 409 Conflict
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 409) {
      // Конфликт версий
      console.error('Optimistic lock conflict:', error.response.data);
    }
    return Promise.reject(error);
  }
);
```

## 📚 Документация

- [CQRS Integration Guide](./CQRS_INTEGRATION_GUIDE.md) - подробное руководство по интеграции
- [Backend CQRS Documentation](../../../../../CQRS_ARCHITECTURE.md) - архитектура CQRS
- [API Examples](../../../../../API_EXAMPLES.md) - примеры API запросов

## 🐛 Troubleshooting

### Проблема: "Конфликт версий при каждом переносе"

**Решение:** Обновляйте сессию после каждого переноса
```typescript
const result = await CQRSService.moveLesson(sessionId, request);
if (result.success) {
  const updated = await CQRSService.getSession(sessionId);
  setCurrentSession(updated);
}
```

### Проблема: "Медленная загрузка расписания"

**Решение:** Используйте Query Side вместо Command Side
```typescript
// ❌ Медленно
const placements = await CQRSService.getPlacements(sessionId);

// ✅ Быстро (8-12ms)
const schedule = await CQRSService.getStudentSchedule(streamId, startDate, endDate);
```

---

**Последнее обновление:** 2024-09-13
**Версия:** 1.0.0
