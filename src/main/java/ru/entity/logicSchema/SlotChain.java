package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "slot_chain")
@Setter
@Getter
public class SlotChain {

    @EmbeddedId
    private SlotChainId id;

    /**
     * Связь с менеджером связей.
     * Использует `curriculum_id` из `SlotChainId`.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("curriculumId")
    @JoinColumn(name = "curriculum_id", nullable = false)
    private ChainManager chainManager;

    /**
     * Слот A и B в связке.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("slotAId")
    @JoinColumn(name = "slot_a_id", nullable = false)
    private CurriculumSlot slotA;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("slotBId")
    @JoinColumn(name = "slot_b_id", nullable = false)
    private CurriculumSlot slotB;

    public SlotChain() {}

    public SlotChain(SlotChainId id,  CurriculumSlot slotA, CurriculumSlot slotB) {
        this.id = id;
        this.slotA = slotA;
        this.slotB = slotB;
    }

    // Геттеры и сеттеры
}


