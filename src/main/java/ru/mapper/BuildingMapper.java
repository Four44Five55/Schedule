package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.building.BuildingDto;
import ru.entity.Auditorium;
import ru.entity.Building;
import ru.entity.Location;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BuildingMapper {

    BuildingDto toDto(Building building);

    BuildingDto.LocationBriefDto toBriefDto(Location location);

    BuildingDto.AuditoriumBriefDto toBriefDto(Auditorium auditorium);
}
