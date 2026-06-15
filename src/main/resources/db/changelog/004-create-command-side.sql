-- ================================================================
-- CQRS Command Side: Создание таблиц schedule_session и lesson_placement
-- ================================================================
-- Write-optimized entities для CQRS Command Side.
-- Поддерживают:
-- - Persистентность размещений
-- - Optimistic locking (@Version)
-- - Аудит изменений
-- - Связь с Assignment (учебный план)
--
-- Автор: CQRS Implementation
-- Дата: 2025-01-11
-- ================================================================

-- ================================================================
-- ТАБЛИЦА: schedule_session
-- ================================================================
-- Сессия редактирования расписания.
-- Поддерживает optimistic locking (version) для конкурентного доступа.

CREATE TABLE schedule_session (
    -- Первичный ключ (UUID)
    id UUID PRIMARY KEY,

    -- Основные поля
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,

    -- Аудит (кто, когда создал)
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100) NOT NULL,

    -- Аудит (кто, когда изменил)
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),

    -- ✅ Optimistic locking (версия для предотвращения конфликтов)
    version BIGINT DEFAULT 0 NOT NULL,

    -- Опциональный snapshot workspace (JSON)
    workspace_snapshot TEXT
);

-- ================================================================
-- ТАБЛИЦА: lesson_placement
-- ================================================================
-- Размещение занятия (Write Model).
-- Хранит персистентное состояние размещения.

CREATE TABLE lesson_placement (
    -- Первичный ключ (UUID)
    id UUID PRIMARY KEY,

    -- Ссылка на сессию
    session_id UUID NOT NULL,

    -- Ссылка на заявку (Assignment)
    assignment_id INTEGER NOT NULL,

    -- Размещение (дата и время)
    scheduled_date DATE NOT NULL,
    scheduled_slot VARCHAR(50) NOT NULL,

    -- Аудит (кто, когда создал)
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100) NOT NULL,

    -- Аудит (кто, когда изменил)
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),

    -- Foreign Keys
    FOREIGN KEY (session_id) REFERENCES schedule_session(id) ON DELETE CASCADE,
    FOREIGN KEY (assignment_id) REFERENCES assignment(id) ON DELETE CASCADE,

    -- Уникальность: одно placement для assignment в сессии
    CONSTRAINT unique_assignment_in_session UNIQUE (session_id, assignment_id)
);

-- ================================================================
-- ТАБЛИЦА: placement_auditoriums (связующая таблица)
-- ================================================================
-- Связь placement ↔ аудитории (Many-to-Many).

CREATE TABLE placement_auditoriums (
    -- Composite Primary Key
    placement_id UUID NOT NULL,
    auditorium_id INTEGER NOT NULL,

    -- Foreign Keys
    FOREIGN KEY (placement_id) REFERENCES lesson_placement(id) ON DELETE CASCADE,
    FOREIGN KEY (auditorium_id) REFERENCES auditorium(id) ON DELETE CASCADE,

    PRIMARY KEY (placement_id, auditorium_id)
);

-- ================================================================
-- ИНДЕКСЫ для schedule_session
-- ================================================================

-- Индекс для поиска по статусу
CREATE INDEX idx_session_status ON schedule_session(status);

-- Индекс для поиска по пользователю
CREATE INDEX idx_session_user ON schedule_session(created_by);

-- Индекс для поиска устаревших сессий
CREATE INDEX idx_session_updated ON schedule_session(updated_at);

-- ================================================================
-- ИНДЕКСЫ для lesson_placement
-- ================================================================

-- Индекс для поиска по сессии (самый частый запрос)
CREATE INDEX idx_placement_session ON lesson_placement(session_id);

-- Индекс для поиска по assignment
CREATE INDEX idx_placement_assignment ON lesson_placement(assignment_id);

-- Индекс для поиска по дате
CREATE INDEX idx_placement_date ON lesson_placement(scheduled_date);

-- Композитный индекс: сессия + дата (для быстрых выборок)
CREATE INDEX idx_placement_session_date ON lesson_placement(session_id, scheduled_date);

-- ================================================================
-- КОММЕНТАРИИ
-- ================================================================

