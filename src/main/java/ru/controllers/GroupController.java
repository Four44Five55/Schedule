package ru.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.group.GroupCreateDto;
import ru.dto.group.GroupDto;
import ru.dto.group.GroupUpdateDto;
import ru.services.GroupService;
import java.util.List;
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;
    @GetMapping
    public ResponseEntity<List<GroupDto>> getAll() {
        return ResponseEntity.ok(groupService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<GroupDto> getById(@PathVariable Integer id) {
        return groupService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<GroupDto> create(@Valid @RequestBody GroupCreateDto dto) {
        GroupDto created = groupService.createGroup(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<GroupDto> update(@PathVariable Integer id, @Valid @RequestBody GroupUpdateDto dto) {
        GroupDto updated = groupService.updateGroup(id, dto);
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
