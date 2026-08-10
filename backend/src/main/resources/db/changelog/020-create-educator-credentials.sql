-- ================================================================
-- Регалии преподавателя: специальное звание, учёная степень, учёное звание
-- ================================================================
-- Задача: подпись преподавателя вида «п-к юст Иванов И.И., к.т.н., доц» — в разделе
-- «Преподаватели» и в таблице «Обозначения» выгрузки. Отдельный повод — импорт расписания из
-- сторонней программы: в чужих файлах преподаватель назван строкой, и звание в ней стоит
-- приставкой к фамилии, то есть его надо уметь опознать, чтобы разобрать ФИО.
--
-- ПОЧЕМУ ЗВАНИЯ/СЛУЖБЫ/ОТРАСЛИ — СПРАВОЧНИКИ, А СТЕПЕНЬ И УЧЁНОЕ ЗВАНИЕ — ENUM.
-- Критерий один: кто добавляет значение и требует ли добавление релиза.
--   * специальное звание, род службы, отрасль науки — перечни ведёт пользователь (набор званий
--     зависит от вуза, номенклатуру отраслей укрупняли и будут) → таблицы с CRUD, как
--     auditorium_purpose;
--   * кандидат/доктор и доцент/профессор — по два значения, заданы нормативкой, добавлять нечего
--     → Java-enum (ru.enums.AcademicDegree / AcademicTitle), здесь только копия строк ради FK и
--     читаемости сырого SQL — ровно как kind_of_study при KindOfStudy и org_unit_type при
--     OrgUnitType. ИСТОЧНИК ПРАВДЫ для них — enum.
-- Выбор обратим и локален: подпись собирает чистая функция EducatorTitles над уже разрешёнными
-- строками, она не знает, пришло значение из enum или из таблицы.
--
-- РОД СЛУЖБЫ — ОТДЕЛЬНОЕ ПОЛЕ, А НЕ СТРОКИ «полковник юстиции» В СПРАВОЧНИКЕ ЗВАНИЙ.
-- Звание и служба — независимые оси; склейка их в одну строку дала бы N×M строк, где добавление
-- новой службы обязывает завести её ко всем нужным званиям вручную (а забыв одно — получить
-- «нет такого звания»). Служба используется как исключение и почти всегда NULL.
--
-- УЧЁНАЯ СТЕПЕНЬ — ДВА ПОЛЯ (УРОВЕНЬ + ОТРАСЛЬ), А НЕ ГОТОВАЯ СТРОКА «к.т.н.».
-- «к.т.н.» = кандидат × технические, то есть та же N×M. Хранить готовое сокращение — значит
-- завести третье представление одного факта, которое разойдётся с двумя первыми.
--
-- СОКРАЩЕНИЯ ХРАНЯТСЯ БЕЗ ТОЧЕК («п-к», «юст», «т», «доц») — решение заказчика. Точки, где они
-- нужны по форме («к.т.н.»), расставляет форматтер: точка — часть формата, а не часть данных.
-- Побочно это снимает неоднозначность при разборе чужого файла («п-к» и «п-к.» — одно и то же).
--
-- ССЫЛКИ У educator — ВСЕ NULLABLE, ПО ТОЙ ЖЕ ПРИЧИНЕ, ЧТО org_unit_id (018) и
-- enrollment_year (019): у существующей сотни преподавателей этих данных нет, взять их неоткуда,
-- а «не указано» — легитимное состояние, а не дефект.
--
-- УДАЛЕНИЕ СТРОКИ СПРАВОЧНИКА — RESTRICT: звание, за которым числятся люди, молча удалить нельзя.
-- Цена называется заранее — в списке едет число преподавателей и флаг deletable (считает бэк).
-- Мягкая альтернатива — active = false: устаревшее звание пропадает из выбора, но история цела
-- (тот же приём, что у org_unit.active).
--
-- ЗАДЕЛ ПОД ДОЛЖНОСТЬ (преподаватель / старший преподаватель / начальник кафедры). Сейчас не
-- нужна (решение заказчика), но добавляется без переделки: ещё одна таблица той же формы, ещё
-- одна nullable FK и ещё одна константа в EducatorDictionaryKind. Ни подпись, ни экраны
-- переписывать не придётся — подпись собирается из упорядоченного списка частей, а справочники
-- обслуживаются одним контроллером и одним экраном.
-- ⚠️ Должность ≠ учёное звание: «доцент» бывает и тем и другим. Смешивать их в одном поле нельзя.
--
-- Дата: 2026-08-10
-- ================================================================

-- ---------------------------------------------------------------
-- 1. Справочники, которые ведёт пользователь
-- ---------------------------------------------------------------
-- Три таблицы одной формы: имя, сокращение, порядок, признак активности.
--   * short_name NOT NULL UNIQUE — по нему собирается подпись И будет разбираться чужой файл;
--     два звания с одним сокращением сделали бы разбор неоднозначным;
--   * sort_order — звания идут по старшинству, а не по алфавиту. Шаг 10 оставляет место вставить
--     звание между существующими, не перенумеровывая остальные (приём из org_unit_type.nesting_rank);
--   * active — расформированное/устаревшее гасится, а не удаляется.

