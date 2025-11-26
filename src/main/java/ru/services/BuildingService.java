package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.building.BuildingCreateDto;
import ru.dto.building.BuildingDto;
import ru.dto.building.BuildingUpdateDto;
import ru.entity.Building;
import ru.entity.Location;
import ru.mapper.BuildingMapper;
import ru.repository.BuildingRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final LocationService locationService;
    private final BuildingMapper buildingMapper;

    @Transactional
    public BuildingDto createBuilding(BuildingCreateDto createDto) {
        // Используем LocationService для получения сущности Location
        Location location = locationService.findEntityById(createDto.locationId());

        Building newBuilding = new Building();
        newBuilding.setName(createDto.name());
        newBuilding.setLocation(location);

        return buildingMapper.toDto(buildingRepository.save(newBuilding));
    }

    @Transactional
    public BuildingDto updateBuilding(Integer id, BuildingUpdateDto updateDto) {
        Building buildingToUpdate = findEntityById(id);

        // Если ID локации изменился, получаем новую сущность локации через сервис
        if (!buildingToUpdate.getLocation().getId().equals(updateDto.locationId())) {
            Location newLocation = locationService.findEntityById(updateDto.locationId());
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

    @Transactional
    public void deleteBuilding(Integer id) {
        // TODO: Добавить проверку, что у корпуса нет аудиторий, перед удалением
        // (хотя ON DELETE CASCADE в DDL обработает это, лучше явная проверка)
        if (!buildingRepository.existsById(id)) {
            throw new EntityNotFoundException("Корпус с id=" + id + " не найден.");
        }
        buildingRepository.deleteById(id);
    }

    // --- СЛУЖЕБНЫЙ МЕТОД ---

    /**
     * Находит сущность Building по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Building findEntityById(Integer id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Корпус с id=" + id + " не найден."));
    }
}
