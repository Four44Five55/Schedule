package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.dto.curriculumSlot.CurriculumSlotDto;
import ru.entity.Auditorium;
import ru.entity.logicSchema.AuditoriumPool;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.ThemeLesson;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CurriculumSlotMapper {

    /**
     * Преобразует сущность CurriculumSlot в CurriculumSlotDto для отображения.
     * Использует кастомные методы (например, toBriefDto) для маппинга вложенных сущностей.
     */
    @Mapping(source = "disciplineCourse.id", target = "disciplineCourseId")
    CurriculumSlotDto toDto(CurriculumSlot slot);

    /**
     * Вспомогательный метод для маппинга ThemeLesson в его краткое DTO.
     * MapStruct вызовет его автоматически при маппинге toDto.
     */
    CurriculumSlotDto.ThemeLessonBriefDto toBriefDto(ThemeLesson themeLesson);

    /**
     * Вспомогательный метод для маппинга Auditorium в его краткое DTO.
     */
    CurriculumSlotDto.AuditoriumBriefDto toBriefDto(Auditorium auditorium);

    /**
     * Вспомогательный метод для маппинга AuditoriumPool в его краткое DTO.
     */
    CurriculumSlotDto.AuditoriumPoolBriefDto toBriefDto(AuditoriumPool auditoriumPool);

    //TODO Реализовать логику Мапперы из Create/Update DTO в сущность в сервисе из-за необходимости
    // подгружать связанные сущности из БД по ID.
}
