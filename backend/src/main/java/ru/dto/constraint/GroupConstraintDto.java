package ru.dto.constraint;


import ru.enums.TimeSlotPair;

import java.time.LocalDate;

public record GroupConstraintDto(
        Integer id,
        Integer groupId,
        String groupName,
        String kindOfConstraint,
        String abbreviation,
        String fullName,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        TimeSlotPair timeSlot
) {}
