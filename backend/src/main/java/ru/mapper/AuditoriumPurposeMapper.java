package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.auditoriumPurpose.AuditoriumPurposeDto;
import ru.entity.AuditoriumPurpose;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditoriumPurposeMapper {
    AuditoriumPurposeDto toDto(AuditoriumPurpose purpose);
    List<AuditoriumPurposeDto> toDtoList(List<AuditoriumPurpose> purposes);
}