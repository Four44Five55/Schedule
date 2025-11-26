package ru.dto.auditorium;

public record AuditoriumDto(
        Integer id,
        String name,
        int capacity,
        BuildingBriefDto building
) {
    public record BuildingBriefDto(Integer id, String name, LocationBriefDto location) {
    }

    public record LocationBriefDto(Integer id, String name) {
    }
}
