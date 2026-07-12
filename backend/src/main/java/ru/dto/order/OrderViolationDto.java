package ru.dto.order;

/**
 * Находка правила порядка изучения — для фронта.
 *
 * <p><b>Подсказка, а не запрет.</b> Расписание с находкой валидно по ресурсам; перенос и
 * установка не блокируются — занятие лишь помечается в сетке.</p>
 *
 * <p>Контракт несёт только <i>семантику</i> ({@code kind}); как её показывать — решает UI
 * (прецедент: {@code features/constraints/constraintStyles.ts} — презентационная мапа
 * «вид → цвет» на фронте).</p>
 *
 * @param placementId        занятие
 * @param lecturePlacementId лекция-причина (последняя по плану перед этим занятием)
 * @param groupId            группа, в чьей дорожке видна находка
 * @param kind               {@code BEFORE_LECTURE} — стоит раньше своей лекции (ошибка порядка);
 *                           {@code FAR_FROM_LECTURE} — слишком далеко после неё (предупреждение)
 * @param gapDays            календарных дней от лекции (осмысленно для {@code FAR_FROM_LECTURE})
 */
public record OrderViolationDto(
        String placementId,
        String lecturePlacementId,
        Integer groupId,
        String kind,
        int gapDays
) {
}
