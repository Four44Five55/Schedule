package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.building.BuildingCreateDto;
import ru.dto.building.BuildingDeletionImpactDto;
import ru.dto.building.BuildingDto;
import ru.dto.building.BuildingUpdateDto;
import ru.services.BuildingService;
import java.util.List;
@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class BuildingController {
    private final BuildingService buildingService;
    @GetMapping
    public ResponseEntity<List<BuildingDto>> getAll() {
        return ResponseEntity.ok(buildingService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<BuildingDto> getById(@PathVariable Integer id) {
        return buildingService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<BuildingDto> create(@Valid @RequestBody BuildingCreateDto dto) {
        BuildingDto created = buildingService.createBuilding(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<BuildingDto> update(@PathVariable Integer id, @Valid @RequestBody BuildingUpdateDto dto) {
        BuildingDto updated = buildingService.updateBuilding(id, dto);
        return ResponseEntity.ok(updated);
    }
    /**
     * Предпросмотр последствий удаления: сколько аудиторий корпуса уйдёт каскадом и сколько занятий
     * останется без комнаты (в т.ч. закреплённых), а если аудитории требует учебный план — удалить нельзя.
     */
    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<BuildingDeletionImpactDto> deleteImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(buildingService.deleteImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            buildingService.deleteBuilding(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            // Аудитории корпуса указаны в учебном плане: БД удалить не даст (FK без каскада).
            // Отвечаем осмысленно, а не сырым 500 (глобального @ControllerAdvice в проекте нет).
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
