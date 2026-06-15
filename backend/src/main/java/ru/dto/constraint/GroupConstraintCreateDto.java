package ru.dto.constraint;

import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public record GroupConstraintCreateDto(
        Integer groupId,
        KindOfConstraints kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
