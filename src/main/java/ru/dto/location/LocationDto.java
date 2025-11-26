package ru.dto.location;

import java.util.List;

/**
 * DTO для полного представления информации о локации (территории).
 *
 * @param id        Уникальный идентификатор.
 * @param name      Название локации.
 * @param address   Адрес.
 * @param buildings Список краткой информации о корпусах на этой локации.
 */
public record LocationDto(
        Integer id,
        String name,
        String address,
        List<BuildingBriefDto> buildings
) {
    /**
     * Краткое DTO для представления корпуса внутри локации.
     */
    public record BuildingBriefDto(Integer id, String name) {}
}