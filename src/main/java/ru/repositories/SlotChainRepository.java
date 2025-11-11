package ru.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.SlotChain;

import java.util.List;
@Repository
public interface SlotChainRepository extends JpaRepository<SlotChain, Integer> {
    //  единый запрос для поиска всех связей (итеративная (BFS))
    @Query("SELECT CASE WHEN sc.slotA.id = :slotId THEN sc.slotB.id ELSE sc.slotA.id END " +
            "FROM SlotChain sc WHERE sc.slotA.id = :slotId OR sc.slotB.id = :slotId")
    List<Integer> findLinkedSlotIds(@Param("slotId") Integer slotId);


    List<SlotChain> findBySlotAId(Integer slotAId);

    List<SlotChain> findBySlotBId(Integer slotBId);

    boolean existsBySlotAIdAndSlotBId(Integer slotAId, Integer slotBId);

    void deleteBySlotAIdAndSlotBId(Integer slotAId, Integer slotBId);
}
