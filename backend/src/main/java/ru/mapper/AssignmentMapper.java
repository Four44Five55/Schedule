package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import ru.dto.assignment.AssignmentDto;
import ru.entity.Assignment;
import ru.entity.Educator;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;
import ru.enums.KindOfStudy;

import java.util.List;

/**
 * Маппер для преобразования между сущностью Assignment и ее DTO.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AssignmentMapper {

    /**
     * Преобразует сущность Assignment в AssignmentDto.
     * Использует другие методы этого маппера для преобразования вложенных объектов.
     */
    AssignmentDto toDto(Assignment assignment);

    /**
     * Преобразует список сущностей в список DTO.
     */
    List<AssignmentDto> toDtoList(List<Assignment> assignments);

    /**
     * Преобразует CurriculumSlot в его краткое DTO.
     * Использует кастомный маппинг для kindOfStudyName.
     */
    @Mapping(source = "kindOfStudy", target = "kindOfStudyName", qualifiedByName = "kindOfStudyToName")
    AssignmentDto.CurriculumSlotBriefDto toBriefDto(CurriculumSlot curriculumSlot);

    /**
     * Преобразует StudyStream в его краткое DTO.
     */
    AssignmentDto.StudyStreamBriefDto toBriefDto(StudyStream studyStream);

    /**
     * Преобразует Educator в его краткое DTO.
     */
    AssignmentDto.EducatorBriefDto toBriefDto(Educator educator);

    /**
     * Кастомный метод, который MapStruct будет использовать для преобразования
     * enum KindOfStudy в его строковое представление (название).
     */
    @Named("kindOfStudyToName")
    default String kindOfStudyToName(KindOfStudy kindOfStudy) {
        return kindOfStudy != null ? kindOfStudy.getFullName() : null;
    }
}
