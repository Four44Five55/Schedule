package ru.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO для ответа с результатом генерации/загрузки расписания.
 */
public record ScheduleResultDto(
        String status,
        List<ScheduledLessonDto> lessons,
        Map<String, List<ScheduledLessonDto>> grid,
        int placedCount,
        int unplacedCount,
        String startDate,
        String endDate,
        int totalSlots,
        int usedSlots
) {
}
