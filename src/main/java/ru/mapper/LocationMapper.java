package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.location.LocationDto;
import ru.entity.Building;
import ru.entity.Location;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LocationMapper {

    LocationDto toDto(Location location);

    LocationDto.BuildingBriefDto toBriefDto(Building building);
}
