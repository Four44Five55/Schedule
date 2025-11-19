package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Auditorium;
import ru.enums.KindOfStudy;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CurriculumSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discipline_course_id")
    private DisciplineCourse disciplineCourse;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KindOfStudy kindOfStudy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_lesson_id")
    private ThemeLesson themeLesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id")
    private StudyStream studyStream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_auditorium_id")
    private Auditorium requiredAuditorium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_auditorium_id")
    private Auditorium priorityAuditorium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allowed_pool_id")
    private AuditoriumPool allowedAuditoriumPool;

    private String splitRule;

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
