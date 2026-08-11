-- ================================================================
-- Наполнение справочника видов занятий (kind_of_study)
-- ================================================================
-- ИДЕМПОТЕНТНОСТЬ. На существующих базах справочник уже заполнен демо-скриптом, и миграция
-- обязана пройти вхолостую. Проверяем не только enum_name (PK), но и full_name /
-- abbreviation_name: на них UNIQUE, и совпадение по любому из трёх означает, что вид уже
-- заведён — вставлять нечего.
-- ================================================================

INSERT INTO kind_of_study (enum_name, full_name, abbreviation_name)
SELECT v.enum_name, v.full_name, v.abbreviation_name
FROM (VALUES ('LECTURE', 'Лекция', 'Л'),
             ('PRACTICAL_WORK', 'Практическое занятие', 'ПЗ'),
             ('LAB_WORK', 'Лабораторная работа', 'ЛР'),
             ('SEMINAR', 'Семинар', 'С'),
             ('GROUP_WORK', 'Групповое занятие', 'ГЗ'),
             ('GROUP_EXERCISE', 'Групповое упражнение', 'ГУ'),
             ('QUIZ', 'Контрольная работа', 'КР'),
             ('INDIVIDUAL_REVIEW_INTERVIEW', 'Индивидуальное контрольное собеседование', 'ИКС'),
             ('CREDIT_WITH_GRADE', 'Зачет с оценкой', 'ЗО'),
             ('CREDIT_WITHOUT_GRADE', 'Зачет без оценки', 'ЗЧ'),
             ('EXAM', 'Экзамен', 'ЭКЗ'),
             ('INDEPENDENT_STUDY', 'Самостоятельная работа', 'СР')) AS v(enum_name, full_name, abbreviation_name)
WHERE NOT EXISTS (SELECT 1
                  FROM kind_of_study k
                  WHERE k.enum_name = v.enum_name
                     OR k.full_name = v.full_name
                     OR k.abbreviation_name = v.abbreviation_name);
