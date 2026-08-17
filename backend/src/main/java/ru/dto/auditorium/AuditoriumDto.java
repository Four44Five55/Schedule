package ru.dto.auditorium;

import ru.dto.auditoriumPurpose.AuditoriumPurposeDto;
import ru.dto.feature.FeatureDto;

import java.util.List;

/**
 * Аудитория для справочника и выбора.
 *
 * @param orgUnitId   подразделение-владелец (кафедра); {@code null} — не указано
 * @param orgUnitName название подразделения — чтобы список читался без склейки на клиенте
 *                    (поле-удобство, прецедент — {@code EducatorDto.orgUnitName})
 */
public record AuditoriumDto(
        Integer id,
        String name,
        int capacity,
        BuildingBriefDto building,
        AuditoriumPurposeDto purpose,
        List<FeatureDto> features,
        Integer orgUnitId,
        String orgUnitName
) {
    public record BuildingBriefDto(Integer id, String name, LocationBriefDto location) {
    }

    public record LocationBriefDto(Integer id, String name) {
    }
}
