package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    @JoinColumn(name = "discipline_course")
    private DisciplineCourse disciplineCourse;

    public CurriculumSlot(KindOfStudy kindOfStudy, ThemeLesson themeLesson, DisciplineCourse disciplineCourse) {
        this.kindOfStudy = kindOfStudy;
        this.themeLesson = themeLesson;
        this.disciplineCourse = disciplineCourse;
    }

    @Override
    public String toString() {
        return "CurriculumSlot{" +
                "id=" + id +
                ", kindOfStudy=" + kindOfStudy +
                ", themeLesson=" + themeLesson +
                ", disciplineCourse=" + disciplineCourse +
                '}';
    }
}
