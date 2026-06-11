package ru.dto.constraint;

import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public record EducatorConstraintDto(
        Integer id,
        Integer educatorId,
        String educatorName,
        KindOfConstraints kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
