package ru.dto.building;

import java.util.List;

/**
 * DTO для полного представления информации об учебном корпусе.
 *
 * @param id          Уникальный идентификатор.
 * @param name        Название корпуса (например, "Корпус №3").
 * @param location    Краткая информация о локации, где находится корпус.
 * @param auditoriums Список краткой информации об аудиториях в этом корпусе.
 */
public record BuildingDto(
        Integer id,
        String name,
        LocationBriefDto location,
        List<AuditoriumBriefDto> auditoriums
) {
    /**
     * Краткое DTO для локации.
     */
    public record LocationBriefDto(Integer id, String name, String address) {
    }

    /**
     * Краткое DTO для аудитории.
     */
    public record AuditoriumBriefDto(Integer id, String name, int capacity) {
    }
}
