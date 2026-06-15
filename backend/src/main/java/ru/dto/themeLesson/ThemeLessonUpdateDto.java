package ru.dto.themeLesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ThemeLessonUpdateDto(
        @NotBlank String themeNumber,
        String title,
        @NotNull Integer disciplineId
) {}
