package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "slot_chain")
@NoArgsConstructor
@Getter
public class SlotChain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Setter
    @ManyToOne
    @JoinColumn(name = "slot_a_id", referencedColumnName = "id")
    private CurriculumSlot slotA;
    @Setter
    @ManyToOne
    @JoinColumn(name = "slot_b_id", referencedColumnName = "id")
    private CurriculumSlot slotB;

    public SlotChain(CurriculumSlot slotA, CurriculumSlot slotB) {
        this.slotA = slotA;
        this.slotB = slotB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotChain that = (SlotChain) o;
        return Objects.equals(slotA, that.slotA) &&
                Objects.equals(slotB, that.slotB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotA, slotB);
    }

    @Override
    public String toString() {
        return "SlotChain{" +
                "id=" + id +
                ", slotA=" + slotA +
                ", slotB=" + slotB +
                '}';
    }
}