package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Discipline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "discipline_course")
@Getter
@Setter
@NoArgsConstructor
public class DisciplineCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    @Column(nullable = false)
    private int semester;

    @OneToMany(mappedBy = "disciplineCourse", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CurriculumSlot> curriculumSlots = new ArrayList<>();

    // Конструктор для удобства
    public DisciplineCourse(Discipline discipline, int semester) {
        this.discipline = discipline;
        this.semester = semester;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisciplineCourse that = (DisciplineCourse) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DisciplineCourse{" +
                "id=" + id +
                ", semester=" + semester +
                ", discipline=" + (discipline != null ? discipline.getName() : "null") +
                '}';
    }
}
