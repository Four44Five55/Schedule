package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.DisciplineBriefDto;
import ru.dto.DisciplineCourseDto;
import ru.entity.Discipline;
import ru.entity.logicSchema.DisciplineCourse;

import java.util.List;

/**
 * Маппер для преобразования между сущностью DisciplineCourse и ее DTO.
 * Использует MapStruct для автоматической генерации реализации.
 * componentModel = "spring" позволяет внедрять этот маппер как Spring Bean.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DisciplineCourseMapper {

    /**
     * Преобразует сущность DisciplineCourse в DisciplineCourseDto.
     * MapStruct автоматически увидит, что нужно преобразовать вложенную
     * сущность Discipline в DisciplineBriefDto, и вызовет соответствующий
     * метод этого же маппера (toBriefDto).
     *
     * @param course Сущность для преобразования.
     * @return DTO для отображения.
     */
    DisciplineCourseDto toDto(DisciplineCourse course);

    /**
     * Преобразует сущность Discipline в DisciplineBriefDto.
     * Этот метод используется как вспомогательный для toDto(DisciplineCourse).
     *
     * @param discipline Сущность дисциплины.
     * @return Краткое DTO дисциплины.
     */
    DisciplineBriefDto toBriefDto(Discipline discipline);

    /**
     * Преобразует список сущностей DisciplineCourse в список DisciplineCourseDto.
     * MapStruct автоматически сгенерирует цикл и вызовет toDto() для каждого элемента.
     *
     * @param courses Список сущностей.
     * @return Список DTO.
     */
    List<DisciplineCourseDto> toDtoList(List<DisciplineCourse> courses);

}
