package ru.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.location.LocationCreateDto;
import ru.dto.location.LocationDto;
import ru.dto.location.LocationUpdateDto;
import ru.entity.Location;
import ru.mapper.LocationMapper;
import ru.repository.LocationRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

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

    @Transactional
    public void deleteLocation(Integer id) {
        // TODO: Добавить проверку, что у локации нет привязанных корпусов,
        // так как у нас стоит ON DELETE RESTRICT
        if (!locationRepository.existsById(id)) {
            throw new EntityNotFoundException("Локация с id=" + id + " не найдена.");
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
