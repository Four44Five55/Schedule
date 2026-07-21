package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.dto.group.GroupDto;
import ru.entity.Auditorium;
import ru.entity.Group;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface GroupMapper {

    @Mapping(target = "orgUnitId", source = "orgUnit.id")
    @Mapping(target = "orgUnitName", source = "orgUnit.name")
    GroupDto toDto(Group group);

    // Вспомогательный метод для маппинга Auditorium -> AuditoriumBriefDto
    GroupDto.AuditoriumBriefDto toBriefDto(Auditorium auditorium);
}