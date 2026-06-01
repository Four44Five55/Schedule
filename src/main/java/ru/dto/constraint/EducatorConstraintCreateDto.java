package ru.dto.constraint;

import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public record EducatorConstraintCreateDto(
        Integer educatorId,
        KindOfConstraints kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
