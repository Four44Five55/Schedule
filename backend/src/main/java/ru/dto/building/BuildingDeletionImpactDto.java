package ru.dto.building;

/**
 * Предпросмотр последствий удаления учебного корпуса.
 *
 * <p>Удаление корпуса — <b>каскадная порча расписания</b>: {@code auditorium.building_id ON DELETE
 * CASCADE} уносит ВСЕ аудитории корпуса, а вслед за ними ({@code placement_auditoriums ON DELETE
 * CASCADE}) занятия в этих аудиториях остаются стоять <b>без комнаты</b> — включая закреплённые
 * вручную. Вернуть комнату им автоматически нечем: подбор идёт только при генерации/переносе.</p>
 *
 * <p>Отдельно считается {@code slotsRequiringIt}: если хоть одну аудиторию корпуса жёстко требует
 * или приоритетно предпочитает слот учебного плана, БД не даст удалить её каскадом
 * ({@code required/priority_auditorium_id} — без каскада), и раньше это вылезло бы сырым 500.
 * Тогда {@code deletable = false}.</p>
 *
 * <p><b>{@code deletable} — поле, а не вывод на фронте</b> (прецедент — {@link ru.dto.auditorium.AuditoriumDeletionImpactDto}):
 * правило считается ЗДЕСЬ, {@code BuildingService.deleteBuilding} им же и отказывает, UI лишь
 * показывает семантику.</p>
 *
 * @param buildingId        id корпуса
 * @param name              название (для текста подтверждения)
 * @param deletable         можно ли удалять вообще
 * @param auditoriumCount   аудиторий уйдёт каскадом
 * @param placedLessons     занятий стоит в этих аудиториях — останутся без комнаты
 * @param lockedLessons     из них закреплено вручную (замок) — самая дорогая потеря
 * @param slotsRequiringIt  слотов плана ссылается на аудитории корпуса как требуемые/приоритетные;
 *                          &gt; 0 → {@code deletable = false}
 * @param groupsUsingAsBase групп числят аудитории корпуса домашними ({@code base_auditorium} обнулится)
 */
public record BuildingDeletionImpactDto(
        Integer buildingId,
        String name,
        boolean deletable,
        long auditoriumCount,
        long placedLessons,
        long lockedLessons,
        long slotsRequiringIt,
        long groupsUsingAsBase
) {}