-- schedule_session
COMMENT ON TABLE schedule_session IS 'CQRS Command Side: Сессия редактирования расписания с optimistic locking';
COMMENT ON COLUMN schedule_session.id IS 'UUID (уникальный идентификатор)';
COMMENT ON COLUMN schedule_session.name IS 'Название сессии (например: "Расписание 2025 весна")';
COMMENT ON COLUMN schedule_session.status IS 'Статус сессии (INITIALIZED, GENERATING, READY_FOR_EDIT, FINAL, ARCHIVED)';
COMMENT ON COLUMN schedule_session.created_at IS 'Время создания';
COMMENT ON COLUMN schedule_session.created_by IS 'Кто создал';
COMMENT ON COLUMN schedule_session.updated_at IS 'Время последнего обновления';
COMMENT ON COLUMN schedule_session.updated_by IS 'Кто обновил';
COMMENT ON COLUMN schedule_session.version IS 'Optimistic lock: версия (автоинкремент при UPDATE)';
COMMENT ON COLUMN schedule_session.workspace_snapshot IS 'Опциональный JSON snapshot ScheduleWorkspace';

-- lesson_placement
COMMENT ON TABLE lesson_placement IS 'CQRS Command Side: Размещение занятия (Write Model)';
COMMENT ON COLUMN lesson_placement.id IS 'UUID (уникальный идентификатор)';
COMMENT ON COLUMN lesson_placement.session_id IS 'Ссылка на сессию (FK)';
COMMENT ON COLUMN lesson_placement.assignment_id IS 'Ссылка на заявку (FK)';
COMMENT ON COLUMN lesson_placement.scheduled_date IS 'Дата занятия';
COMMENT ON COLUMN lesson_placement.scheduled_slot IS 'Временной слот (FIRST, SECOND)';
COMMENT ON COLUMN lesson_placement.created_at IS 'Время создания';
COMMENT ON COLUMN lesson_placement.created_by IS 'Кто создал';
COMMENT ON COLUMN lesson_placement.updated_at IS 'Время последнего обновления';
COMMENT ON COLUMN lesson_placement.updated_by IS 'Кто обновил';
COMMENT ON COLUMN schedule_session.version IS 'Optimistic lock: версия (автоинкремент при UPDATE)';

-- placement_auditoriums
COMMENT ON TABLE placement_auditoriums IS 'Связующая таблица: placement ↔ аудитории (Many-to-Many)';

-- ================================================================
-- ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ
-- ================================================================

-- Пример 1: Создать новую сессию
-- INSERT INTO schedule_session (id, name, status, created_at, created_by, updated_at, updated_by, version)
-- VALUES (gen_random_uuid(), 'Расписание 2025 весна', 'INITIALIZED', NOW(), 'admin', NOW(), 'admin', 0);

-- Пример 2: Создать placement
-- INSERT INTO lesson_placement (id, session_id, assignment_id, scheduled_date, scheduled_slot, created_at, created_by)
-- VALUES (gen_random_uuid(), <session_id>, <assignment_id>, '2025-01-15', 'FIRST', NOW(), 'admin');

-- Пример 3: Добавить аудитории к placement
-- INSERT INTO placement_auditoriums (placement_id, auditorium_id)
-- VALUES (<placement_id>, <auditorium_id>);

-- Пример 4: Optimistic lock (версия автоматически увеличивается)
-- UPDATE schedule_session SET status = 'READY_FOR_EDIT', updated_at = NOW(), updated_by = 'admin'
-- WHERE id = <session_id>; -- version автоматически увеличится на 1

-- Пример 5: Найти все placements в сессии
-- SELECT * FROM lesson_placement WHERE session_id = <session_id> ORDER BY scheduled_date, scheduled_slot;

-- Пример 6: Найти конфликты (одновременно в одной аудитории)
-- SELECT p1.id, p2.id, p1.scheduled_date, p1.scheduled_slot
-- FROM lesson_placement p1
-- JOIN lesson_placement p2 ON (p1.scheduled_date = p2.scheduled_date AND p1.scheduled_slot = p2.scheduled_slot)
-- JOIN placement_auditoriums pa1 ON (p1.id = pa1.placement_id)
-- JOIN placement_auditoriums pa2 ON (p2.id = pa2.placement_id)
-- WHERE pa1.auditorium_id = pa2.auditorium_id AND p1.id < p2.id;
