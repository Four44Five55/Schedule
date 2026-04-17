package ru.dto.auditoriumPool;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AuditoriumPoolCreateDto(
        @NotBlank String name,
        String description,
        List<Integer> auditoriumIds
) {}
