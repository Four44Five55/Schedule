package ru.dto.educator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.enums.DayOfWeek;
import ru.enums.TimeSlotPair;

import java.util.Set;

public record EducatorCreateDto(
        @NotBlank @Size(max = 255)
        String name,
        Set<DayOfWeek> preferredDays,
        Set<TimeSlotPair> preferredTimeSlots,
        boolean compactSchedule,

        // Подразделение (кафедра или отдел), необязательное поле: «не распределён» легитимно
        Integer orgUnitId
) {}