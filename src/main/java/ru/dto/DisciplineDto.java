package ru.dto;

import ru.entity.Discipline;

public record DisciplineDto(
        int id,
        String name,
        String abbreviation
) {
    // Можно добавить статический метод для конвертации из entity
    public static DisciplineDto fromEntity(Discipline discipline) {
        return new DisciplineDto(
                discipline.getId(),
                discipline.getName(),
                discipline.getAbbreviation()
        );
    }
}
