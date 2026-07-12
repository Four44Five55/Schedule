--liquibase formatted sql

--changeset four4five5:12_constraint_time_slot
--comment: Пер-парные ограничения. Добавляем nullable time_slot в три таблицы ограничений.
--         NULL = ограничение на весь день (все пары) — обратносовместимо со старыми строками.
--         Заданная пара (FIRST..FOURTH) = ограничение только на неё. Доменное ядро
--         (ConstraintData/SchedulableResource) уже работает пер-ячейка (день, пара);
--         меняется лишь разворот диапазона в loadAllConstraints с учётом time_slot.

ALTER TABLE educator_constraint   ADD COLUMN time_slot VARCHAR(50);
ALTER TABLE group_constraint      ADD COLUMN time_slot VARCHAR(50);
ALTER TABLE auditorium_constraint ADD COLUMN time_slot VARCHAR(50);

COMMENT ON COLUMN educator_constraint.time_slot   IS 'Пара ограничения (FIRST..FOURTH); NULL = весь день';
COMMENT ON COLUMN group_constraint.time_slot      IS 'Пара ограничения (FIRST..FOURTH); NULL = весь день';
COMMENT ON COLUMN auditorium_constraint.time_slot IS 'Пара ограничения (FIRST..FOURTH); NULL = весь день';
