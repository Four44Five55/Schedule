-- Добавление поля compact_schedule в таблицу educator
-- Позволяет включать режим компактности расписания для конкретного преподавателя

ALTER TABLE educator
    ADD COLUMN compact_schedule BOOLEAN NOT NULL DEFAULT FALSE;

-- Добавление комментария для поля
COMMENT ON COLUMN educator.compact_schedule IS 'Флаг компактности расписания: если true, занятия для разных групп стараются размещать в минимальное количество дней';
