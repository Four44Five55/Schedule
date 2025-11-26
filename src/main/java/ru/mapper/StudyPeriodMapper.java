package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.studyPeriod.StudyPeriodDto;
import ru.entity.StudyPeriod;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StudyPeriodMapper {
    StudyPeriodDto toDto(StudyPeriod studyPeriod);
}