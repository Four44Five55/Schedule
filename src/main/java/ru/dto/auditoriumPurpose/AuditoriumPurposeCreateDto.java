package ru.dto.auditoriumPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuditoriumPurposeCreateDto(
        @NotBlank @Size(max = 255) String name
) {}
