--liquibase formatted sql

--changeset four4five5:1_create_tables
--comment: Создание начальной структуры всех таблиц и их связей

-- =======================================================================
-- ОСНОВНЫЕ СПРАВОЧНИКИ (не зависят от других сущностей)
-- =======================================================================

-- Справочник дисциплин. Хранит общую информацию о дисциплине, не привязанную к семестру.
CREATE TABLE discipline (
                            id           SERIAL PRIMARY KEY,
                            name         VARCHAR(255) NOT NULL UNIQUE,
                            abbreviation VARCHAR(255)
);

-- Справочник локаций (территорий, кампусов).
CREATE TABLE location (
                          id      SERIAL PRIMARY KEY,
                          name    VARCHAR(255) NOT NULL UNIQUE,
                          address TEXT
);

-- Справочник учебных корпусов. Каждый корпус принадлежит одной локации.
CREATE TABLE building (
                          id          SERIAL PRIMARY KEY,
                          name        VARCHAR(255) NOT NULL,
                          location_id INTEGER      NOT NULL REFERENCES location(id) ON DELETE RESTRICT,
                          UNIQUE (location_id, name)
);

-- Справочник аудиторий. Каждая аудитория принадлежит одному корпусу.
CREATE TABLE auditorium (
                            id          SERIAL PRIMARY KEY,
                            name        VARCHAR(255) NOT NULL,
                            capacity    INTEGER      NOT NULL CHECK (capacity > 0),
                            building_id INTEGER      NOT NULL REFERENCES building(id) ON DELETE CASCADE,
                            UNIQUE (building_id, name)
);

-- Справочник "пулов" или групп аудиторий (например, "Лекционные", "Компьютерные классы").
CREATE TABLE auditorium_pool (
                                 id          SERIAL PRIMARY KEY,
                                 name        VARCHAR(255) NOT NULL UNIQUE,
                                 description TEXT
);

-- Справочник учебных групп. Содержит ссылку на "домашнюю" аудиторию.
CREATE TABLE groups (
                        id                 SERIAL PRIMARY KEY,
                        name               VARCHAR(255) NOT NULL UNIQUE,
                        size               INTEGER      NOT NULL CHECK (size > 0),
                        base_auditorium_id INTEGER      REFERENCES auditorium (id) ON DELETE SET NULL
);

-- Справочник преподавателей.
CREATE TABLE educator (
                          id   SERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL
);

-- Справочник видов занятий (лекция, практика и т.д.).
CREATE TABLE kind_of_study (
                               enum_name         VARCHAR(255) PRIMARY KEY,
                               full_name         VARCHAR(255) NOT NULL UNIQUE,
                               abbreviation_name VARCHAR(255) NOT NULL UNIQUE
);

-- Справочник учебных потоков и подгрупп.
CREATE TABLE study_stream (
                              id   SERIAL PRIMARY KEY,
                              name VARCHAR(255) NOT NULL UNIQUE,
                              semester INTEGER      NOT NULL
);

-- Справочник учебных периодов (семестров).
CREATE TABLE study_period (
                              id          SERIAL PRIMARY KEY,
                              name        VARCHAR(255) NOT NULL,
                              study_year  INTEGER      NOT NULL,
                              period_type VARCHAR(50)  NOT NULL,
                              start_date  DATE         NOT NULL,
                              end_date    DATE         NOT NULL,
                              UNIQUE (study_year, period_type)
);

-- =======================================================================
-- СВЯЗУЮЩИЕ ТАБЛИЦЫ И ТАБЛИЦЫ ДАННЫХ
-- =======================================================================

-- Связь "многие-ко-многим": какие аудитории в какой пул входят.
CREATE TABLE auditorium_pool_mapping (
                                         pool_id       INTEGER NOT NULL REFERENCES auditorium_pool(id) ON DELETE CASCADE,
                                         auditorium_id INTEGER NOT NULL REFERENCES auditorium(id) ON DELETE CASCADE,
                                         PRIMARY KEY (pool_id, auditorium_id)
);

-- Связь "многие-ко-многим": какие группы в какой поток входят.
CREATE TABLE stream_groups (
                               stream_id INTEGER NOT NULL REFERENCES study_stream(id) ON DELETE CASCADE,
                               group_id  INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                               PRIMARY KEY (stream_id, group_id)
);

-- Таблица предпочтений преподавателей по дням недели.
CREATE TABLE educator_day_priority (
                                       educator_id INTEGER      NOT NULL REFERENCES educator(id) ON DELETE CASCADE,
                                       day_of_week VARCHAR(255) NOT NULL,
                                       PRIMARY KEY (educator_id, day_of_week)
);

