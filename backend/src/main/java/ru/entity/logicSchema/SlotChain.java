package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Сущность, представляющая "сцепку" - неразрывную связь между двумя слотами
 * учебного плана. Указывает, что занятие 'slotB' должно идти сразу после 'slotA'.
 */
@Entity
@Table(name = "slot_chain")
@Getter
@Setter
@NoArgsConstructor
public class SlotChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Первый слот в "сцепке".
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_a_id")
    private CurriculumSlot slotA;

    /**
     * Второй слот в "сцепке", который должен идти сразу после первого.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_b_id")
    private CurriculumSlot slotB;

    public SlotChain(CurriculumSlot slotA, CurriculumSlot slotB) {
        this.slotA = slotA;
        this.slotB = slotB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotChain slotChain = (SlotChain) o;
        return id != null && Objects.equals(id, slotChain.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "SlotChain{" +
                "id=" + id +
                ", slotA_id=" + (slotA != null ? slotA.getId() : "null") +
                ", slotB_id=" + (slotB != null ? slotB.getId() : "null") +
                '}';
    }
}