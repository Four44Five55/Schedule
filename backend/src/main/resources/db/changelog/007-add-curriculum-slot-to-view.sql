-- Добавление ссылки на слот учебного плана в read-модель расписания.
-- Нужно фронту, чтобы определять «сцепки» (SlotChain связывает CurriculumSlot),
-- то есть какие соседние занятия идут единой неразрывной цепочкой.

-- IF NOT EXISTS: колонку мог уже создать Hibernate (ddl-auto=update) из сущности —
-- делаем миграцию идемпотентной, чтобы Liquibase не падал на повторном добавлении.
ALTER TABLE schedule_view
    ADD COLUMN IF NOT EXISTS curriculum_slot_id INTEGER;

COMMENT ON COLUMN schedule_view.curriculum_slot_id IS 'ID слота учебного плана (CurriculumSlot) — для определения сцепок занятий на фронте. Заполняется при синхронизации из placement.assignment.';