-- Таблица предпочтений преподавателей по парам.
CREATE TABLE educator_slot_priority (
                                        educator_id INTEGER      NOT NULL REFERENCES educator(id) ON DELETE CASCADE,
                                        time_slot   VARCHAR(255) NOT NULL,
                                        PRIMARY KEY (educator_id, time_slot)
);

-- "Курс" - реализация дисциплины в конкретном семестре.
CREATE TABLE discipline_course (
                                   id            SERIAL PRIMARY KEY,
                                   discipline_id INTEGER NOT NULL REFERENCES discipline(id) ON DELETE CASCADE,
                                   study_period_id INTEGER NOT NULL REFERENCES study_period(id) ON DELETE RESTRICT,
                                   UNIQUE (discipline_id, study_period_id)
);

-- Темы занятий, привязанные к "глобальной" дисциплине.
CREATE TABLE theme_lesson (
                              id            SERIAL PRIMARY KEY,
                              discipline_id INTEGER      NOT NULL REFERENCES discipline(id) ON DELETE CASCADE,
                              theme_number  VARCHAR(255) NOT NULL,
                              title         TEXT,
                              UNIQUE (discipline_id, theme_number)
);

-- "Академическая" часть учебного плана: что, в каком порядке и с какими требованиями к месту.
CREATE TABLE curriculum_slot (
                                 id                     SERIAL PRIMARY KEY,
                                 discipline_course_id   INTEGER      NOT NULL REFERENCES discipline_course(id) ON DELETE CASCADE,
                                 position               INTEGER      NOT NULL,
                                 kind_of_study          VARCHAR(255) NOT NULL REFERENCES kind_of_study(enum_name),
                                 theme_lesson_id        INTEGER REFERENCES theme_lesson(id),
                                 required_auditorium_id INTEGER REFERENCES auditorium(id),
                                 priority_auditorium_id INTEGER REFERENCES auditorium(id),
                                 allowed_pool_id        INTEGER REFERENCES auditorium_pool(id),
                                 UNIQUE (discipline_course_id, position)
);

-- "Организационная" часть плана: кто и с кем проводит занятие из учебного плана.
CREATE TABLE assignment (
                            id                 SERIAL PRIMARY KEY,
                            curriculum_slot_id INTEGER NOT NULL REFERENCES curriculum_slot(id) ON DELETE CASCADE,
                            study_stream_id    INTEGER NOT NULL REFERENCES study_stream(id) ON DELETE RESTRICT,
                            UNIQUE (curriculum_slot_id, study_stream_id)
);

-- Связь "многие-ко-многим": какие преподаватели назначены на конкретное "назначение".
CREATE TABLE assignment_educators (
                                      assignment_id INTEGER NOT NULL REFERENCES assignment(id) ON DELETE CASCADE,
                                      educator_id   INTEGER NOT NULL REFERENCES educator(id) ON DELETE RESTRICT,
                                      PRIMARY KEY (assignment_id, educator_id)
);

-- "Сцепки" неразрывных занятий.
CREATE TABLE slot_chain (
                            id        SERIAL PRIMARY KEY,
                            slot_a_id INTEGER NOT NULL REFERENCES curriculum_slot(id) ON DELETE CASCADE,
                            slot_b_id INTEGER NOT NULL REFERENCES curriculum_slot(id) ON DELETE CASCADE,
                            UNIQUE (slot_a_id, slot_b_id),
                            CHECK (slot_a_id != slot_b_id)
);

-- Таблицы постоянных ограничений для ресурсов.
CREATE TABLE educator_constraint (
                                     id                 SERIAL PRIMARY KEY,
                                     educator_id        INTEGER      NOT NULL REFERENCES educator(id) ON DELETE CASCADE,
                                     kind_of_constraint VARCHAR(255) NOT NULL,
                                     start_date         DATE         NOT NULL,
                                     end_date           DATE         NOT NULL,
                                     description        TEXT,
                                     CHECK (start_date <= end_date)
);

CREATE TABLE group_constraint (
                                  id                 SERIAL PRIMARY KEY,
                                  group_id           INTEGER      NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                                  kind_of_constraint VARCHAR(255) NOT NULL,
                                  start_date         DATE         NOT NULL,
                                  end_date           DATE         NOT NULL,
                                  description        TEXT,
                                  CHECK (start_date <= end_date)
);

