package ru.dto.auditorium;

import ru.dto.auditoriumPurpose.AuditoriumPurposeDto;
import ru.dto.feature.FeatureDto;

import java.util.List;

public record AuditoriumDto(
        Integer id,
        String name,
        int capacity,
        BuildingBriefDto building,
        AuditoriumPurposeDto purpose,
        List<FeatureDto> features
) {
    public record BuildingBriefDto(Integer id, String name, LocationBriefDto location) {
    }

    public record LocationBriefDto(Integer id, String name) {
    }
}
