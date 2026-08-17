package ru.services.importing;

import java.util.List;
import java.util.UUID;

/**
 * Итог записи расписания: что встало, что нет и куда смотреть дальше.
 *
 * <p><b>Отчёт называет цену и остаток, а не только успех.</b> Импорт фиксирует факт, а не просит
 * разрешения (И-9), поэтому «записано N» само по себе ничего не гарантирует: смотреть надо на то,
 * сколько занятий не доехало и по какой причине.</p>
 *
 * @param sessionId      сессия, в которую записано расписание — по ней работают все датчики и по
 *                       ней же оно сносится
 * @param sessionName    имя сессии (человеку — чтобы узнать её в списке)
 * @param placements     записанных размещений
 * @param withoutRoom    записанных без аудитории: комната из файла у нас не нашлась либо
 *                       одноимённых несколько. Занятие при этом не теряется — время в нём верное
 * @param outsidePeriod  занятий вне периода: не размещаем (И-8), они уходят в очередь неразмещённых
 * @param notResolved    занятий, не доведённых до назначения — причины в {@code blockers}
 * @param projected      строк read-модели: {@code 0} — проекция не запрашивалась, и расписание
 *                       видно только датчиками Command Side
 * @param plan           отчёт плана этого же прохода: что заведено курсами, темами, слотами
 * @param blockers       причины, по которым занятия не доехали, с числом и образцом
 */
public record ScheduleWriteReport(
        UUID sessionId,
        String sessionName,
        int placements,
        int withoutRoom,
        int outsidePeriod,
        int notResolved,
        int projected,
        PlanReport plan,
        List<MergeReport.Finding> blockers
) {
}
