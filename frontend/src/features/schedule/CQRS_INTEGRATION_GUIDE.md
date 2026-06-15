# CQRS Integration Guide - Frontend

## 📋 Что реализовано

### ✅ Phase 1: Типы и API сервис

#### 1. TypeScript типы (`src/types/cqrs.ts`)
Все типы для работы с CQRS архитектурой:

```typescript
// Статусы сессии
export type SessionStatus = 'INITIALIZED' | 'GENERATING' | 'READY_FOR_EDIT' | 'FINAL' | 'ARCHIVED';

// Основные DTO
export interface ScheduleSessionDto { /* ... */ }
export interface LessonPlacementDto { /* ... */ }
export interface ScheduleViewDto { /* ... */ }

// Операции
export interface MoveLessonRequest { /* ... */ }
export interface MoveLessonResult { /* ... */ }
export interface ConflictResponse { /* ... */ }

// Поиск вариантов переноса
export interface FindMoveOptionsRequest { /* ... */ }
export interface MoveOptionDto { /* ... */ }
```

#### 2. CQRS API Service (`src/services/cqrsApiService.ts`)

**Command Side (запись):**
- `createSchedule(request)` - создать пустую сессию
- `generateSchedule(request)` - генерация расписания
- `getSession(sessionId)` - получить сессию по ID
- `moveLesson(sessionId, request)` - перенести занятие с optimistic lock
- `getPlacements(sessionId)` - получить все размещения сессии
- `deleteSession(sessionId)` - удалить сессию

**Query Side (чтение):**
- `getStudentSchedule(streamId, startDate, endDate)` - расписание группы (быстро!)
- `getEducatorSchedule(educatorId, date)` - расписание преподавателя на дату
- `checkAuditoriumFree(auditoriumId, date, slot)` - проверка свободности аудитории

**Move Options:**
- `findMoveOptions(request)` - поиск вариантов для переноса занятия

### ✅ Phase 2: UI Компоненты

#### 1. MoveLessonDialog (`src/features/schedule/components/MoveLessonDialog.tsx`)
Диалоговое окно для переноса занятий:
- Поиск вариантов переноса через `findMoveOptions`
- Отображение вариантов с оценками (score)
- Перенос занятия с optimistic lock
- Обработка конфликтов версий

#### 2. ConflictAlert (`src/features/schedule/components/ConflictAlert.tsx`)
Компоненты для отображения конфликтов:
- `ConflictAlert` - полноформатное уведомление о конфликте
- `ConflictBadge` - компактный бейдж

#### 3. SessionStatusCard (`src/features/schedule/components/SessionStatusCard.tsx`)
Компоненты для отображения статуса сессии:
- `SessionStatusCard` - карточка статуса с детальной информацией
- `SessionStatusBadge` - компактный бейдж статуса
- `SessionInfo` - детальная информация о сессии

### ✅ Phase 3: Интеграция в существующие компоненты

#### 1. ScheduleManager (`src/features/schedule/components/ScheduleManager.tsx`)

**Новые возможности:**
```typescript
// CQRS State
const [isEditMode, setIsEditMode] = useState(false);
const [currentSession, setCurrentSession] = useState<ScheduleSessionDto | null>(null);
const [conflict, setConflict] = useState<ConflictResponse | null>(null);

// Методы
const handleGenerateSchedule = async () => { /* ... */ }
const handleMoveLesson = async (placementId: string) => { /* ... */ }
const handleReloadSession = async () => { /* ... */ }
const handleEnableEditMode = () => { /* ... */ }
const handleDisableEditMode = () => { /* ... */ }
```

**UI элементы:**
- Панель управления сессией (статус, версия, количество занятий)
- Кнопки: "Сгенерировать расписание", "Редактировать", "Обновить"
- Отображение конфликтов версий
- Индикаторы загрузки

#### 2. AcademicGridSchedule (`src/features/schedule/components/AcademicGridSchedule.tsx`)

**Новые props:**
```typescript
interface AcademicGridScheduleProps {
  // ... существующие props
  isEditMode?: boolean;
  sessionId?: string;
  currentVersion?: number;
  onMoveLesson?: (placementId: string) => void;
}
```

**Новые возможности:**
- Режим редактирования (изменение цвета ячеек)
- Клик по занятию для переноса (в режиме редактирования)
- Интеграция с MoveLessonDialog
- Индикатор редактируемости

