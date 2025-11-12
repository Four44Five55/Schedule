-- liquibase formatted sql

-- changeset four4five5:1
-- comment: Создание начальной структуры таблиц и индексов

CREATE TABLE discipline (
                            id           INTEGER PRIMARY KEY,
                            name         VARCHAR(255),
                            abbreviation VARCHAR(255)
);

CREATE TABLE kind_of_study (
                               enum_name         VARCHAR(255) PRIMARY KEY,
                               full_name         VARCHAR(255) NOT NULL UNIQUE,
                               abbreviation_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE discipline_course (
                                   id            INTEGER PRIMARY KEY,
                                   discipline_id BIGINT  NOT NULL REFERENCES discipline (id) ON DELETE CASCADE,
                                   semester      INTEGER NOT NULL,
                                   UNIQUE (discipline_id, semester)
);

CREATE TABLE theme_lesson (
                              id            INTEGER PRIMARY KEY,
                              discipline_id BIGINT NOT NULL REFERENCES discipline (id) ON DELETE CASCADE,
                              theme_number  VARCHAR(255) NOT NULL,
                              title         TEXT,
                              UNIQUE (discipline_id, theme_number)
);

CREATE TABLE curriculum_slot (
                                 id                   INTEGER PRIMARY KEY,
                                 discipline_course_id BIGINT NOT NULL REFERENCES discipline_course (id) ON DELETE CASCADE,
                                 position             INTEGER NOT NULL,
                                 kind_of_study        VARCHAR(255) NOT NULL REFERENCES kind_of_study (enum_name),
                                 theme_lesson_id      BIGINT REFERENCES theme_lesson (id),
                                 UNIQUE (discipline_course_id, position)
);

CREATE TABLE slot_chain (
                            id        INTEGER PRIMARY KEY,
                            slot_a_id BIGINT NOT NULL REFERENCES curriculum_slot (id) ON DELETE CASCADE,
                            slot_b_id BIGINT NOT NULL REFERENCES curriculum_slot (id) ON DELETE CASCADE,
                            UNIQUE (slot_a_id, slot_b_id),
                            CHECK (slot_a_id != slot_b_id)
);

CREATE INDEX idx_discipline_course_discipline_id ON discipline_course(discipline_id);
CREATE INDEX idx_theme_lesson_discipline_id ON theme_lesson(discipline_id);
CREATE INDEX idx_curriculum_slot_course_id ON curriculum_slot(discipline_course_id);
CREATE INDEX idx_slot_chain_a ON slot_chain (slot_a_id);
CREATE INDEX idx_slot_chain_b ON slot_chain (slot_b_id);

-- changeset four4five5:2
-- comment: Создание представления для подсчета занятий
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
             curriculum_slot cs ON st.enum_name = cs.kind_of_study AND cs.discipline_course_id = 5 -- Учтите, что id=5 захардкожен
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


-- changeset four4five5:3
-- comment: Создание представления для уроков дисциплины
CREATE OR REPLACE VIEW view_discipline_lessons AS
SELECT
    d.abbreviation AS "Дис",
    cs.id AS cs_id,
    kos.abbreviation_name AS "Вид",
    tl.theme_number AS "№_Т",
    tl.title AS "Тема"
FROM curriculum_slot cs
         JOIN discipline d ON cs.discipline_course_id = d.id
         JOIN kind_of_study kos ON cs.kind_of_study = kos.enum_name
         LEFT JOIN theme_lesson tl ON cs.theme_lesson_id = tl.id
WHERE d.id = 5; -- Учтите, что id=5 захардкожен