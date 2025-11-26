package ru.dto.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationCreateDto(
        @NotBlank @Size(max = 255) String name,
        String address
) {}