CREATE TABLE special_rank
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    short_name VARCHAR(50)  NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE rank_service
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    short_name VARCHAR(50)  NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE science_branch
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    short_name VARCHAR(50)  NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE special_rank IS 'Специальные (воинские) звания преподавателей; перечень ведёт пользователь';
COMMENT ON TABLE rank_service IS 'Род службы к специальному званию («юстиции», «внутренней службы»); используется как исключение';
COMMENT ON TABLE science_branch IS 'Отрасли науки для учёной степени («технические» → к.т.н.)';
COMMENT ON COLUMN special_rank.short_name IS 'Сокращение без точек («п-к»); по нему собирается подпись и опознаётся звание при импорте';
COMMENT ON COLUMN special_rank.sort_order IS 'Порядок в списке — по старшинству, шаг 10';
COMMENT ON COLUMN science_branch.short_name IS 'Буквенная часть сокращения степени без точек («т» → к.т.н., «ф-м» → к.ф-м.н.)';

-- Стартовое наполнение. Это данные, а не схема: пользователь правит и дополняет их в UI.
INSERT INTO special_rank (name, short_name, sort_order)
VALUES ('младший лейтенант', 'мл л-т', 10),
       ('лейтенант', 'л-т', 20),
       ('старший лейтенант', 'ст л-т', 30),
       ('капитан', 'к-н', 40),
       ('майор', 'м-р', 50),
       ('подполковник', 'п/п-к', 60),
       ('полковник', 'п-к', 70),
       ('генерал-майор', 'г-м', 80),
       ('генерал-лейтенант', 'г-л', 90);

INSERT INTO rank_service (name, short_name, sort_order)
VALUES ('юстиции', 'юст', 10),
       ('внутренней службы', 'вн сл', 20),
       ('медицинской службы', 'мед сл', 30);

INSERT INTO science_branch (name, short_name, sort_order)
VALUES ('технические', 'т', 10),
       ('физико-математические', 'ф-м', 20),
       ('педагогические', 'п', 30),
       ('военные', 'воен', 40),
       ('экономические', 'э', 50),
       ('юридические', 'ю', 60),
       ('исторические', 'и', 70),
       ('филологические', 'филол', 80),
       ('философские', 'филос', 90),
       ('психологические', 'психол', 100),
       ('социологические', 'социол', 110),
       ('химические', 'х', 120),
       ('биологические', 'б', 130),
       ('медицинские', 'мед', 140),
       ('географические', 'г', 150);

-- ---------------------------------------------------------------
-- 2. Справочные копии Java-enum'ов (аналог kind_of_study при KindOfStudy)
-- ---------------------------------------------------------------
-- Нужны для ссылочной целостности и чтения сырого SQL глазами. Источник правды — enum.

CREATE TABLE academic_degree
(
    enum_name         VARCHAR(50)  PRIMARY KEY,
    full_name         VARCHAR(255) NOT NULL UNIQUE,
    abbreviation_name VARCHAR(50)  NOT NULL UNIQUE
);

INSERT INTO academic_degree (enum_name, full_name, abbreviation_name)
VALUES ('CANDIDATE', 'кандидат наук', 'к'),
       ('DOCTOR', 'доктор наук', 'д');

CREATE TABLE academic_title
(
    enum_name         VARCHAR(50)  PRIMARY KEY,
    full_name         VARCHAR(255) NOT NULL UNIQUE,
    abbreviation_name VARCHAR(50)  NOT NULL UNIQUE
);

INSERT INTO academic_title (enum_name, full_name, abbreviation_name)
VALUES ('ASSOCIATE_PROFESSOR', 'доцент', 'доц'),
       ('PROFESSOR', 'профессор', 'проф');

COMMENT ON TABLE academic_degree IS 'Копия ru.enums.AcademicDegree ради FK и чтения сырого SQL; источник правды — enum';
COMMENT ON TABLE academic_title IS 'Копия ru.enums.AcademicTitle ради FK и чтения сырого SQL; источник правды — enum';

-- ---------------------------------------------------------------
-- 3. Регалии на преподавателе
-- ---------------------------------------------------------------
-- Индексов по этим колонкам нет намеренно: преподавателей сотня, список и так грузится целиком,
-- а фильтрация по званию/степени идёт в памяти на фронте (в отличие от охвата подразделения,
-- где нужен разворот дерева на бэке).

ALTER TABLE educator
    ADD COLUMN special_rank_id   INTEGER,
    ADD COLUMN rank_service_id   INTEGER,
    ADD COLUMN academic_degree   VARCHAR(50),
    ADD COLUMN science_branch_id INTEGER,
    ADD COLUMN academic_title    VARCHAR(50);

ALTER TABLE educator
    ADD CONSTRAINT fk_educator_special_rank
        FOREIGN KEY (special_rank_id) REFERENCES special_rank (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_educator_rank_service
        FOREIGN KEY (rank_service_id) REFERENCES rank_service (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_educator_science_branch
        FOREIGN KEY (science_branch_id) REFERENCES science_branch (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_educator_academic_degree
        FOREIGN KEY (academic_degree) REFERENCES academic_degree (enum_name),
    ADD CONSTRAINT fk_educator_academic_title
        FOREIGN KEY (academic_title) REFERENCES academic_title (enum_name);

COMMENT ON COLUMN educator.special_rank_id IS 'Специальное (воинское) звание; NULL — не указано';
COMMENT ON COLUMN educator.rank_service_id IS 'Род службы к званию («юстиции»); NULL — обычный случай';
COMMENT ON COLUMN educator.academic_degree IS 'Уровень учёной степени (CANDIDATE/DOCTOR); отрасль — в science_branch_id';
COMMENT ON COLUMN educator.science_branch_id IS 'Отрасль науки степени; вместе с academic_degree даёт «к.т.н.»';
COMMENT ON COLUMN educator.academic_title IS 'Учёное звание (ASSOCIATE_PROFESSOR/PROFESSOR) — НЕ должность';
