package ru.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.dto.discipline.DisciplineCreateDto;
import ru.dto.discipline.DisciplineDto;
import ru.entity.Discipline;

import java.util.List;

/**
 * Маппер для преобразования между сущностью Discipline и ее DTO.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DisciplineCourseMapper.class}
)
public interface DisciplineMapper {

    /**
     * Преобразует сущность Discipline в полное DisciplineDto.
     */
    DisciplineDto toDto(Discipline discipline);

    /**
     * Преобразует список сущностей в список DTO.
     */
    List<DisciplineDto> toDtoList(List<Discipline> disciplines);

    /**
     * Преобразует DisciplineCreateDto в сущность Discipline.
     * Поля 'id' и 'courses' в сущности останутся null/пустыми, что корректно для нового объекта.
     */
    Discipline toEntity(DisciplineCreateDto createDto);
}
