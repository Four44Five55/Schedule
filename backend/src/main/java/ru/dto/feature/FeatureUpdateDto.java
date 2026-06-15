package ru.dto.feature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeatureUpdateDto(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9_]+$") String code
) {}
