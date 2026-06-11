package ru.dto.constraint;

import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public record GroupConstraintDto(
        Integer id,
        Integer groupId,
        String groupName,
        KindOfConstraints kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
