package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.feature.FeatureDto;
import ru.entity.Feature;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FeatureMapper {
    FeatureDto toDto(Feature feature);
    List<FeatureDto> toDtoList(List<Feature> features);
}
