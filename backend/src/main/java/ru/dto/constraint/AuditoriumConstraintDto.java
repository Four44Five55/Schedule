package ru.dto.constraint;

import ru.enums.KindOfConstraints;

import java.time.LocalDate;

public record AuditoriumConstraintDto(
        Integer id,
        Integer auditoriumId,
        String auditoriumName,
        KindOfConstraints kindOfConstraint,
        String abbreviation,
        String fullName,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
