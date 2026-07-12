--liquibase formatted sql

--changeset four4five5:11_curriculum_slot_position_deferrable
--comment: UNIQUE (discipline_course_id, position) на curriculum_slot делаем DEFERRABLE
--         INITIALLY DEFERRED. Иначе вставка слота в середину плана падает: сервис
--         сдвигает последующие слоты bulk-апдейтом (position = position + 1), а Postgres
--         проверяет уникальность построчно — сдвигая 2→3 при существующей 3, ловит
--         конфликт. Отложенная проверка (на коммите) видит уже согласованные позиции.
--         Уникальность сохраняется — меняется только момент проверки.

-- Имя по умолчанию из inline UNIQUE в 001-initial-schema (PostgreSQL) + наше имя — drop-if-exists.
ALTER TABLE curriculum_slot DROP CONSTRAINT IF EXISTS curriculum_slot_discipline_course_id_position_key;
ALTER TABLE curriculum_slot DROP CONSTRAINT IF EXISTS uq_curriculum_slot_course_position;
ALTER TABLE curriculum_slot ADD CONSTRAINT uq_curriculum_slot_course_position
    UNIQUE (discipline_course_id, position) DEFERRABLE INITIALLY DEFERRED;
