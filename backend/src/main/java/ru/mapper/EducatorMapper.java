package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.educator.EducatorDto;
import ru.entity.Educator;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EducatorMapper {
    EducatorDto toDto(Educator educator);
}