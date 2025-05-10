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
@Table(name = "curriculum_slot")
public class CurriculumSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private KindOfStudy kindOfStudy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_lesson_id")
    private ThemeLesson themeLesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discipline_curriculum_id")
    private DisciplineCurriculum disciplineCurriculum;

    public CurriculumSlot(KindOfStudy kindOfStudy) {
        this.kindOfStudy = kindOfStudy;

    }

}
