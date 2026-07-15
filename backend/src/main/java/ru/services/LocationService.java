package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.location.LocationCreateDto;
import ru.dto.location.LocationDeletionImpactDto;
import ru.dto.location.LocationDto;
import ru.dto.location.LocationUpdateDto;
import ru.entity.Location;
import ru.mapper.LocationMapper;
import ru.repository.BuildingRepository;
import ru.repository.LocationRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    // Только для счётчика цены удаления (корпуса блокируют удаление локации через FK RESTRICT).
    private final BuildingRepository buildingRepository;

    @Transactional
    public LocationDto createLocation(LocationCreateDto createDto) {
        if (locationRepository.existsByName(createDto.name())) {
            throw new IllegalStateException("Локация с названием '" + createDto.name() + "' уже существует.");
        }
        Location newLocation = new Location();
        newLocation.setName(createDto.name());
        newLocation.setAddress(createDto.address());
        return locationMapper.toDto(locationRepository.save(newLocation));
    }

    @Transactional
    public LocationDto updateLocation(Integer id, LocationUpdateDto updateDto) {
        Location location = getEntityById(id);

        locationRepository.findByName(updateDto.name()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalStateException("Локация с названием '" + updateDto.name() + "' уже существует.");
            }
        });

        location.setName(updateDto.name());
        location.setAddress(updateDto.address());
        return locationMapper.toDto(locationRepository.save(location));
    }

    /**
     * Предпросмотр последствий удаления локации: сколько корпусов к ней привязано (они блокируют
     * удаление через FK RESTRICT). Состояние не меняет.
     *
     * @see LocationDeletionImpactDto
     */
    @Transactional(readOnly = true)
    public LocationDeletionImpactDto deleteImpact(Integer id) {
        Location location = getEntityById(id);
        long buildingCount = buildingRepository.countByLocationId(id);
        return new LocationDeletionImpactDto(
                location.getId(),
                location.getName(),
                buildingCount == 0, // deletable: правило считается ЗДЕСЬ, фронт его не выводит
                buildingCount);
    }

    /**
     * Удаляет локацию по ID.
     *
     * <p>Отказывает, если к локации привязаны корпуса ({@code building.location_id ON DELETE
     * RESTRICT}) — БД удалить не даст, и раньше наружу летел сырой 500.</p>
     */
    @Transactional
    public void deleteLocation(Integer id) {
        // Одно правило — один источник: и предпросмотр, и отказ смотрят на тот же deletable.
        LocationDeletionImpactDto impact = deleteImpact(id); // бросит 404, если локации нет
        if (!impact.deletable()) {
            throw new IllegalStateException(
                    "Локацию нельзя удалить: к ней привязано " + impact.buildingCount()
                            + " корпусов. Сначала перенесите или удалите их.");
        }
        locationRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<LocationDto> findById(Integer id) {
        // Используем метод с EntityGraph для получения полной информации
        return locationRepository.findWithBuildingsById(id).map(locationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<LocationDto> findAll() {
        return locationRepository.findAll().stream()
                .map(locationMapper::toDto)
                .collect(Collectors.toList());
    }

    // === СЛУЖЕБНЫЕ МЕТОДЫ (для других сервисов) ===

    /**
     * Находит сущность Location по ID. Для внутреннего использования другими сервисами.
     */
    @Transactional(readOnly = true)
    public Location getEntityById(Integer id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Локация с id=" + id + " не найдена."));
    }
}
