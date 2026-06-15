package ru.mapper.command;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.dto.command.ScheduleSessionDto;
import ru.entity.write.ScheduleSession;

/**
 * MapStruct-маппер для преобразования {@link ScheduleSession} в {@link ScheduleSessionDto}.
 *
 * <p>Используется в Command Side для конвертации сущностей в DTO для REST API.</p>
 *
 * <h3>Особенности маппинга:</h3>
 * <ul>
 *   <li>Status конвертируется через {@link #mapStatus(ru.enums.SessionStatus)}</li>
 *   <li>PlacementsCount вычисляется через {@link ScheduleSession#getPlacementsCount()}</li>
 *   <li>HasWorkspaceSnapshot проверяется через {@link ScheduleSession#hasWorkspaceSnapshot()}</li>
 * </ul>
 *
 * @see ScheduleSession
 * @see ScheduleSessionDto
 * @see LessonPlacementMapper
 */
@Mapper(componentModel = "spring")
public interface ScheduleSessionMapper {

    /**
     * Преобразует сущность ScheduleSession в DTO для REST API.
     *
     * @param entity сущность сессии из БД
     * @return DTO для отправки через REST API
     */
    @Mapping(target = "status", expression = "java(mapStatus(entity.getStatus()))")
    @Mapping(target = "placementsCount", expression = "java(entity.getPlacementsCount())")
    @Mapping(target = "hasWorkspaceSnapshot", expression = "java(entity.hasWorkspaceSnapshot())")
    ScheduleSessionDto toDto(ScheduleSession entity);

    /**
     * Конвертирует enum SessionStatus в SessionStatusDto.
     *
     * @param status статус сущности
     * @return соответствующий статус DTO
     */
    default ScheduleSessionDto.SessionStatusDto mapStatus(ru.enums.SessionStatus status) {
        return ScheduleSessionDto.SessionStatusDto.valueOf(status.name());
    }
}
