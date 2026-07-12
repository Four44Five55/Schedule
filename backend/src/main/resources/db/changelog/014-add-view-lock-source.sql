-- ================================================================
-- Фича 2 (пины): проекция locked/source в read-модель schedule_view
-- ================================================================
-- Денормализуем признак закрепления и происхождение из lesson_placement
-- в read-модель, чтобы фронт мог рисовать «замок» без обращения к Command Side.
-- Заполняется при синхронизации (ScheduleSynchronizer) из placement.
--
-- IF NOT EXISTS — идемпотентность (колонку мог создать Hibernate в прошлом;
-- сейчас ddl-auto=none, но оставляем для безопасности повторного прогона).
-- ================================================================

ALTER TABLE schedule_view
    ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE schedule_view
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'GENERATED';

COMMENT ON COLUMN schedule_view.locked IS 'Пин: занятие закреплено вручную (денормализовано из lesson_placement.locked) — для индикатора замка на фронте';
COMMENT ON COLUMN schedule_view.source IS 'Происхождение: GENERATED | MANUAL (денормализовано из lesson_placement.source)';
