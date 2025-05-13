package ru.entity.logicSchema;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repositories.SlotChainRepository;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChainManager {

    private final SlotChainRepository slotChainRepository;

    public ChainManager(SlotChainRepository slotChainRepository) {
        this.slotChainRepository = slotChainRepository;
    }

    /**
     * Связывает два занятия (A → B).
     */
    public void chain(Integer slotAId, Integer slotBId) {
        if (slotAId.equals(slotBId)) {
            throw new IllegalArgumentException("Cannot chain slot to itself");
        }

        CurriculumSlot slotA = new CurriculumSlot();
        slotA.setId(slotAId);

        CurriculumSlot slotB = new CurriculumSlot();
        slotB.setId(slotBId);

        SlotChain chain = new SlotChain(slotA, slotB);
        slotChainRepository.save(chain);
    }

    /**
     * Проверяет, есть ли у занятия следующее в цепочке.
     */
    public boolean hasNext(Integer slotId) {
        return !slotChainRepository.findBySlotAId(slotId).isEmpty();
    }

    /**
     * Проверяет, есть ли у занятия предыдущее в цепочке.
     */
    public boolean hasPrev(Integer slotId) {
        return !slotChainRepository.findBySlotBId(slotId).isEmpty();
    }

    /**
     * Возвращает ID следующего занятия в цепочке (или null).
     */
    public Integer getNext(Integer slotId) {
        return slotChainRepository.findBySlotAId(slotId)
                .stream()
                .findFirst()
                .map(sc -> sc.getSlotB().getId())
                .orElse(null);
    }

    /**
     * Возвращает ID предыдущего занятия в цепочке (или null).
     */
    public Integer getPrev(Integer slotId) {
        return slotChainRepository.findBySlotBId(slotId)
                .stream()
                .findFirst()
                .map(sc -> sc.getSlotA().getId())
                .orElse(null);
    }

    /**
     * Возвращает все цепочки связей между слотами.
     */
    public Map<Integer, Integer> getAllChains() {
        return slotChainRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        sc -> sc.getSlotA().getId(),
                        sc -> sc.getSlotB().getId()
                ));
    }

    /**
     * Разрывает связь между занятиями.
     */
    public void unchain(Integer slotAId, Integer slotBId) {
        slotChainRepository.deleteBySlotAIdAndSlotBId(slotAId, slotBId);
    }

    /**
     * Проверяет, связаны ли два занятия.
     */
    public boolean isChained(Integer slotAId, Integer slotBId) {
        return slotChainRepository.existsBySlotAIdAndSlotBId(slotAId, slotBId) ||
                slotChainRepository.existsBySlotAIdAndSlotBId(slotBId, slotAId);
    }
}