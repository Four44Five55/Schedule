package ru.dto.studyPeriod;

import ru.enums.PeriodType;
import java.time.LocalDate;

/**
 * DTO для представления информации об учебном периоде.
 */
public record StudyPeriodDto(
        Integer id,
        String name,
        int studyYear,
        PeriodType periodType,
        LocalDate startDate,
        LocalDate endDate
) {}