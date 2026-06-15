package ru.dto.constraint;

import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public record AuditoriumConstraintCreateDto(
        Integer auditoriumId,
        KindOfConstraints kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
