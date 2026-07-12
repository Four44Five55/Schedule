package ru.services.order;

import ru.enums.TimeSlotPair;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Размещённое занятие ОДНОГО курса — вход правила порядка изучения ({@link LessonOrderRule}).
 *
 * <p>Развёрнуто по группам: лекция читается потоку, практика — группе, поэтому единственный
 * общий знаменатель, через который их вообще можно сравнивать, — это группа. Одно занятие
 * потока попадает сюда с несколькими {@code groupIds}.</p>
 *
 * <p>Темы правилу не нужны — только {@code planPosition}. Это сознательно: правило работает и
 * там, где темы не проставлены, а «тема едет с занятием» при пересортировке
 * ({@link ru.services.reindex.TrackReorderStrategy}), поэтому опираться на неё нельзя.</p>
 *
 * @param placementId  размещение (чтобы вернуть вердикт фронту)
 * @param date         дата размещения
 * @param slot         пара размещения
 * @param planPosition позиция слота в учебном плане курса (порядок изучения)
 * @param lecture      лекция ли это; всё остальное (ПЗ, ЛР, аттестации) — «практика»
 * @param assessment   аттестация ли это (ЭКЗ/ЗО/ЗЧ). Такие занятия по смыслу стоят в конце курса,
 *                     через месяцы после последней лекции, поэтому из проверки ОТРЫВА они
 *                     исключены — иначе загорелись бы все и утопили полезный сигнал. В проверке
 *                     «раньше лекции» участвуют наравне с практиками.
 * @param groupIds     группы, которые видят это занятие (у практики обычно одна)
 */
public record PlannedLesson(
        UUID placementId,
        LocalDate date,
        TimeSlotPair slot,
        int planPosition,
        boolean lecture,
        boolean assessment,
        Set<Integer> groupIds
) {
}
