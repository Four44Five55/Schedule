package ru.dto.group;

/**
 * DTO для представления информации об учебной группе.
 *
 * @param id             Уникальный идентификатор группы.
 * @param name           Название группы (например, "ИВТ-31").
 * @param size           Количество студентов.
 * @param baseAuditorium  Краткая информация о "домашней" аудитории (если есть).
 * @param enrollmentYear  Год набора (поступления); {@code null} — не указан.
 * @param orgUnitId       Подразделение (кафедра или факультет); {@code null} — не распределена.
 * @param orgUnitName     Название подразделения — чтобы список читался без склейки на клиенте.
 */
public record GroupDto(
        Integer id,
        String name,
        int size,
        AuditoriumBriefDto baseAuditorium,
        Integer enrollmentYear,
        Integer orgUnitId,
        String orgUnitName
) {
    /**
     * Краткое DTO для аудитории.
     */
    public record AuditoriumBriefDto(Integer id, String name) {}
}
