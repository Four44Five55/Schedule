package ru.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dto.slotChain.SlotChainCreateDto;
import ru.dto.slotChain.SlotChainDto;
import ru.services.SlotChainService;

import java.util.List;

@RestController
@RequestMapping("/api/slot-chains")
@RequiredArgsConstructor
public class SlotChainController {

    private final SlotChainService slotChainService;

    @GetMapping
    public ResponseEntity<List<SlotChainDto>> getAll() {
        return ResponseEntity.ok(slotChainService.findAll());
    }

    @GetMapping("/chain/{slotId}")
    public ResponseEntity<List<Integer>> getFullChain(@PathVariable Integer slotId) {
        return ResponseEntity.ok(slotChainService.getFullChain(slotId));
    }

    @PostMapping
    public ResponseEntity<SlotChainDto> create(@Valid @RequestBody SlotChainCreateDto dto) {
        SlotChainDto created = slotChainService.createChain(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        slotChainService.deleteChain(id);
        return ResponseEntity.noContent().build();
    }
}
