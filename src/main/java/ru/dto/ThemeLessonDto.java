package ru.dto;

import ru.entity.logicSchema.ThemeLesson;

public record ThemeLessonDto(
        int id,
        String themeNumber,
        String title
) {
    public static ThemeLessonDto fromEntity(ThemeLesson theme) {
        return new ThemeLessonDto(
                theme.getId(),
                theme.getThemeNumber(),
                theme.getTitle()
        );
    }
}