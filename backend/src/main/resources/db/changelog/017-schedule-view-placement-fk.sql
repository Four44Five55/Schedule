-- ================================================================
-- schedule_view.placement_id → lesson_placement(id) ON DELETE CASCADE
-- ================================================================
-- Проблема (найдена 2026-07-13 по «залипшему» занятию: гр. 963, 03.09, 3-я пара, ИЯ):
-- read-модель не имела FK на write-сторону, а lesson_placement сносится каскадами БД
-- по двум цепочкам:
--     discipline → discipline_course → curriculum_slot → assignment → lesson_placement
--     schedule_session ────────────────────────────────────────────→ lesson_placement
-- Каскад идёт мимо приложения: Hibernate о нём не знает, PlacementChangedEvent(DELETED)
-- не публикуется, и строки schedule_view остаются СИРОТАМИ. Такое занятие видно в сетке,
-- но недостижимо ни одной командой (крестик, перенос, «очистить всё» и даже «пересобрать
-- read-модель» работают по существующим размещениям) — удалить его из UI невозможно.
-- Код чистил проекцию лишь на части путей удаления (AssignmentService, CourseDeletionService),
-- то есть инвариант держался на памяти программиста.
--
-- Решение: сделать проекцию формально производной. FK с ON DELETE CASCADE закрывает КЛАСС
-- проблемы целиком: строка view физически не может пережить своё размещение, кем бы оно ни
-- было удалено — каскадом, будущим кодом или ручным SQL. Ни одного нового пути удаления
-- помнить не нужно.
--
-- Развязка CQRS этим не нарушается: FK ограничивает не чтение, а лишь время жизни строки.
-- Read и write лежат в одной БД; если read-модель когда-нибудь переедет в отдельную,
-- защиту возьмёт на себя владелец проекции в коде (ScheduleSynchronizer).
--
-- placement_id остаётся NULLABLE (FK допускает NULL) — колонка не меняется.
--
-- Дата: 2026-07-13
-- ================================================================

-- Разовая уборка уже накопившихся сирот: без неё ADD CONSTRAINT не пройдёт валидацию.
-- Идемпотентно — на чистой базе удаляет 0 строк.
DELETE FROM schedule_view v
WHERE v.placement_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lesson_placement p WHERE p.id = v.placement_id);

ALTER TABLE schedule_view
    ADD CONSTRAINT schedule_view_placement_id_fkey
    FOREIGN KEY (placement_id) REFERENCES lesson_placement (id) ON DELETE CASCADE;

COMMENT ON CONSTRAINT schedule_view_placement_id_fkey ON schedule_view IS
    'Read-модель производна от lesson_placement: строка проекции не может пережить своё размещение (каскадное удаление вместо строк-призраков).';
