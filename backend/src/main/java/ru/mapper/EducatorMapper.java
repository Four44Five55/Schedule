package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.dto.educator.EducatorDto;
import ru.entity.Educator;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EducatorMapper {

    @Mapping(target = "orgUnitId", source = "orgUnit.id")
    @Mapping(target = "orgUnitName", source = "orgUnit.name")
    EducatorDto toDto(Educator educator);
}