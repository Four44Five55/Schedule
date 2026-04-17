package ru.dto.building;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BuildingUpdateDto(
        @NotBlank @Size(max = 255)
        String name,

        @NotNull
        Integer locationId
) {}
