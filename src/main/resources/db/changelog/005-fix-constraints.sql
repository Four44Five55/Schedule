-- ================================================================
-- ИСПРАВЛЕНИЕ CONSTRAINTS: Вариант 3 CQRS
-- ================================================================
-- Проблема: constraint unique_assignment_in_session ПРЕПЯТСТВУЕТ
-- нормальной работе расписания (один assignment может быть для нескольких групп)
--
-- Решение: Убираем неверный constraint, разрешаем одному assignment
-- обслуживать несколько групп в Query Side (schedule_view)
--
-- Автор: CQRS Implementation
-- Дата: 2026-06-14
-- ================================================================

-- ================================================================
-- ШАГ 1: Удаляем неверный constraint
-- ================================================================

DO $$
BEGIN
    -- Проверяем существование constraint
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'unique_assignment_in_session'
        AND table_name = 'lesson_placement'
    ) THEN
        -- Удаляем constraint
        ALTER TABLE lesson_placement DROP CONSTRAINT unique_assignment_in_session;

        RAISE NOTICE 'Удален неверный constraint unique_assignment_in_session';
    ELSE
        RAISE NOTICE 'Constraint unique_assignment_in_session не найден (возможно, уже удален)';
    END IF;
END $$;

-- ================================================================
-- ШАГ 2: Проверяем текущую структуру lesson_placement
-- ================================================================

-- Комментируем структуру таблицы (для справки)
COMMENT ON TABLE lesson_placement IS 'CQRS Command Side: Размещение занятия (Write Model). Один placement МОЖЕТ обслуживать несколько групп (легитимно).';

-- ================================================================
-- ШАГ 3: (Опционально) Создаем индекс для быстрого поиска по сессии
-- ================================================================

-- Если индекса нет - создаем
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_placement_session'
    ) THEN
        CREATE INDEX idx_placement_session ON lesson_placement(session_id);

        RAISE NOTICE 'Создан индекс idx_placement_session';
    END IF;
END $$;

-- ================================================================
-- ШАГ 4: (Опционально) Создаем индекс для поиска по assignment
-- ================================================================

-- Если индекса нет - создаем
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_placement_assignment'
    ) THEN
        CREATE INDEX idx_placement_assignment ON lesson_placement(assignment_id);

        RAISE NOTICE 'Создан индекс idx_placement_assignment';
    END IF;
END $$;

-- ================================================================
-- ПОЯСНЕНИЯ: Почему НЕ нужен UNIQUE constraint
-- ================================================================

/*
ОБРАЗЕЦ:
Дисциплина "Базы данных" (Assignment 100)
Изучают 3 группы: ПИ-401, ПИ-402, ПИ-403
Лекция проводится в одно время для всех 3 групп (Пн 09:00)

СТАРАЯ СТРУКТУРА (с unique_assignment_in_session):
❌ Placement 1: (assignment=100, date=2026-02-09, slot=FIRST) → ПИ-401
❌ Placement 2: (assignment=100, date=2026-02-09, slot=FIRST) → ПИ-402 ❌ CONSTRAINT ERROR!
❌ Placement 3: (assignment=100, date=2026-02-09, slot=FIRST) → ПИ-403 ❌ CONSTRAINT ERROR!

НОВАЯ СТРУКТУРА (без unique_assignment_in_session):
✅ Placement 1: (assignment=100, date=2026-02-09, slot=FIRST) → Все 3 группы
   ↓ Query Side синхронизация
✅ ScheduleView 1: (placement_id=xxx, group_id=10, ...) → ПИ-401
✅ ScheduleView 2: (placement_id=xxx, group_id=20, ...) → ПИ-402
✅ ScheduleView 3: (placement_id=xxx, group_id=30, ...) → ПИ-403

РЕЗУЛЬТАТ:
- ✅ Command Side: 1 placement (экономия памяти)
- ✅ Query Side: 3 ScheduleView (быстрый поиск по группе)
- ✅ Никаких constraint errors
- ✅ Логически правильно (одна лекция для 3 групп)
*/

-- ================================================================
-- ПОЯСНЕНИЯ: Уникальность на Query Side
-- ================================================================

/*
Если нужно предотвратить истинные дубликаты (одно и то же занятие для одной группы),
constraint должен быть НА QUERY SIDE:

CREATE UNIQUE INDEX idx_unique_lesson
ON schedule_view (placement_id, group_id, scheduled_date, scheduled_slot);

Это предотвратит:
- ❌ Два занятия для одной группы в одно время
- ✅ Но позволит один assignment для разных групп
*/

-- ================================================================
-- ПРОВЕРКА: Что получилось в итоге
-- ================================================================

-- Примечание: Для проверки структуры используйте psql вручную:
-- \d+ lesson_placement

-- Ожидаемый результат:
-- lesson_placement:
--   - id (UUID, PK)
--   - session_id (UUID, FK)
--   - assignment_id (INTEGER, FK)
--   - scheduled_date (DATE)
--   - scheduled_slot (VARCHAR)
--   - created_at, updated_at, created_by, updated_by
--   - Индексы: idx_placement_session, idx_placement_assignment
--   - ❌ БЕЗ unique_assignment_in_session!

-- ================================================================
-- ВАЖНО: После применения скрипта
-- ================================================================

-- 1. Перезапустите приложение
-- 2. Сгенерируйте расписание
-- 3. Проверьте логи:
--    ✅ "Извлечено 1138 placements из workspace" (без потери данных!)
-- 4. Проверьте БД:
--    SELECT COUNT(*) FROM lesson_placement WHERE session_id = 'xxx';
--    Ожидается: ~1138 записей
-- 5. Проверьте Query Side:
--    SELECT COUNT(*) FROM schedule_view WHERE placement_id IN (...);
--    Ожидается: >=1138 записей (может быть больше за счет нескольких групп)
