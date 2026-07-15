package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.building.BuildingCreateDto;
import ru.dto.building.BuildingDeletionImpactDto;
import ru.dto.building.BuildingDto;
import ru.dto.building.BuildingUpdateDto;
import ru.entity.Auditorium;
import ru.entity.Building;
import ru.entity.Location;
import ru.mapper.BuildingMapper;
import ru.repository.BuildingRepository;
import ru.repository.CurriculumSlotRepository;
import ru.repository.GroupRepository;
import ru.repository.write.LessonPlacementRepository;
import ru.services.projection.ProjectionMaintenance;
import ru.services.projection.ProjectionSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final LocationService locationService;
    private final BuildingMapper buildingMapper;
    private final ProjectionMaintenance projectionMaintenance;
    // Репозитории, а не сервисы: нужны только счётчики цены удаления (по образцу AuditoriumService).
    private final CurriculumSlotRepository curriculumSlotRepository;
    private final GroupRepository groupRepository;
    private final LessonPlacementRepository placementRepository;

    @Transactional
    public BuildingDto createBuilding(BuildingCreateDto createDto) {
        // Используем LocationService для получения сущности Location
        Location location = locationService.getEntityById(createDto.locationId());

        Building newBuilding = new Building();
        newBuilding.setName(createDto.name());
        newBuilding.setLocation(location);

        return buildingMapper.toDto(buildingRepository.save(newBuilding));
    }

    @Transactional
    public BuildingDto updateBuilding(Integer id, BuildingUpdateDto updateDto) {
        Building buildingToUpdate = getEntityById(id);

        // Если ID локации изменился, получаем новую сущность локации через сервис
        if (!buildingToUpdate.getLocation().getId().equals(updateDto.locationId())) {
            Location newLocation = locationService.getEntityById(updateDto.locationId());
            buildingToUpdate.setLocation(newLocation);
        }

        buildingToUpdate.setName(updateDto.name());

        return buildingMapper.toDto(buildingRepository.save(buildingToUpdate));
    }

    @Transactional(readOnly = true)
    public Optional<BuildingDto> findById(Integer id) {
        return buildingRepository.findWithDetailsById(id).map(buildingMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<BuildingDto> findAll() {
        return buildingRepository.findAll().stream()
                .map(buildingMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Предпросмотр последствий удаления корпуса. Удаление каскадом уносит ВСЕ его аудитории, а с
     * ними — комнаты у занятий в них (останутся без аудитории, включая закреплённые). Если хоть одну
     * аудиторию корпуса требует учебный план — удалить нельзя ({@code deletable = false}). Состояние
     * не меняет.
     *
     * @see BuildingDeletionImpactDto
     */
    @Transactional(readOnly = true)
    public BuildingDeletionImpactDto deleteImpact(Integer id) {
        Building building = buildingRepository.findWithDetailsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Корпус с id=" + id + " не найден."));

        List<Integer> auditoriumIds = building.getAuditoriums().stream()
                .map(Auditorium::getId)
                .toList();

        // Нет аудиторий → все каскадные счётчики нулевые (и IN () по пустому списку не гоняем).
        if (auditoriumIds.isEmpty()) {
            return new BuildingDeletionImpactDto(building.getId(), building.getName(),
                    true, 0, 0, 0, 0, 0);
        }

        long referencedBySlots = curriculumSlotRepository.countReferencingAuditoriumIn(auditoriumIds);
        return new BuildingDeletionImpactDto(
                building.getId(),
                building.getName(),
                referencedBySlots == 0, // deletable: правило считается ЗДЕСЬ, фронт его не выводит
                auditoriumIds.size(),
                placementRepository.countByAuditoriumIdIn(auditoriumIds),
                placementRepository.countLockedByAuditoriumIdIn(auditoriumIds),
                referencedBySlots,
                groupRepository.countByBaseAuditoriumIdIn(auditoriumIds));
    }

    /**
     * Удаляет корпус по ID.
     *
     * <p>Отказывает, если на любую аудиторию корпуса ссылается учебный план (требуемая/приоритетная):
     * эти FK идут без каскада, БД удалить не даст, и раньше наружу летел сырой 500. Занятость в
     * расписании удалению НЕ мешает — но занятия останутся без комнаты, поэтому цена названа в
     * {@link #deleteImpact} и подтверждается в UI.</p>
     */
    @Transactional
    public void deleteBuilding(Integer id) {
        // Одно правило — один источник: и предпросмотр, и отказ смотрят на тот же deletable.
        BuildingDeletionImpactDto impact = deleteImpact(id); // бросит 404, если корпуса нет
        if (!impact.deletable()) {
            throw new IllegalStateException(
                    "Корпус нельзя удалить: его аудитории указаны требуемыми или приоритетными в "
                            + impact.slotsRequiringIt() + " занятиях учебного плана. "
                            + "Сначала уберите эти аудитории из плана.");
        }

        // ОБЯЗАТЕЛЬНО до удаления: аудитории корпуса уйдут каскадом, а с ними `placement_auditoriums`.
        // Занятия останутся (уже без комнаты) — read-модель должна показать это, а не старое название
        // удалённой аудитории. Объявляем устаревание проекции по ВСЕМ аудиториям корпуса.
        List<Integer> auditoriumIds = buildingRepository.findWithDetailsById(id)
                .map(b -> b.getAuditoriums().stream().map(Auditorium::getId).collect(Collectors.toList()))
                .orElse(List.of());
        if (!auditoriumIds.isEmpty()) {
            projectionMaintenance.announce(ProjectionSource.AUDITORIUM, auditoriumIds);
        }

        buildingRepository.deleteById(id);
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    /**
     * Находит сущность Building по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Building getEntityById(Integer id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Корпус с id=" + id + " не найден."));
    }
}
