package ru.dto.auditoriumPool;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AuditoriumPoolUpdateDto(
        @NotBlank String name,
        String description,
        List<Integer> auditoriumIds // Полный новый список ID аудиторий в пуле
) {}
