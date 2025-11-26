package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.studyStream.StudyStreamDto;
import ru.entity.Group;
import ru.entity.logicSchema.StudyStream;

import java.util.List;

/**
 * Маппер для преобразования между сущностью StudyStream и ее DTO.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StudyStreamMapper {

    /**
     * Преобразует сущность StudyStream в StudyStreamDto.
     * MapStruct автоматически преобразует Set<Group> в List<GroupBriefDto>,
     * используя вспомогательный метод toBriefDto(Group group).
     */
    StudyStreamDto toDto(StudyStream studyStream);

    /**
     * Преобразует список сущностей в список DTO.
     */
    List<StudyStreamDto> toDtoList(List<StudyStream> studyStreams);

    /**
     * Вспомогательный метод для маппинга сущности Group в ее краткое DTO.
     */
    StudyStreamDto.GroupBriefDto toBriefDto(Group group);

}