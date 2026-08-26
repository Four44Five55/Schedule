package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.location.LocationCreateDto;
import ru.dto.location.LocationDeletionImpactDto;
import ru.dto.location.LocationDto;
import ru.dto.location.LocationUpdateDto;
import ru.services.LocationService;
import java.util.List;
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;
    @GetMapping
    public ResponseEntity<List<LocationDto>> getAll() {
        return ResponseEntity.ok(locationService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<LocationDto> getById(@PathVariable Integer id) {
        return locationService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<LocationDto> create(@Valid @RequestBody LocationCreateDto dto) {
        LocationDto created = locationService.createLocation(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<LocationDto> update(@PathVariable Integer id, @Valid @RequestBody LocationUpdateDto dto) {
        LocationDto updated = locationService.updateLocation(id, dto);
        return ResponseEntity.ok(updated);
    }
    /**
     * Предпросмотр последствий удаления: сколько корпусов привязано к локации (они блокируют удаление).
     */
    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<LocationDeletionImpactDto> deleteImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(locationService.deleteImpact(id));
    }

    /**
     * Удаление. Локацию с привязанными корпусами сервис удалить не даст (FK RESTRICT):
     * {@code InUseException} едет в {@link ApiExceptionHandler} и становится 409.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }
}
