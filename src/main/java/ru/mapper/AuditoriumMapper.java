package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.auditorium.AuditoriumDto;
import ru.entity.Auditorium;
import ru.entity.Building;
import ru.entity.Location;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {AuditoriumPurposeMapper.class, FeatureMapper.class}
)
public interface AuditoriumMapper {

    AuditoriumDto toDto(Auditorium auditorium);

    AuditoriumDto.BuildingBriefDto toBriefDto(Building building);

    AuditoriumDto.LocationBriefDto toBriefDto(Location location);
}