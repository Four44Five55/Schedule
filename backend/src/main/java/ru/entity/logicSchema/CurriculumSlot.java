package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Auditorium;
import ru.enums.AssessmentWindow;
import ru.enums.KindOfStudy;

import java.util.Objects;

@Entity
@Table(name = "curriculum_slot")
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

    /**
     * Где сдаётся аттестация — норма плана (чейнджлог 023).
     *
     * <p>{@code SESSION} означает «идёт в экзаменационную сессию и требует дней подготовки»:
     * генерация такие занятия не размещает (их раскладывает диспетчер — экзамен принимает лектор и
     * по группам идёт лесенкой), а датчик баланса сессии считает по ним дни. {@code STUDY_TIME} —
     * обычное занятие в семестре; сюда же попадает экзамен по физподготовке, которому сессия не
     * нужна.</p>
     *
     * <p>Для неаттестационных видов поле тривиально равно {@code STUDY_TIME}: так проверка
     * доступности читается единообразно для любого занятия, и лекция в окно сессии не просочится
     * по построению.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_window", nullable = false, length = 20)
    private AssessmentWindow assessmentWindow = AssessmentWindow.STUDY_TIME;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_lesson_id")
    private ThemeLesson themeLesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_auditorium_id")
    private Auditorium requiredAuditorium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_auditorium_id")
    private Auditorium priorityAuditorium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allowed_pool_id")
    private AuditoriumPool allowedAuditoriumPool;

    @Override
    public String toString() {
        return "CurriculumSlot{" +
                "id=" + id +
                ", position=" + position +
                ", kindOfStudy=" + kindOfStudy +
                ", disciplineCourseId=" + (disciplineCourse != null ? disciplineCourse.getId() : "null") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CurriculumSlot that = (CurriculumSlot) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
