package ru.dto.constraint;

import ru.enums.KindOfConstraints;
import ru.enums.TimeSlotPair;

import java.time.LocalDate;

public record AuditoriumConstraintCreateDto(
        Integer auditoriumId,
        KindOfConstraints kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        /** Пара ограничения; null = весь день. */
        TimeSlotPair timeSlot
) {}
