package ru.dto.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationUpdateDto(
        @NotBlank @Size(max = 255) String name,
        String address
) {}
