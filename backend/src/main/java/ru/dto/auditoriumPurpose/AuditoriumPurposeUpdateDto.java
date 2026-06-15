package ru.dto.auditoriumPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuditoriumPurposeUpdateDto(
        @NotBlank @Size(max = 255) String name
) {}
