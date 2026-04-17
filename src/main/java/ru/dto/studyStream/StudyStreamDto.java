package ru.dto.studyStream;

import java.util.List;

/**
 * DTO для полного представления информации об учебном потоке/подгруппе.
 *
 * @param id       Уникальный идентификатор потока.
 * @param name     Название потока (например, "Поток ИВТ-3 курс").
 * @param semester Номер семестра, к которому относится этот поток.
 * @param groups   Список DTO групп, входящих в этот поток.
 */
public record StudyStreamDto(
        Integer id,
        String name,
        int semester,
        List<GroupBriefDto> groups // Используем краткое DTO для групп
) {
    /**
     * Краткое DTO для представления группы внутри потока.
     */
    public record GroupBriefDto(Integer id, String name, int size) {
    }
}
