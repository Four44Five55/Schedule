package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import ru.dto.slotChain.SlotChainDto;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.SlotChain;
import ru.enums.KindOfStudy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SlotChainMapper {

    SlotChainDto toDto(SlotChain slotChain);

    @Mapping(source = "kindOfStudy", target = "kindOfStudyName", qualifiedByName = "kindOfStudyToName")
    SlotChainDto.CurriculumSlotBriefDto toBriefDto(CurriculumSlot curriculumSlot);

    @Named("kindOfStudyToName")
    default String kindOfStudyToName(KindOfStudy kindOfStudy) {
        return kindOfStudy != null ? kindOfStudy.getFullName() : null;
    }
}
