package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.group.GroupDto;
import ru.entity.Auditorium;
import ru.entity.Group;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface GroupMapper {

    GroupDto toDto(Group group);

    // Вспомогательный метод для маппинга Auditorium -> AuditoriumBriefDto
    GroupDto.AuditoriumBriefDto toBriefDto(Auditorium auditorium);
}