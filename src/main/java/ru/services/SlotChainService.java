package ru.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.SlotChain;
import ru.repositories.SlotChainRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SlotChainService {
    private final SlotChainRepository repository;
    private final CurriculumSlotService slotService;

    public void createChain(Integer slotAId, Integer slotBId) {
        if (slotAId.equals(slotBId)) {
            throw new IllegalArgumentException("Cannot chain slot to itself");
        }
        if (repository.existsBySlotAIdAndSlotBId(slotAId, slotBId)) {
            throw new IllegalStateException("Chain already exists");
        }

        CurriculumSlot slotA = slotService.getById(slotAId);
        CurriculumSlot slotB = slotService.getById(slotBId);

        repository.save(new SlotChain(slotA, slotB));
    }

    public SlotChain getByID(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public SlotChain update(Integer id, SlotChain slotChain) {
        SlotChain old = repository.findById(id).orElse(null);
        if (old != null) {
            old.setSlotA(slotChain.getSlotA());
            old.setSlotB(slotChain.getSlotB());
        }
        return repository.save(old != null ? old : null);
    }

    public SlotChain save(SlotChain slotChain) {
        return repository.save(slotChain);

    }

    public void removeChain(Integer slotAId, Integer slotBId) {
        repository.deleteBySlotAIdAndSlotBId(slotAId, slotBId);
    }

    public boolean isChained(Integer slotAId, Integer slotBId) {
        return repository.existsBySlotAIdAndSlotBId(slotAId, slotBId) ||
                repository.existsBySlotAIdAndSlotBId(slotBId, slotAId);
    }

    public List<Integer> getLinkedSlotIds(Integer slotId) {
        List<Integer> linkedIds = new ArrayList<>();

        repository.findBySlotAId(slotId)
                .forEach(sc -> linkedIds.add(sc.getSlotB().getId()));

        repository.findBySlotBId(slotId)
                .forEach(sc -> linkedIds.add(sc.getSlotA().getId()));

        return linkedIds;
    }
}
