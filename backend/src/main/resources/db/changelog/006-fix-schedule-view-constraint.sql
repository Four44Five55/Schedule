-- ================================================================
-- ИСПРАВЛЕНИЕ CONSTRAINT: schedule_view для мульти-групповой поддержки
-- ================================================================
-- Проблема: constraint schedule_view_placement_id_key ПРЕПЯТСТВУЕТ
-- созданию нескольких ScheduleView для одного placement (для разных групп)
--
-- Решение: Заменяем UNIQUE(placement_id) на UNIQUE(placement_id, study_stream_id)
--
-- Автор: CQRS Implementation
-- Дата: 2026-06-14
-- ================================================================

-- ================================================================
-- ШАГ 1: Удаляем старый constraint
-- ================================================================

DO $$
BEGIN
    -- Проверяем существование constraint
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'schedule_view_placement_id_key'
        AND table_name = 'schedule_view'
    ) THEN
        -- Удаляем constraint
        ALTER TABLE schedule_view DROP CONSTRAINT schedule_view_placement_id_key;

        RAISE NOTICE 'Удален constraint schedule_view_placement_id_key';
    ELSE
        RAISE NOTICE 'Constraint schedule_view_placement_id_key не найден (возможно, уже удален)';
    END IF;
END $$;

-- ================================================================
-- ШАГ 2: Создаем новый составной constraint
-- ================================================================

DO $$
BEGIN
    -- Проверяем, что constraint еще не существует
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'schedule_view_placement_group_unique'
        AND table_name = 'schedule_view'
    ) THEN
        -- Создаем составной уникальный constraint
        ALTER TABLE schedule_view
        ADD CONSTRAINT schedule_view_placement_group_unique
        UNIQUE (placement_id, study_stream_id);

        RAISE NOTICE 'Создан составной unique constraint (placement_id, study_stream_id)';
    ELSE
        RAISE NOTICE 'Constraint schedule_view_placement_group_unique уже существует';
    END IF;
END $$;

-- ================================================================
-- ПОЯСНЕНИЯ: Новая структура
-- ================================================================

/*
СТАРАЯ СТРУКТУРА (с UNIQUE(placement_id)):
❌ Placement 1 (id=xxx) для Лекции БД
   ❌ ScheduleView 1: (placement_id=xxx, group_id=10, ...) → ПИ-401 ✅
   ❌ ScheduleView 2: (placement_id=xxx, group_id=20, ...) → ПИ-402 ❌ CONSTRAINT ERROR!
   ❌ ScheduleView 3: (placement_id=xxx, group_id=30, ...) → ПИ-403 ❌ CONSTRAINT ERROR!

НОВАЯ СТРУКТУРА (с UNIQUE(placement_id, study_stream_id)):
✅ Placement 1 (id=xxx) для Лекции БД (все 3 группы)
   ✅ ScheduleView 1: (placement_id=xxx, group_id=10, ...) → ПИ-401 ✅
   ✅ ScheduleView 2: (placement_id=xxx, group_id=20, ...) → ПИ-402 ✅
   ✅ ScheduleView 3: (placement_id=xxx, group_id=30, ...) → ПИ-403 ✅

РЕЗУЛЬТАТ:
- ✅ Один placement для нескольких групп
- ✅ Несколько ScheduleView (по одной на группу)
- ✅ Уникальность: (placement_id + study_stream_id) - одна запись на группу
- ✅ Никаких constraint errors
*/

-- ================================================================
-- ПРОВЕРКА: Что получилось в итоге
-- ================================================================

-- Примечание: Для проверки структуры используйте psql вручную:
-- \d+ schedule_view

-- Ожидаемый результат:
-- schedule_view:
--   - id (UUID, PK)
--   - placement_id (UUID) - часть составного unique constraint
--   - study_stream_id (INTEGER) - часть составного unique constraint
--   - ✅ Constraint: schedule_view_placement_group_unique (placement_id, study_stream_id)
--   - ❌ БЕЗ schedule_view_placement_id_key!

-- ================================================================
-- ВАЖНО: После применения скрипта
-- ================================================================

-- 1. Перезапустите приложение
-- 2. Сгенерируйте расписание
-- 3. Проверьте логи:
--    ✅ "Извлечено N placements из workspace"
--    ✅ "Синхронизация завершена: M view records"
-- 4. Проверьте БД:
--    SELECT COUNT(*) FROM schedule_view;
--    Ожидается: >= N записей (N placements * количество групп)
