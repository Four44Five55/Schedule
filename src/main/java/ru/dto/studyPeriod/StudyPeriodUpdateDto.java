package ru.dto.studyPeriod;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.enums.PeriodType;

import java.time.LocalDate;

public record StudyPeriodUpdateDto(
        @NotBlank String name,
        @Min(2020) int studyYear,
        @NotNull PeriodType periodType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
