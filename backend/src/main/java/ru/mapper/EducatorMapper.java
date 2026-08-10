package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import ru.dto.educator.EducatorDto;
import ru.entity.Educator;
import ru.services.educator.EducatorCredentialsAssembler;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EducatorMapper {

    @Mapping(target = "orgUnitId", source = "orgUnit.id")
    @Mapping(target = "orgUnitName", source = "orgUnit.name")
    @Mapping(target = "specialRankId", source = "specialRank.id")
    @Mapping(target = "specialRankName", source = "specialRank.name")
    @Mapping(target = "rankServiceId", source = "rankService.id")
    @Mapping(target = "rankServiceName", source = "rankService.name")
    @Mapping(target = "scienceBranchId", source = "scienceBranch.id")
    @Mapping(target = "scienceBranchName", source = "scienceBranch.name")
    @Mapping(target = "titleLine", source = "educator", qualifiedByName = "titleLine")
    EducatorDto toDto(Educator educator);

    /**
     * Подпись с регалиями собирает общий форматтер, а не маппер: тот же текст нужен выгрузке, и
     * вторая склейка неминуемо разошлась бы с первой. Здесь только вызов.
     */
    @Named("titleLine")
    default String titleLine(Educator educator) {
        return EducatorCredentialsAssembler.lineOf(educator);
    }
}
