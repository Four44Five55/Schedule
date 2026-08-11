package ru.dto.constraint;


import ru.enums.TimeSlotPair;

import java.time.LocalDate;

public record EducatorConstraintDto(
        Integer id,
        Integer educatorId,
        String educatorName,
        String kindOfConstraint,
        String abbreviation,
        String fullName,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        TimeSlotPair timeSlot
) {}
