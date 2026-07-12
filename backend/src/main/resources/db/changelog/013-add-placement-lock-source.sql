-- ================================================================
-- Фича 2 (ручное размещение / пины): флаг закрепления + источник
-- ================================================================
-- Добавляет на lesson_placement:
--   locked  — «не трогать при (ре)генерации» (пин);
--   source  — происхождение (GENERATED | MANUAL).
--
-- Обратносовместимо: дефолты заполняют существующие строки как
-- незакреплённые сгенерированные размещения.
-- ================================================================

ALTER TABLE lesson_placement
    ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE lesson_placement
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'GENERATED';

-- Частичный индекс под выборку пинов сессии (посев Фазы 0 / регенерация
-- «вокруг замков»): фильтр идёт по WHERE locked, индексируем только пины.
CREATE INDEX idx_placement_locked ON lesson_placement(session_id) WHERE locked;

COMMENT ON COLUMN lesson_placement.locked IS 'Пин: размещение закреплено вручную, распределитель его не двигает и не удаляет';
COMMENT ON COLUMN lesson_placement.source IS 'Происхождение размещения: GENERATED (алгоритм) | MANUAL (диспетчер)';
