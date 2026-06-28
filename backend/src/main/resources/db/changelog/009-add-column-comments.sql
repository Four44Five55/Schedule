--liquibase formatted sql

--changeset four4five5:9_add_column_comments
--comment: Краткие описания (COMMENT ON COLUMN) для ключевых/неочевидных полей таблиц
--          начальной схемы. Очевидные id (PK) и уже прокомментированные поля
--          (educator.compact_schedule — чейнджлог 002; CQRS-таблицы — 003–005) пропущены.

-- ===== Справочники и ядро =====
COMMENT ON COLUMN discipline.abbreviation        IS 'Аббревиатура дисциплины (для компактного отображения в сетке).';

COMMENT ON COLUMN location.address               IS 'Почтовый/физический адрес локации.';

COMMENT ON COLUMN building.location_id           IS 'FK → location: к какой локации относится корпус.';

COMMENT ON COLUMN feature.code                   IS 'Короткий машинный код оснащения (напр. PROJECTOR, PC_30).';

COMMENT ON COLUMN auditorium.capacity            IS 'Вместимость (мест); должна покрывать размер потока.';
COMMENT ON COLUMN auditorium.purpose_id          IS 'FK → auditorium_purpose: назначение аудитории.';
COMMENT ON COLUMN auditorium.building_id         IS 'FK → building: корпус, в котором находится аудитория.';

COMMENT ON COLUMN groups.size                    IS 'Численность группы (используется при подборе аудитории по вместимости).';
COMMENT ON COLUMN groups.base_auditorium_id      IS 'FK → auditorium: «домашняя» аудитория группы (резервный вариант).';

COMMENT ON COLUMN kind_of_study.enum_name        IS 'Машинное имя вида занятия (PK), совпадает с Java-enum KindOfStudy.';
COMMENT ON COLUMN kind_of_study.full_name        IS 'Полное название вида занятия для отображения.';
COMMENT ON COLUMN kind_of_study.abbreviation_name IS 'Краткое название вида занятия.';

COMMENT ON COLUMN study_stream.semester          IS 'Номер семестра потока.';

COMMENT ON COLUMN study_period.study_year        IS 'Учебный год периода.';
COMMENT ON COLUMN study_period.period_type       IS 'Тип периода (напр. осенний/весенний семестр).';
COMMENT ON COLUMN study_period.start_date        IS 'Дата начала периода (включительно).';
COMMENT ON COLUMN study_period.end_date          IS 'Дата окончания периода (включительно).';

-- ===== Учебный план =====
COMMENT ON COLUMN discipline_course.discipline_id   IS 'FK → discipline: какая дисциплина реализуется.';
COMMENT ON COLUMN discipline_course.study_period_id IS 'FK → study_period: в каком семестре.';

COMMENT ON COLUMN theme_lesson.discipline_id     IS 'FK → discipline: к какой дисциплине относится тема.';
COMMENT ON COLUMN theme_lesson.theme_number      IS 'Номер темы (уникален в пределах дисциплины).';
COMMENT ON COLUMN theme_lesson.title             IS 'Название темы занятия.';

COMMENT ON COLUMN curriculum_slot.discipline_course_id   IS 'FK → discipline_course: курс, к которому принадлежит слот.';
COMMENT ON COLUMN curriculum_slot.position               IS 'Порядковый номер слота в плане курса (уникален в пределах курса).';
COMMENT ON COLUMN curriculum_slot.kind_of_study          IS 'FK → kind_of_study: вид занятия.';
COMMENT ON COLUMN curriculum_slot.theme_lesson_id        IS 'FK → theme_lesson: тема занятия (опционально).';
COMMENT ON COLUMN curriculum_slot.required_auditorium_id IS 'FK → auditorium: жёстко требуемая аудитория (если задана).';
COMMENT ON COLUMN curriculum_slot.priority_auditorium_id IS 'FK → auditorium: приоритетная аудитория (если свободна — берётся она).';
COMMENT ON COLUMN curriculum_slot.required_purpose_id    IS 'FK → auditorium_purpose: требуемое назначение аудитории.';
COMMENT ON COLUMN curriculum_slot.allowed_pool_id        IS 'FK → auditorium_pool: разрешённый пул аудиторий для выбора.';

COMMENT ON COLUMN assignment.curriculum_slot_id  IS 'FK → curriculum_slot: какое занятие плана проводится.';
COMMENT ON COLUMN assignment.study_stream_id     IS 'FK → study_stream: для какого потока/подгруппы.';

COMMENT ON COLUMN slot_chain.slot_a_id           IS 'FK → curriculum_slot: первое звено сцепки.';
COMMENT ON COLUMN slot_chain.slot_b_id           IS 'FK → curriculum_slot: второе звено сцепки (≠ slot_a_id).';

-- ===== Постоянные ограничения =====
COMMENT ON COLUMN educator_constraint.educator_id        IS 'FK → educator: чьё ограничение доступности.';
COMMENT ON COLUMN educator_constraint.kind_of_constraint IS 'Вид ограничения.';
COMMENT ON COLUMN educator_constraint.start_date         IS 'Начало действия ограничения (включительно).';
COMMENT ON COLUMN educator_constraint.end_date           IS 'Окончание действия ограничения (включительно).';
COMMENT ON COLUMN educator_constraint.description        IS 'Произвольное описание ограничения.';

COMMENT ON COLUMN group_constraint.group_id            IS 'FK → groups: чьё ограничение доступности.';
COMMENT ON COLUMN group_constraint.kind_of_constraint  IS 'Вид ограничения.';
COMMENT ON COLUMN group_constraint.start_date          IS 'Начало действия ограничения (включительно).';
COMMENT ON COLUMN group_constraint.end_date            IS 'Окончание действия ограничения (включительно).';
COMMENT ON COLUMN group_constraint.description         IS 'Произвольное описание ограничения.';

COMMENT ON COLUMN auditorium_constraint.auditorium_id      IS 'FK → auditorium: чьё ограничение доступности.';
COMMENT ON COLUMN auditorium_constraint.kind_of_constraint IS 'Вид ограничения.';
COMMENT ON COLUMN auditorium_constraint.start_date         IS 'Начало действия ограничения (включительно).';
COMMENT ON COLUMN auditorium_constraint.end_date           IS 'Окончание действия ограничения (включительно).';
COMMENT ON COLUMN auditorium_constraint.description        IS 'Произвольное описание ограничения.';
