package ru.entity.logicSchema;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ru.entity.Discipline;

@Entity
@Table(name = "discipline_course")
public class DisciplineCourse {
    @Id
    private int id;
    @Getter
    @Setter
    @ManyToOne
    private Discipline discipline;
    @Getter
    @Setter
    private int semester;
}
