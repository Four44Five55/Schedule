-- liquibase formatted sql

-- changeset your_name:1
-- comment: Создание всей начальной схемы (таблицы, индексы, представления)

-- ---------------------------------
-- Таблицы
-- ---------------------------------

CREATE TABLE discipline (
                            id           SERIAL PRIMARY KEY,
                            name         VARCHAR(255),
                            abbreviation VARCHAR(255),
                            semester     INTEGER NOT NULL
);

CREATE TABLE kind_of_study (
                               enum_name         VARCHAR(255) PRIMARY KEY,
                               full_name         VARCHAR(255) NOT NULL UNIQUE,
                               abbreviation_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE theme_lesson (
                              id            SERIAL PRIMARY KEY,
                              discipline_id INTEGER NOT NULL REFERENCES discipline (id),
                              theme_number  VARCHAR(255) NOT NULL,
                              title         TEXT,
                              UNIQUE (discipline_id, theme_number)
);

CREATE TABLE curriculum_slot (
                                 id              SERIAL PRIMARY KEY,
                                 kind_of_study   VARCHAR(255) NOT NULL REFERENCES kind_of_study (enum_name),
                                 theme_lesson_id INTEGER REFERENCES theme_lesson (id),
                                 discipline_id   INTEGER NOT NULL REFERENCES discipline (id)
);

CREATE TABLE discipline_curriculum (
                                       id            SERIAL PRIMARY KEY,
                                       discipline_id INTEGER NOT NULL REFERENCES discipline (id),
                                       start_slot_id INTEGER REFERENCES curriculum_slot (id),
                                       end_slot_id   INTEGER REFERENCES curriculum_slot (id)
);

CREATE TABLE slot_chain (
                            id            SERIAL PRIMARY KEY,
                            discipline_id INTEGER NOT NULL REFERENCES discipline (id),
                            slot_a_id     INTEGER NOT NULL REFERENCES curriculum_slot (id),
                            slot_b_id     INTEGER NOT NULL REFERENCES curriculum_slot (id),
                            UNIQUE (slot_a_id, slot_b_id),
                            CHECK (slot_a_id != slot_b_id)
);

-- ---------------------------------
-- Индексы
-- ---------------------------------

CREATE INDEX idx_curriculum_slot_discipline ON curriculum_slot (discipline_id);
CREATE INDEX idx_curriculum_slot_kind ON curriculum_slot (kind_of_study);
CREATE INDEX idx_curriculum_slot_theme ON curriculum_slot (theme_lesson_id);

CREATE INDEX idx_discipline_curriculum_discipline ON discipline_curriculum (discipline_id);
CREATE INDEX idx_discipline_curriculum_start_slot ON discipline_curriculum (start_slot_id);
CREATE INDEX idx_discipline_curriculum_end_slot ON discipline_curriculum (end_slot_id);

CREATE INDEX idx_slot_chain_a ON slot_chain (slot_a_id);
CREATE INDEX idx_slot_chain_b ON slot_chain (slot_b_id);
CREATE INDEX idx_slot_chain_discipline ON slot_chain (discipline_id);

-- changeset your_name:2
-- comment: Создание представления view_counting_tupy_lessons

CREATE OR REPLACE VIEW view_counting_tupy_lessons AS
WITH study_types AS (
    SELECT 1 AS sort_order, 'LECTURE' AS enum_name, 'Лекция' AS full_name
    UNION ALL SELECT 2, 'PRACTICAL_WORK', 'Практическое занятие'
    UNION ALL SELECT 3, 'LAB_WORK', 'Лабораторная работа'
    UNION ALL SELECT 4, 'SEMINAR', 'Семинар'
    UNION ALL SELECT 5, 'GROUP_WORK', 'Групповое занятие'
    UNION ALL SELECT 6, 'GROUP_EXERCISE', 'Групповое упражнение'
    UNION ALL SELECT 7, 'QUIZ', 'Контрольная работа'
    UNION ALL SELECT 8, 'INDIVIDUAL_REVIEW_INTERVIEW', 'Индивидуальное контрольное собеседование'
    UNION ALL SELECT 9, 'CREDIT_WITH_GRADE', 'Зачет с оценкой'
    UNION ALL SELECT 10, 'CREDIT_WITHOUT_GRADE', 'Зачет без оценки'
    UNION ALL SELECT 11, 'EXAM', 'Экзамен'
    UNION ALL SELECT 12, 'INDEPENDENT_STUDY', 'Самостоятельная работа'
),
     type_counts AS (
         SELECT
             st.sort_order,
             st.full_name AS study_type_name,
             COUNT(cs.id) AS count,
             st.enum_name AS kind_of_study
         FROM
             study_types st
                 LEFT JOIN
             curriculum_slot cs ON st.enum_name = cs.kind_of_study AND cs.discipline_id = 5
         GROUP BY
             st.sort_order, st.enum_name, st.full_name
     ),
     total_count AS (
         SELECT
             999 AS sort_order,
             'ВСЕГО' AS study_type_name,
             SUM(count) AS count,
             NULL AS kind_of_study
         FROM
             type_counts
     )
SELECT
    study_type_name,
    count,
    kind_of_study
FROM
    (SELECT * FROM type_counts
     UNION ALL
     SELECT * FROM total_count) AS combined_results
ORDER BY
    sort_order;

-- changeset your_name:3
-- comment: Создание представления view_discipline_lessons

CREATE OR REPLACE VIEW view_discipline_lessons AS
SELECT
    d.abbreviation AS "Дис",
    cs.id AS cs_id,
    kos.abbreviation_name AS "Вид",
    tl.theme_number AS "№_Т",
    tl.title AS "Тема"
FROM curriculum_slot cs
         JOIN discipline d ON cs.discipline_id = d.id
         JOIN kind_of_study kos ON cs.kind_of_study = kos.enum_name
         LEFT JOIN theme_lesson tl ON cs.theme_lesson_id = tl.id
WHERE d.id = 5;