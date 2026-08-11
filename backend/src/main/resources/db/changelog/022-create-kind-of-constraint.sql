-- ================================================================
-- Виды ограничений: из Java-enum в пользовательский справочник
-- ================================================================
-- ЗАЧЕМ. Вид ограничения (командировка, отпуск, наряд, учения…) — метка без поведения: код нигде
-- по нему не ветвится, вид определяет только подпись и цвет в сетке. Такой перечень принадлежит
-- пользователю: «нужен свой вид» не должно означать «нужен релиз». Ср. с видами занятий, которые
-- обязаны остаться в коде — там от вида зависит распределение и порядок изучения (см. CLAUDE.md).
--
-- КЛЮЧ — СТРОКОВЫЙ, А НЕ SERIAL. В трёх таблицах ограничений уже лежат строки ('BUSINESS_TRIP',
-- 'VACATION', …) в колонке kind_of_constraint. Числовой id потребовал бы переписать эти данные,
-- все запросы и фронт; строковый code позволяет поставить внешний ключ ПОВЕРХ существующих
-- значений, ничего не мигрируя. Пользовательские виды получают сгенерированный код USER_<n> —
-- он служебный, человеку показывается только название.
--
-- ЦВЕТ ХРАНИТСЯ ИМЕНЕМ ПАЛИТРЫ, А НЕ HEX. Фронт на Tailwind, а тот собирает классы статически:
-- 'bg-amber-100' из базы в вёрстку не подставить — класса просто не окажется в сборке. Поэтому в
-- колонке лежит ключ ('amber', 'sky', …), а набор классов по ключу знает фронт.
--
-- СИСТЕМНЫЕ СТРОКИ. Девять видов, живших в enum, помечены is_system: их код неизменен (на него
-- ссылаются существующие ограничения), название и цвет — правь сколько угодно. Удалять системные
-- нельзя, гасить (active=false) можно: погашенное не предлагается в выборе, но уже проставленное
-- показывается — как в справочниках регалий (миграция 020).
--
-- УДАЛЕНИЕ — RESTRICT. Вид, которым размечены ограничения, БД удалить не даст; цена удаления
-- называется заранее, как для аудитории, корпуса и дисциплины.
--
-- Дата: 2026-08-11
-- ================================================================

CREATE TABLE kind_of_constraint
(
    code       VARCHAR(50)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    short_name VARCHAR(50)  NOT NULL UNIQUE,
    color      VARCHAR(20)  NOT NULL DEFAULT 'slate',
    sort_order INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_system  BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE kind_of_constraint IS
    'Справочник видов ограничений; ведёт пользователь. Раньше — Java-enum KindOfConstraints.';
COMMENT ON COLUMN kind_of_constraint.code      IS 'Ключ (PK). У системных — прежние имена enum, у пользовательских — USER_<n>.';
COMMENT ON COLUMN kind_of_constraint.color     IS 'Ключ палитры фронта (amber, sky, rose…), НЕ hex: классы Tailwind собираются статически.';
COMMENT ON COLUMN kind_of_constraint.active    IS 'Погашенный вид не предлагается в выборе, но уже проставленный показывается.';
COMMENT ON COLUMN kind_of_constraint.is_system IS 'Пришёл из кода: код неизменен, удалять нельзя; название и цвет правятся.';

-- Девять видов из ru.enums.KindOfConstraints. Цвета — те, что были зашиты во фронте
-- (features/constraints/constraintStyles.ts), чтобы вид сетки не изменился ни на пиксель.
INSERT INTO kind_of_constraint (code, name, short_name, color, sort_order, is_system)
VALUES ('BUSINESS_TRIP', 'Командировка', 'Ком', 'amber', 10, TRUE),
       ('VACATION', 'Отпуск', 'Отп', 'emerald', 20, TRUE),
       ('EXAM_SESSION', 'Экзаменационная сессия', 'ЭкзС', 'rose', 30, TRUE),
       ('MEDICAL_CARE', 'Углубленно-медицинское обеспечение', 'УМО', 'sky', 40, TRUE),
       ('LIBRARY', 'Библиотека', 'Биб', 'violet', 50, TRUE),
       ('FINAL_STATE_ATTESTATION', 'Государственная итоговая аттестация', 'ГИА', 'fuchsia', 60, TRUE),
       ('INDIVIDUAL_WORK', 'Самостоятельная работа', 'СР', 'teal', 70, TRUE),
       ('WEEKEND', 'Выходной', 'Вых', 'indigo', 80, TRUE),
       ('OTHER', 'Другой вид ограничения', 'ДВО', 'slate', 90, TRUE);

-- Страховка перед внешним ключом: если в данных завёлся код, которого в enum уже не было
-- (переименование в прошлом, ручная правка), FK бы не создался и миграция упала бы на живой базе.
-- Заводим такие строки как «неизвестный вид» — видно в справочнике, чинится руками, ничего не
-- теряется. На чистой базе этот INSERT не делает ничего.
INSERT INTO kind_of_constraint (code, name, short_name, color, sort_order, is_system)
SELECT found.code,
       'Неизвестный вид (' || found.code || ')',
       substr(found.code, 1, 20),
       'slate',
       900,
       FALSE
FROM (SELECT DISTINCT kind_of_constraint AS code FROM educator_constraint
      UNION
      SELECT DISTINCT kind_of_constraint FROM group_constraint
      UNION
      SELECT DISTINCT kind_of_constraint FROM auditorium_constraint) AS found
WHERE found.code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM kind_of_constraint k WHERE k.code = found.code);

ALTER TABLE educator_constraint
    ADD CONSTRAINT fk_educator_constraint_kind
        FOREIGN KEY (kind_of_constraint) REFERENCES kind_of_constraint (code) ON DELETE RESTRICT;

ALTER TABLE group_constraint
    ADD CONSTRAINT fk_group_constraint_kind
        FOREIGN KEY (kind_of_constraint) REFERENCES kind_of_constraint (code) ON DELETE RESTRICT;

ALTER TABLE auditorium_constraint
    ADD CONSTRAINT fk_auditorium_constraint_kind
        FOREIGN KEY (kind_of_constraint) REFERENCES kind_of_constraint (code) ON DELETE RESTRICT;