## 🚀 Примеры использования

### Пример 1: Создание и генерация расписания

```typescript
import { CQRSService } from '../../../services/cqrsApiService';
import { ScheduleManager } from './components/ScheduleManager';

// В родительском компоненте
const App = () => {
  const [session, setSession] = useState<ScheduleSessionDto | null>(null);

  const handleCreateSchedule = async () => {
    // Создаём сессию
    const newSession = await CQRSService.createSchedule({
      name: 'Расписание 2024 осень',
      courseIds: [1, 2, 3]
    });
    setSession(newSession);
  };

  const handleGenerate = async () => {
    if (!session) return;

    // Генерируем расписание
    const updated = await CQRSService.generateSchedule({
      name: session.name,
      courseIds: [1, 2, 3]
    });
    setSession(updated);
  };

  return (
    <div>
      {!session ? (
        <button onClick={handleCreateSchedule}>
          Создать расписание
        </button>
      ) : (
        <ScheduleManager
          currentSession={session}
          onSessionChange={setSession}
          // ... другие props
        />
      )}
    </div>
  );
};
```

### Пример 2: Перенос занятия

```typescript
// MoveLessonDialog используется внутри AcademicGridSchedule
// При клике на занятие в режиме редактирования открывается диалог

<AcademicGridSchedule
  lessons={lessons}
  startDate={startDate}
  endDate={endDate}
  isEditMode={true}
  sessionId="session-123"
  currentVersion={5}
  onMoveLesson={(placementId) => {
    console.log('Занятие перенесено:', placementId);
    // Перезагрузить данные
    reloadSchedule();
  }}
/>
```

### Пример 3: Обработка конфликтов

```typescript
import { ConflictAlert } from './components/ConflictAlert';

const MyComponent = () => {
  const [conflict, setConflict] = useState<ConflictResponse | null>(null);

  const handleMoveLesson = async () => {
    try {
      await CQRSService.moveLesson(sessionId, {
        placementId: 'placement-123',
        newDate: '2024-09-15',
        newSlot: 'SECOND',
        auditoriumIds: [1, 2],
        version: currentVersion
      });
    } catch (error: any) {
      if (error.response?.status === 409) {
        // Конфликт версий!
        setConflict(error.response.data);
      }
    }
  };

  const handleReload = async () => {
    const reloaded = await CQRSService.getSession(sessionId);
    setCurrentSession(reloaded);
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
    </div>
  );
};
```

### Пример 4: Быстрое чтение (Query Side)

```typescript
import { CQRSService } from '../../../services/cqrsApiService';

// Получить расписание студента (ОЧЕНЬ БЫСТРО - 8-12ms)
const getStudentSchedule = async (streamId: number) => {
  const schedule = await CQRSService.getStudentSchedule(
    streamId,
    '2024-09-01',
    '2024-12-31'
  );

  // Денормализованные данные - без JOIN!
  schedule.forEach(item => {
    console.log(`${item.disciplineAbbr} - ${item.educatorName}`);
    console.log(`${item.scheduledDate} в ${item.timeSlot}`);
    console.log(`Аудитория: ${item.auditoriumName}`);
  });
};

// Получить расписание преподавателя на дату
const getEducatorSchedule = async (educatorId: number, date: string) => {
  const schedule = await CQRSService.getEducatorSchedule(educatorId, date);
  return schedule;
};

// Проверить свободность аудитории
const checkAuditorium = async (auditoriumId: number, date: string, slot: string) => {
  const isFree = await CQRSService.checkAuditoriumFree(auditoriumId, date, slot);
  console.log(isFree ? 'Аудитория свободна' : 'Аудитория занята');
};
```

## 🔧 Конфигурация

### Настройка API endpoints

Убедитесь, что `apiClient` настроен правильно:

```typescript
// src/services/apiClient.ts
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080', // ваш backend
  headers: {
    'Content-Type': 'application/json'
  }
});

// Обработка 409 Conflict для optimistic lock
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 409) {
      // Конфликт версий - передаем дальше для обработки
      return Promise.reject(error);
    }
    return Promise.reject(error);
  }
);

export default api;
```

## 📊 Мониторинг и отладка

### Логирование операций

