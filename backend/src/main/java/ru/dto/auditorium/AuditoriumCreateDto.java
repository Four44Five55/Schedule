package ru.dto.auditorium;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AuditoriumCreateDto(
        @NotBlank @Size(max = 255) String name,
        @Min(1) int capacity,
        @NotNull Integer buildingId,
        Integer purposeId,
        List<Integer> featureIds,
        /* Кафедра-владелец; null — не указана. Обязательной не делаем: у большинства комнат в базе
           владельца нет, и требование сломало бы заведение аудитории «как раньше». */
        Integer orgUnitId
) {
}
