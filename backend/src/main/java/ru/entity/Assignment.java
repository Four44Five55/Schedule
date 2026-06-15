package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.logicSchema.CurriculumSlot;
import ru.entity.logicSchema.StudyStream;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Сущность "Назначение".
 *
 * <p>Это центральная организационная сущность, которая связывает "академическую" часть
 * учебного плана ({@link CurriculumSlot}) с "ресурсной" частью — кто ({@link Educator})
 * и для кого ({@link StudyStream}) проводит это занятие.</p>
 */
@Entity
@Table(name = "assignment")
@Getter
@Setter
@NoArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Слот учебного плана, к которому относится это назначение.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_slot_id")
    private CurriculumSlot curriculumSlot;

    /**
     * Поток/подгруппа, для которой предназначено это назначение.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_stream_id")
    private StudyStream studyStream;

    /**
     * Преподаватели, назначенные на это занятие.
     * Может быть один или несколько (например, для лекции, которую ведут двое).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "assignment_educators",
            joinColumns = @JoinColumn(name = "assignment_id"),
            inverseJoinColumns = @JoinColumn(name = "educator_id")
    )
    private Set<Educator> educators = new HashSet<>();

    // Конструктор для удобства создания
    public Assignment(CurriculumSlot curriculumSlot, StudyStream studyStream) {
        this.curriculumSlot = curriculumSlot;
        this.studyStream = studyStream;
    }

    // Вспомогательные методы для управления связью с Educator
    public void addEducator(Educator educator) {
        this.educators.add(educator);
        // В Educator нет обратной связи @ManyToMany, поэтому вторую сторону обновлять не нужно.
    }

    public void removeEducator(Educator educator) {
        this.educators.remove(educator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Assignment that = (Assignment) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "id=" + id +
                ", curriculumSlotId=" + (curriculumSlot != null ? curriculumSlot.getId() : "null") +
                ", studyStreamId=" + (studyStream != null ? studyStream.getId() : "null") +
                '}';
    }
}
