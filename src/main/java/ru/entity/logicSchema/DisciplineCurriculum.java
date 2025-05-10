package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Discipline;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class DisciplineCurriculum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)  // Ленивая загрузка (загружается только при обращении)
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    @OneToMany(mappedBy = "disciplineCurriculum", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CurriculumSlot> curriculumSlots;

    @OneToOne(mappedBy = "curriculum", cascade = CascadeType.ALL, orphanRemoval = true)
    private ChainManager chainManager;


    public DisciplineCurriculum(int id, Discipline discipline, List<CurriculumSlot> curriculumSlots, ChainManager chainManager) {
        this.id = id;
        this.discipline = discipline;
        this.curriculumSlots = curriculumSlots;
        this.chainManager = chainManager;
    }
}
