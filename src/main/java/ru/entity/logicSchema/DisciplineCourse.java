package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.entity.Discipline;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "discipline_course")
public class DisciplineCourse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    @Column(nullable = false)
    private int semester;

    @OneToMany(mappedBy = "disciplineCourse", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CurriculumSlot> curriculumSlots = new ArrayList<>();
}
