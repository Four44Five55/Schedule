package ru.dto.auditoriumPool;

import java.util.List;

/**
 * DTO для полного представления информации о пуле аудиторий.
 *
 * @param id          Уникальный идентификатор.
 * @param name        Название пула.
 * @param description Описание.
 * @param auditoriums Список краткой информации об аудиториях в этом пуле.
 */
public record AuditoriumPoolDto(
        Integer id,
        String name,
        String description,
        List<AuditoriumBriefDto> auditoriums
) {
    /**
     * Краткое DTO для аудитории внутри пула.
     */
    public record AuditoriumBriefDto(Integer id, String name, int capacity) {
    }
}
