package ru.dto.group;

/**
 * DTO для представления информации об учебной группе.
 *
 * @param id             Уникальный идентификатор группы.
 * @param name           Название группы (например, "ИВТ-31").
 * @param size           Количество студентов.
 * @param baseAuditorium Краткая информация о "домашней" аудитории (если есть).
 */
public record GroupDto(
        Integer id,
        String name,
        int size,
        AuditoriumBriefDto baseAuditorium
) {
    /**
     * Краткое DTO для аудитории.
     */
    public record AuditoriumBriefDto(Integer id, String name) {}
}
