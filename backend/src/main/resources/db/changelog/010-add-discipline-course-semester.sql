--liquibase formatted sql

--changeset four4five5:10_add_discipline_course_semester
--comment: Порядковый семестр на discipline_course. Одна дисциплина в одном периоде
--          может читаться разным курсам на разных семестрах с разным планом, поэтому
--          уникальность расширяется до (discipline_id, study_period_id, semester).

-- IF NOT EXISTS: колонку мог уже создать Hibernate (ddl-auto=update) из сущности —
-- делаем миграцию идемпотентной.
ALTER TABLE discipline_course ADD COLUMN IF NOT EXISTS semester INTEGER;

-- Бэкофилл существующих строк (на случай, если колонку добавил Hibernate без значения)
UPDATE discipline_course SET semester = 1 WHERE semester IS NULL;

ALTER TABLE discipline_course ALTER COLUMN semester SET DEFAULT 1;
ALTER TABLE discipline_course ALTER COLUMN semester SET NOT NULL;

COMMENT ON COLUMN discipline_course.semester IS 'Порядковый семестр программы (1..12), на котором изучается дисциплина. Не путать со study_period (календарный период с датами).';

-- Старый уникальный ключ (discipline_id, study_period_id) блокирует один и тот же
-- предмет на разных семестрах в одном периоде — снимаем его (имя по умолчанию PostgreSQL).
ALTER TABLE discipline_course DROP CONSTRAINT IF EXISTS discipline_course_discipline_id_study_period_id_key;

-- Новый составной уникальный ключ с учётом семестра (drop-if-exists + add — идемпотентно).
ALTER TABLE discipline_course DROP CONSTRAINT IF EXISTS uq_discipline_course_discipline_period_semester;
ALTER TABLE discipline_course ADD CONSTRAINT uq_discipline_course_discipline_period_semester UNIQUE (discipline_id, study_period_id, semester);
