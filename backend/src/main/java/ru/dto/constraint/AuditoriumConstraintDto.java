package ru.dto.constraint;


import ru.enums.TimeSlotPair;

import java.time.LocalDate;

public record AuditoriumConstraintDto(
        Integer id,
        Integer auditoriumId,
        String auditoriumName,
        String kindOfConstraint,
        String abbreviation,
        String fullName,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        TimeSlotPair timeSlot
) {}