CREATE TABLE auditorium_constraint (
                                       id                 SERIAL PRIMARY KEY,
                                       auditorium_id      INTEGER      NOT NULL REFERENCES auditorium(id) ON DELETE CASCADE,
                                       kind_of_constraint VARCHAR(255) NOT NULL,
                                       start_date         DATE         NOT NULL,
                                       end_date           DATE         NOT NULL,
                                       description        TEXT,
                                       CHECK (start_date <= end_date)
);

--changeset four4five5:2_create_indexes
--comment: Создание индексов для ускорения выборок по внешним ключам

CREATE INDEX idx_building_location_id ON building(location_id);
CREATE INDEX idx_auditorium_building_id ON auditorium(building_id);
CREATE INDEX idx_discipline_course_discipline_id ON discipline_course(discipline_id);
CREATE INDEX idx_theme_lesson_discipline_id ON theme_lesson(discipline_id);
CREATE INDEX idx_curriculum_slot_course_id ON curriculum_slot(discipline_course_id);
CREATE INDEX idx_assignment_slot_id ON assignment(curriculum_slot_id);
CREATE INDEX idx_assignment_stream_id ON assignment(study_stream_id);
CREATE INDEX idx_educator_constraint_educator_id ON educator_constraint(educator_id);
CREATE INDEX idx_group_constraint_group_id ON group_constraint(group_id);
CREATE INDEX idx_auditorium_constraint_auditorium_id ON auditorium_constraint(auditorium_id);
CREATE INDEX idx_slot_chain_a ON slot_chain (slot_a_id);
CREATE INDEX idx_slot_chain_b ON slot_chain (slot_b_id);

--changeset four4five5:3_create_views
--comment: Создание представлений для аналитики и отчетов (адаптированных)

CREATE OR REPLACE VIEW view_counting_tupy_lessons AS
WITH study_types AS (SELECT 1 AS sort_order, 'LECTURE' AS enum_name, 'Лекция' AS full_name
                     UNION ALL
                     SELECT 2, 'PRACTICAL_WORK', 'Практическое занятие'
                     UNION ALL
                     SELECT 3, 'LAB_WORK', 'Лабораторная работа'
                     UNION ALL
                     SELECT 4, 'SEMINAR', 'Семинар'
                     UNION ALL
                     SELECT 5, 'GROUP_WORK', 'Групповое занятие'
                     UNION ALL
                     SELECT 6, 'GROUP_EXERCISE', 'Групповое упражнение'
                     UNION ALL
                     SELECT 7, 'QUIZ', 'Контрольная работа'
                     UNION ALL
                     SELECT 8, 'INDIVIDUAL_REVIEW_INTERVIEW', 'Индивидуальное контрольное собеседование'
                     UNION ALL
                     SELECT 9, 'CREDIT_WITH_GRADE', 'Зачет с оценкой'
                     UNION ALL
                     SELECT 10, 'CREDIT_WITHOUT_GRADE', 'Зачет без оценки'
                     UNION ALL
                     SELECT 11, 'EXAM', 'Экзамен'
                     UNION ALL
                     SELECT 12, 'INDEPENDENT_STUDY', 'Самостоятельная работа'),
     type_counts AS (SELECT st.sort_order,
                            st.full_name AS study_type_name,
                            COUNT(cs.id) AS count,
                            st.enum_name AS kind_of_study
                     FROM study_types st
                              LEFT JOIN
                          curriculum_slot cs
                          ON st.enum_name = cs.kind_of_study AND cs.discipline_course_id = 5 -- id=5 захардкожен
                     GROUP BY st.sort_order, st.enum_name, st.full_name),
     total_count AS (SELECT 999 AS sort_order,
                            'ВСЕГО' AS study_type_name,
                            SUM(count) AS count,
                            NULL AS kind_of_study
                     FROM type_counts)
SELECT study_type_name,
       count,
       kind_of_study
FROM (SELECT *
      FROM type_counts
      UNION ALL
      SELECT *
      FROM total_count) AS combined_results
ORDER BY sort_order;

-- changeset four4five5:3
-- comment: Создание представления для уроков дисциплины
CREATE OR REPLACE VIEW view_discipline_lessons AS
SELECT d.abbreviation AS "Дис",
       cs.id AS cs_id,
       kos.abbreviation_name AS "Вид",
       tl.theme_number AS "№_Т",
       tl.title AS "Тема"
FROM curriculum_slot cs
         JOIN discipline d ON cs.discipline_course_id = d.id
         JOIN kind_of_study kos ON cs.kind_of_study = kos.enum_name
         LEFT JOIN theme_lesson tl ON cs.theme_lesson_id = tl.id
WHERE d.id = 5; -- id=5 захардкожен