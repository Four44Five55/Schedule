package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.auditoriumPool.AuditoriumPoolDto;
import ru.entity.Auditorium;
import ru.entity.logicSchema.AuditoriumPool;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditoriumPoolMapper {
    AuditoriumPoolDto toDto(AuditoriumPool pool);

    AuditoriumPoolDto.AuditoriumBriefDto toBriefDto(Auditorium auditorium);
}