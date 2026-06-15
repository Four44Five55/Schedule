package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Group;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Сущность, представляющая "Учебный поток" или "подгруппу".
 * Поток может состоять из одной или нескольких учебных групп.
 */
@Entity
@Table(name = "study_stream")
@Getter
@Setter
@NoArgsConstructor
public class StudyStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Название потока, например, "Поток ИВТ-3", "Подгруппа по английскому А".
     * Должно быть уникальным для удобства идентификации.
     */
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    /**
     * Номер семестра, к которому относится данный поток.
     */
    @Column(name = "semester", nullable = false)
    private int semester;

    /**
     * Связь "многие-ко-многим" с сущностью Group.
     * Определяет, какие группы входят в данный поток.
     * Конфигурация хранится в связующей таблице "stream_groups".
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "stream_groups", // Имя связующей таблицы
            joinColumns = @JoinColumn(name = "stream_id"), // Имя колонки в связующей таблице, которая ссылается на ЭТУ сущность (StudyStream)
            inverseJoinColumns = @JoinColumn(name = "group_id") // Имя колонки, которая ссылается на ДРУГУЮ сущность (Group)
    )
    private Set<Group> groups = new HashSet<>();

    public StudyStream(String name, int semester) {
        this.name = name;
        this.semester = semester;
    }

    /**
     * Вычисляет общее количество студентов в потоке путем суммирования
     * размеров всех входящих в него групп.
     *
     * @return Общее количество студентов.
     */
    public int calculateTotalSize() {
        if (groups == null) {
            return 0;
        }
        return groups.stream()
                .mapToInt(Group::getSize)
                .sum();
    }

    // Вспомогательные методы для управления связью
    public void addGroup(Group group) {
        this.groups.add(group);
        group.getStudyStreams().add(this);
    }

    public void removeGroup(Group group) {
        this.groups.remove(group);
        group.getStudyStreams().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudyStream that = (StudyStream) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "StudyStream{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}