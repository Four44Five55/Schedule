package ru.mapper.command;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.dto.command.LessonPlacementDto;
import ru.entity.write.LessonPlacement;

/**
 * MapStruct-маппер для преобразования {@link LessonPlacement} в {@link LessonPlacementDto}.
 *
 * <p>Используется в Command Side для конвертации размещений занятий в DTO для REST API.</p>
 *
 * <h2>Особенности маппинга:</h2>
 * <ul>
 *   <li>SessionId извлекается из {@code entity.session.id}</li>
 *   <li>AssignmentId извлекается из {@code entity.assignment.id}</li>
 *   <li>ScheduledSlot конвертируется в String через {@code name()}</li>
 *   <li>AuditoriumIds собираются из множества аудиторий через {@link #mapAuditoriums(LessonPlacement)}</li>
 * </ul>
 *
 * @see LessonPlacement
 * @see LessonPlacementDto
 * @see ScheduleSessionMapper
 */
@Mapper(componentModel = "spring")
public interface LessonPlacementMapper {

    /**
     * Преобразует сущность LessonPlacement в DTO для REST API.
     *
     * @param entity сущность размещения занятия
     * @return DTO для отправки через REST API
     */
    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "assignment.id", target = "assignmentId")
    @Mapping(source = "scheduledSlot", target = "scheduledSlot")
    @Mapping(target = "auditoriumIds", expression = "java(mapAuditoriums(entity))")
    LessonPlacementDto toDto(LessonPlacement entity);

    /**
     * Извлекает ID аудиторий из размещения занятия.
     *
     * @param entity сущность размещения занятия
     * @return множество ID аудиторий
     */
    default java.util.Set<Integer> mapAuditoriums(LessonPlacement entity) {
        if (entity.getAssignedAuditoriums() == null) {
            return java.util.Collections.emptySet();
        }
        return entity.getAssignedAuditoriums().stream()
            .map(ru.entity.Auditorium::getId)
            .collect(java.util.stream.Collectors.toSet());
    }
}
