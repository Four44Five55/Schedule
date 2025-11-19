package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.logicSchema.StudyStream;
import ru.inter.IMaterialEntity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Сущность, представляющая учебную группу.
 */
@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
public class Group implements IMaterialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название группы.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Количество студентов в группе.
     */
    @Column(name = "size", nullable = false)
    private int size;

    /**
     * "Домашняя" или базовая аудитория для группы.
     * Является приоритетной по умолчанию для занятий этой группы.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_auditorium_id")
    private Auditorium baseAuditorium;

    /**
     * Связь для того, чтобы можно было узнать, в каких потоках состоит группа.
     * `mappedBy` указывает, что эта сторона связи не является управляющей.
     */
    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    private Set<StudyStream> studyStreams = new HashSet<>();

    // Конструктор для удобства создания новых экземпляров
    public Group(String name, int size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return id != null && Objects.equals(id, group.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
