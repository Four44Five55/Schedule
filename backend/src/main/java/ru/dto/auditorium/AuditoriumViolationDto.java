package ru.dto.auditorium;

import java.util.List;

/**
 * Находка по аудитории конкретного занятия — для подсветки в сетке (по образцу
 * {@link ru.dto.order.OrderViolationDto}).
 *
 * <p><b>Подсказка, а не запрет.</b> Расписание с находкой валидно по остальным ресурсам; занятие
 * лишь помечается — как показывать, решает UI. Двойное бронирование — про физику (красный),
 * теснота — про суждение (янтарный с числом), ровно как два баннера дашборда.</p>
 *
 * <p>Отдаётся <b>по занятию</b>: те же находки, что датчик {@code AuditoriumHealthService}
 * схлопывает в счётчики, здесь остаются пофамильно. Одно занятие может дать две находки (комната
 * и занята, и мала) — они приходят двумя элементами с одним {@code placementId}.</p>
 *
 * @param placementId   занятие
 * @param auditoriumId  комната, о которой находка
 * @param auditoriumName имя комнаты (для показа/тултипа)
 * @param kind          {@code DOUBLE_BOOKED} — комната занята другим занятием (физика, только 0
 *                      допустимо); {@code OVER_CAPACITY} — поток не помещается (суждение)
 * @param excess        на сколько человек не хватает мест (осмысленно для {@code OVER_CAPACITY})
 * @param sharedWith    другие занятия в этой комнате и ячейке — человекочитаемо («Фил · 954»),
 *                      для {@code DOUBLE_BOOKED}; иначе пусто
 */
public record AuditoriumViolationDto(
        String placementId,
        Integer auditoriumId,
        String auditoriumName,
        String kind,
        int excess,
        List<String> sharedWith
) {
}
