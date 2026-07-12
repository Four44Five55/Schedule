-- ================================================================
-- Расширение UNIQUE на schedule_view до (placement_id, study_stream_id, educator_id)
-- ================================================================
-- Проблема: UNIQUE(placement_id, study_stream_id) из 006 запрещал вторую строку той же
-- группы для одного размещения. Read-модель проецируется по паре (группа × преподаватель),
-- поэтому занятие с НЕСКОЛЬКИМИ преподавателями (напр. английский вдвоём) на одну группу
-- давало две строки с одинаковым (placement_id, study_stream_id) → нарушение UNIQUE →
-- строки не записывались → занятие пропадало из расписания и группы, и преподавателей.
--
-- Решение: добавляем educator_id в уникальный ключ. Ключ становится ШИРЕ прежнего,
-- поэтому существующие данные (уже удовлетворявшие более строгому UNIQUE(placement, stream))
-- ему заведомо удовлетворяют — ALTER ADD не конфликтует.
--
-- Дата: 2026-07-10
-- ================================================================

ALTER TABLE schedule_view DROP CONSTRAINT IF EXISTS schedule_view_placement_group_unique;

ALTER TABLE schedule_view
    ADD CONSTRAINT schedule_view_placement_group_educator_unique
    UNIQUE (placement_id, study_stream_id, educator_id);
