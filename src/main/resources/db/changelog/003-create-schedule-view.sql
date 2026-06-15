-- ================================================================
-- CQRS Query Side: Создание таблицы schedule_view
-- ================================================================
-- Read-optimized view для расписания.
-- Денормализованные данные для быстрых SELECT запросов без JOIN.
-- Индексы на основных полях для мгновенного доступа.
--
-- Автор: CQRS Implementation
-- Дата: 2025-01-11
-- ================================================================

-- Создание таблицы schedule_view
CREATE TABLE schedule_view (
    -- Первичный ключ (UUID)
    id UUID PRIMARY KEY,

    -- Индексированные поля для быстрых запросов
    scheduled_date DATE NOT NULL,
    educator_id INTEGER,
    study_stream_id INTEGER,
    auditorium_id INTEGER,

    -- Денормализованные данные (избегаем JOIN)
    discipline_name VARCHAR(255),
    discipline_abbr VARCHAR(50),
    educator_name VARCHAR(255),
    group_name VARCHAR(255),
    kind_of_study VARCHAR(50),
    time_slot VARCHAR(50) NOT NULL,
    auditorium_name VARCHAR(255),
    theme_number VARCHAR(50),
    theme_title VARCHAR(255),

    -- Метаданные для синхронизации с Command Side
    placement_id UUID UNIQUE,
    last_updated TIMESTAMP NOT NULL
);

-- ================================================================
-- ИНДЕКСЫ для быстрых запросов
-- ================================================================

-- Индекс для запросов по дате (студенты: расписание на неделю)
CREATE INDEX idx_view_date ON schedule_view(scheduled_date);

-- Индекс для запросов по преподавателю (преподаватели: моё расписание)
CREATE INDEX idx_view_educator ON schedule_view(educator_id);

-- Индекс для запросов по группе (студенты: расписание группы)
CREATE INDEX idx_view_group ON schedule_view(study_stream_id);

-- Индекс для запросов по аудитории (проверка свободности)
CREATE INDEX idx_view_auditorium ON schedule_view(auditorium_id);

-- Индекс для синхронизации с Command Side
CREATE INDEX idx_view_placement ON schedule_view(placement_id);

-- ================================================================
-- КОММЕНТАРИИ
-- ================================================================

COMMENT ON TABLE schedule_view IS 'CQRS Query Side: Read-optimized view for schedule (denormalized data)';
COMMENT ON COLUMN schedule_view.id IS 'UUID (совпадает с placement_id из Command Side)';
COMMENT ON COLUMN schedule_view.scheduled_date IS 'Дата занятия (индексирована)';
COMMENT ON COLUMN schedule_view.educator_id IS 'ID преподавателя (индексирован)';
COMMENT ON COLUMN schedule_view.study_stream_id IS 'ID потока/подгруппы (индексирован)';
COMMENT ON COLUMN schedule_view.auditorium_id IS 'ID аудитории (индексирован)';
COMMENT ON COLUMN schedule_view.discipline_name IS 'Денормализовано: название дисциплизы';
COMMENT ON COLUMN schedule_view.discipline_abbr IS 'Денормализовано: аббревиатура дисциплизы';
COMMENT ON COLUMN schedule_view.educator_name IS 'Денормализовано: имя преподавателя';
COMMENT ON COLUMN schedule_view.group_name IS 'Денормализовано: название группы';
COMMENT ON COLUMN schedule_view.kind_of_study IS 'Денормализовано: тип занятия (LECTURE, PRACTICE, etc.)';
COMMENT ON COLUMN schedule_view.time_slot IS 'Временной слот (FIRST, SECOND)';
COMMENT ON COLUMN schedule_view.auditorium_name IS 'Денормализовано: название аудитории';
COMMENT ON COLUMN schedule_view.theme_number IS 'Денормализовано: номер темы';
COMMENT ON COLUMN schedule_view.theme_title IS 'Денормализовано: название темы';
COMMENT ON COLUMN schedule_view.placement_id IS 'Ссылка на LessonPlacement (Command Side)';
COMMENT ON COLUMN schedule_view.last_updated IS 'Время последнего обновления (для синхронизации)';

-- ================================================================
-- ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ
-- ================================================================

-- Пример 1: Расписание для студента (группы) на неделю
-- SELECT * FROM schedule_view
-- WHERE study_stream_id = 123
-- AND scheduled_date BETWEEN '2025-01-11' AND '2025-01-17'
-- ORDER BY scheduled_date, time_slot;

-- Пример 2: Расписание для преподавателя на завтра
-- SELECT * FROM schedule_view
-- WHERE educator_id = 456
-- AND scheduled_date = '2025-01-12'
-- ORDER BY time_slot;

-- Пример 3: Проверить свободность аудитории
-- SELECT COUNT(*) FROM schedule_view
-- WHERE auditorium_id = 789
-- AND scheduled_date = '2025-01-12'
-- AND time_slot = 'FIRST';

-- Пример 4: Статистика загруженности аудиторий
-- SELECT auditorium_id, COUNT(*), scheduled_date
-- FROM schedule_view
-- GROUP BY auditorium_id, scheduled_date
-- ORDER BY scheduled_date, COUNT(*) DESC;
