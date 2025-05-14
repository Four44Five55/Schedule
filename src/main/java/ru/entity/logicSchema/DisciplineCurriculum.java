package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Discipline;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class DisciplineCurriculum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_slot_id")
    private CurriculumSlot startSlot;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_slot_id")
    private CurriculumSlot endSlot;
    @Transient
    private List<CurriculumSlot> curriculumSlots = new ArrayList<>();

    public DisciplineCurriculum(Discipline discipline, CurriculumSlot startSlot, CurriculumSlot endSlot) {
        this.discipline = discipline;
        this.startSlot = startSlot;
        this.endSlot = endSlot;
    }

}