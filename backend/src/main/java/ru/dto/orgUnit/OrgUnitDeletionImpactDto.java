package ru.dto.orgUnit;

/**
 * Предпросмотр последствий удаления подразделения.
 *
 * <p>В отличие от корпуса или аудитории, здесь каскада нет <b>намеренно</b>: все четыре ссылки на
 * подразделение ({@code org_unit.parent_id}, {@code educator.org_unit_id},
 * {@code groups.org_unit_id}, {@code auditorium.org_unit_id}) заведены с
 * {@code ON DELETE RESTRICT}. Расформирование факультета не должно молча уносить кафедры, а
 * кафедры — преподавателей: люди, группы и комнаты не производны от подразделения и обязаны его
 * пережить. Поэтому подразделение со связями удалить нельзя вовсе — сначала перепривязать или
 * поднять на уровень выше.</p>
 *
 * <p><b>{@code deletable} считает бэк</b> (прецедент — {@code BuildingDeletionImpactDto}): тем же
 * полем {@code OrgUnitService.delete} и отказывает, UI лишь показывает семантику.</p>
 *
 * <p>Альтернатива удалению — снять флаг {@code active}: подразделение исчезнет из выбора, но
 * исторические ссылки уцелеют.</p>
 *
 * @param orgUnitId  id подразделения
 * @param name       название (для текста подтверждения)
 * @param deletable  можно ли удалять: true, когда все счётчики нулевые
 * @param childUnits непосредственно вложенных подразделений
 * @param educators   преподавателей числится в подразделении
 * @param groups      групп числится в подразделении
 * @param auditoriums аудиторий закреплено за подразделением (миграция 025)
 */
public record OrgUnitDeletionImpactDto(
        Integer orgUnitId,
        String name,
        boolean deletable,
        long childUnits,
        long educators,
        long groups,
        long auditoriums
) {}
