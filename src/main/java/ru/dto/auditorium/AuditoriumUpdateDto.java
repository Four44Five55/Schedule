package ru.dto.auditorium;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditoriumUpdateDto(
        @NotBlank @Size(max = 255) String name,
        @Min(1) int capacity,
        @NotNull Integer buildingId
) {
}