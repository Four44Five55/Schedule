package ru.dto.command;

import java.util.List;

/**
 * Ответ переупорядочивания трека: актуальная сессия-владелец (в т.ч. версия для фронта) и
 * список размещений «под флаг» с причинами (напр. распавшаяся сцепка) для предупреждений.
 *
 * @param session  сессия-владелец с актуальными данными
 * @param problems размещения под флагом
 */
public record ReorderResponse(ScheduleSessionDto session, List<ReorderProblemDto> problems) {
}
