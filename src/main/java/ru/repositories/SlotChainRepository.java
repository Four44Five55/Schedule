package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.SlotChain;

import java.util.List;
@Repository
public interface SlotChainRepository extends JpaRepository<SlotChain, Integer> {

    List<SlotChain> findBySlotAId(Integer slotAId);

    List<SlotChain> findBySlotBId(Integer slotBId);

    boolean existsBySlotAIdAndSlotBId(Integer slotAId, Integer slotBId);

    void deleteBySlotAIdAndSlotBId(Integer slotAId, Integer slotBId);
}
