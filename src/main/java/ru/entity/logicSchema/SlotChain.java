package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.entity.Discipline;

import java.util.Objects;

@Entity
@Table(name = "slot_chain")
@NoArgsConstructor
@Getter
public class SlotChain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;  // Новое поле для первичного ключа

    @ManyToOne
    @JoinColumn(name = "slot_a_id", referencedColumnName = "id")
    private CurriculumSlot slotA;

    @ManyToOne
    @JoinColumn(name = "slot_b_id", referencedColumnName = "id")
    private CurriculumSlot slotB;

    @ManyToOne
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    public void setDiscipline(Discipline discipline) {
        this.discipline = discipline;
    }

    public SlotChain(CurriculumSlot slotA, CurriculumSlot slotB) {
        this.slotA = slotA;
        this.slotB = slotB;
    }


    public void setSlotA(CurriculumSlot slotA) {
        this.slotA = slotA;
    }

    public void setSlotB(CurriculumSlot slotB) {
        this.slotB = slotB;
    }

    // equals и hashCode
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
                ", discipline=" + discipline +
                '}';
    }
}