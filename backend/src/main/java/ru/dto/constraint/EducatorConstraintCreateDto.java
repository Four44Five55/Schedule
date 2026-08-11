package ru.dto.constraint;


import ru.enums.TimeSlotPair;

import java.time.LocalDate;

public record EducatorConstraintCreateDto(
        Integer educatorId,
        String kindOfConstraint,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        /** Пара ограничения; null = весь день. */
        TimeSlotPair timeSlot
) {}
