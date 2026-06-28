--liquibase formatted sql

--changeset four4five5:8_add_table_comments
--comment: Краткие описания (COMMENT ON TABLE) для таблиц из начальной схемы (001).
--          CQRS-таблицы (schedule_session, lesson_placement, placement_auditoriums,
--          schedule_view) уже прокомментированы в чейнджлогах 003–005.

-- ===== Справочники и ядро =====
COMMENT ON TABLE discipline          IS 'Справочник дисциплин (общая информация, не привязанная к семестру).';
COMMENT ON TABLE location            IS 'Справочник локаций/кампусов.';
COMMENT ON TABLE building            IS 'Учебные корпуса; каждый принадлежит одной локации.';
COMMENT ON TABLE auditorium_purpose  IS 'Справочник назначений аудиторий (лекционная, компьютерный класс и т.п.).';
COMMENT ON TABLE feature             IS 'Справочник оснащения аудиторий (проектор, ПК и т.п.).';
COMMENT ON TABLE auditorium          IS 'Аудитории; каждая принадлежит одному корпусу, имеет вместимость и назначение.';
COMMENT ON TABLE auditorium_pool     IS 'Пулы (группы) аудиторий, из которых можно выбирать место для занятия.';
COMMENT ON TABLE groups              IS 'Учебные группы; со ссылкой на «домашнюю» аудиторию.';
COMMENT ON TABLE educator            IS 'Преподаватели. Флаг compact_schedule включает компактное расписание.';
COMMENT ON TABLE kind_of_study       IS 'Справочник видов занятий (enum-таблица: LECTURE, PRACTICAL_WORK, EXAM и т.д.).';
COMMENT ON TABLE study_stream        IS 'Учебные потоки/подгруппы (объединение групп для совместных занятий).';
COMMENT ON TABLE study_period        IS 'Учебные периоды (семестры) с датами начала и окончания.';

-- ===== Учебный план =====
COMMENT ON TABLE discipline_course   IS 'Курс — реализация дисциплины в конкретном учебном периоде.';
COMMENT ON TABLE theme_lesson        IS 'Темы занятий, привязанные к дисциплине.';
COMMENT ON TABLE curriculum_slot     IS 'Слот учебного плана: что и в каком порядке проводится, с требованиями к месту.';
COMMENT ON TABLE assignment          IS 'Назначение: кто (поток + преподаватели) проводит занятие из учебного плана.';
COMMENT ON TABLE slot_chain          IS 'Сцепки неразрывных занятий: пары слотов плана, идущих единой цепочкой.';

-- ===== Связующие таблицы (M:N) и предпочтения =====
COMMENT ON TABLE auditorium_features              IS 'M:N: оснащение, имеющееся в аудитории.';
COMMENT ON TABLE auditorium_pool_mapping          IS 'M:N: состав пула аудиторий.';
COMMENT ON TABLE stream_groups                    IS 'M:N: какие группы входят в поток.';
COMMENT ON TABLE assignment_educators             IS 'M:N: преподаватели, назначенные на конкретное назначение.';
COMMENT ON TABLE curriculum_slot_required_features IS 'M:N: требования к оснащению для слота учебного плана.';
COMMENT ON TABLE educator_day_priority            IS 'Предпочтения преподавателя по дням недели.';
COMMENT ON TABLE educator_slot_priority           IS 'Предпочтения преподавателя по парам (временным слотам).';

-- ===== Постоянные ограничения =====
COMMENT ON TABLE educator_constraint   IS 'Постоянные ограничения доступности преподавателя на период.';
COMMENT ON TABLE group_constraint      IS 'Постоянные ограничения доступности группы на период.';
COMMENT ON TABLE auditorium_constraint IS 'Постоянные ограничения доступности аудитории на период.';
