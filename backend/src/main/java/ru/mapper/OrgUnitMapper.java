package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.dto.orgUnit.OrgUnitDto;
import ru.entity.OrgUnit;

/**
 * Маппинг подразделения в DTO. Через MapStruct, как все остальные сущности проекта — ручной
 * {@code toDto} в контроллере (как у трёх constraint-контроллеров) уводит маппинг в HTTP-слой
 * и множится копипастой.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrgUnitMapper {

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "parentName", source = "parent.name")
    OrgUnitDto toDto(OrgUnit orgUnit);
}