```typescript
// Добавьте логирование для отслеживания CQRS операций
import { CQRSService } from '../../../services/cqrsApiService';

// Логирование всех операций
const originalMoveLesson = CQRSService.moveLesson;
CQRSService.moveLesson = async (...args) => {
  console.log('🔄 Перенос занятия:', args);
  const result = await originalMoveLesson(...args);
  console.log('✅ Результат:', result);
  return result;
};
```

### Проверка версий

```typescript
// Отображение текущей версии сессии
<SessionStatusCard
  session={currentSession}
  className="mb-4"
/>

// Или компактно:
<div className="flex items-center gap-2">
  <span className="text-xs text-slate-500">Версия:</span>
  <span className="font-bold">{currentSession?.version}</span>
</div>
```

## 🎯 Best Practices

### 1. Всегда обновляйте версию перед операциями записи

```typescript
// ❌ ПЛОХО - может быть устаревшая версия
await CQRSService.moveLesson(sessionId, {
  placementId: '123',
  version: oldVersion // устаревшая!
});

// ✅ ХОРОШО - всегда свежая версия
const freshSession = await CQRSService.getSession(sessionId);
await CQRSService.moveLesson(sessionId, {
  placementId: '123',
  version: freshSession.version // актуальная!
});
```

### 2. Обрабатывайте конфликты версий

```typescript
try {
  await CQRSService.moveLesson(sessionId, request);
} catch (error: any) {
  if (error.response?.status === 409) {
    const conflict: ConflictResponse = error.response.data;

    // Показать уведомление пользователю
    showConflictNotification(conflict);

    // Предложить обновить данные
    promptUserToReload();
  }
}
```

### 3. Используйте Query Side для чтения

```typescript
// ❌ ПЛОХО - медленно, много JOIN
const lessons = await api.get(`/api/schedule/lessons?streamId=${streamId}`);

// ✅ ХОРОШО - быстро, денормализованные данные
const schedule = await CQRSService.getStudentSchedule(streamId, startDate, endDate);
```

### 4. Используйте findMoveOptions для умного переноса

```typescript
// ❌ ПЛОХО - перенос без проверки
await CQRSService.moveLesson(sessionId, {
  newDate: '2024-09-15',
  newSlot: 'SECOND',
  // ... могут быть конфликты!
});

// ✅ ХОРОШО - сначала ищем варианты
const options = await CQRSService.findMoveOptions({
  sessionId,
  lessonId,
  rootEntityId: educatorId,
  rootEntityType: 'EDUCATOR'
});

// Выбираем лучший вариант
const bestOption = options[0]; // с наивысшим score

// Переносим с проверкой
await CQRSService.moveLesson(sessionId, {
  newDate: bestOption.date,
  newSlot: bestOption.timeSlot,
  auditoriumIds: bestOption.auditoriumIds
});
```

## 🐛 Troubleshooting

### Проблема: "Конфликт версий при каждом переносе"

**Причина:** Не обновляется версия сессии между операциями.

**Решение:**
```typescript
// После каждого успешного переноса обновляйте сессию
const handleMove = async () => {
  const result = await CQRSService.moveLesson(sessionId, request);

  if (result.success) {
    // Обновляем сессию с новой версией
    const updated = await CQRSService.getSession(sessionId);
    setCurrentSession(updated);
  }
};
```

### Проблема: "Медленная загрузка расписания"

**Причина:** Использование Command Side вместо Query Side.

**Решение:**
```typescript
// ❌ Command Side - медленно
const placements = await CQRSService.getPlacements(sessionId);

// ✅ Query Side - быстро
const schedule = await CQRSService.getStudentSchedule(streamId, startDate, endDate);
```

### Проблема: "Нет вариантов для переноса"

**Причина:** Все слоты заняты или не подходят по ограничениям.

**Решение:** Проверьте ограничения и создайте свободные слоты:
```typescript
const options = await CQRSService.findMoveOptions(request);

if (options.length === 0) {
  console.log('Нет свободных слотов. Проверьте ограничения:');
  console.log('- Ограничения преподавателя');
  console.log('- Ограничения группы');
  console.log('- Ограничения аудитории');
}
```

## 📚 Дополнительные ресурсы

- [Backend CQRS Documentation](../../../../../CQRS_ARCHITECTURE.md)
- [API Examples](../../../../../API_EXAMPLES.md)
- [Testing Guide](../../../../../TESTING_GUIDE.md)

---

**Последнее обновление:** 2024-09-13
**Версия:** 1.0.0
