package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.SlotChain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.entity.logicSchema.SlotChain;
import java.util.List;

@Repository
public interface SlotChainRepository extends JpaRepository<SlotChain, Integer> {

    /**
     * Проверяет, существует ли уже такая сцепка.
     */
    boolean existsBySlotAIdAndSlotBId(Integer slotAId, Integer slotBId);

    /**
     * Находит ID всех слотов, напрямую сцепленных с данным слотом.
     * Если slotId - это slotA, вернет slotB.id. Если это slotB, вернет slotA.id.
     */
    @Query("SELECT CASE WHEN sc.slotA.id = :slotId THEN sc.slotB.id ELSE sc.slotA.id END " +
            "FROM SlotChain sc WHERE sc.slotA.id = :slotId OR sc.slotB.id = :slotId")
    List<Integer> findDirectlyLinkedSlotIds(@Param("slotId") Integer slotId);
}
