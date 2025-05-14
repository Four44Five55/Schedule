package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Discipline;
import ru.enums.KindOfStudy;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CurriculumSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Enumerated(EnumType.STRING)
    private KindOfStudy kindOfStudy;
    @ManyToOne
    private ThemeLesson themeLesson;
    @ManyToOne
    @JoinColumn(name = "discipline_id") // Добавляем явную связь
    private Discipline discipline;

    public CurriculumSlot(KindOfStudy kindOfStudy, ThemeLesson themeLesson, Discipline discipline) {
        this.kindOfStudy = kindOfStudy;
        this.themeLesson = themeLesson;
        this.discipline = discipline;
    }

    @Override
    public String toString() {
        return "CurriculumSlot{" +
                "id=" + id +
                ", kindOfStudy=" + kindOfStudy +
                ", themeLesson=" + themeLesson +
                ", discipline=" + discipline +
                '}';
    }
}
