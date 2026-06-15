package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.dto.themeLesson.ThemeLessonDto;
import ru.entity.logicSchema.ThemeLesson;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ThemeLessonMapper {

    @Mapping(source = "discipline.id", target = "disciplineId")
    @Mapping(source = "discipline.name", target = "disciplineName")
    ThemeLessonDto toDto(ThemeLesson themeLesson);
}
