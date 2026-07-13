package ru.dto.auditorium;

/**
 * Предпросмотр последствий удаления аудитории.
 *
 * <p>Удаление аудитории — <b>необратимая порча расписания</b>, и до этой проверки оно шло молча:
 * {@code placement_auditoriums} уходит каскадом ({@code auditorium_id ON DELETE CASCADE}), поэтому
 * занятия остаются стоять, но <b>без комнаты</b> — включая закреплённые вручную. Вернуть аудиторию
 * им автоматически нечем: подбор делается только при генерации/переносе.</p>
 *
 * <p>Отдельно считается {@code slotsRequiringIt}: если аудиторию жёстко требует или приоритетно
 * предпочитает слот учебного плана, БД удалить её не даст вовсе
 * ({@code curriculum_slot.required/priority_auditorium_id} — без каскада), и раньше это вылезало
 * сырым 500. Теперь удаление отклоняется осмысленно.</p>
 *
 * <p><b>{@code deletable} — поле, а не вывод на фронте.</b> Правило «аудиторию, на которую
 * ссылается план, удалить нельзя» — знание бэкенда, и живёт оно в одном месте: здесь его считают,
 * {@code AuditoriumService.deleteAuditorium} им же и отказывает. UI лишь показывает семантику
 * (прецедент — {@code OrderViolation.kind}: бэк отдаёт смысл, фронт решает, как его нарисовать).</p>
 *
 * @param auditoriumId      id аудитории
 * @param name              название (для текста подтверждения)
 * @param deletable         можно ли удалять вообще
 * @param placedLessons     занятий стоит в этой аудитории — они останутся без комнаты
 * @param lockedLessons     из них закреплено вручную (замок) — самая дорогая потеря
 * @param slotsRequiringIt  слотов плана ссылается на неё как на требуемую/приоритетную;
 *                          &gt; 0 → {@code deletable = false}
 * @param groupsUsingAsBase групп числят её домашней ({@code base_auditorium} обнулится)
 */
public record AuditoriumDeletionImpactDto(
        Integer auditoriumId,
        String name,
        boolean deletable,
        long placedLessons,
        long lockedLessons,
        long slotsRequiringIt,
        long groupsUsingAsBase
) {}
