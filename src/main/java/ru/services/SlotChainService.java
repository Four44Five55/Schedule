package ru.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.sql.Update;
import org.springframework.stereotype.Service;
import ru.entity.logicSchema.SlotChain;
import ru.repositories.SlotChainRepository;

@Service
@Transactional
@RequiredArgsConstructor
public class SlotChainService {
    private final SlotChainRepository repository;

    public SlotChain create(SlotChain slotChain) {
        return repository.save(slotChain);
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
}
