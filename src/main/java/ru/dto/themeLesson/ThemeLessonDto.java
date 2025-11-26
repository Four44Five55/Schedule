package ru.dto.themeLesson;

public record ThemeLessonDto(
        Integer id,
        String themeNumber,
        String title,
        Integer disciplineId,
        String disciplineName
) {}
