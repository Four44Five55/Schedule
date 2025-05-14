package ru.dto;

import ru.enums.KindOfStudy;
import ru.entity.logicSchema.CurriculumSlot;

public record CurriculumSlotDto(
        int id,
        KindOfStudy kindOfStudy,
        ThemeLessonDto themeLesson
) {
    public static CurriculumSlotDto fromEntity(CurriculumSlot slot) {
        return new CurriculumSlotDto(
                slot.getId(),
                slot.getKindOfStudy(),
                slot.getThemeLesson() != null ?
                        ThemeLessonDto.fromEntity(slot.getThemeLesson()) : null
        );
    }
}