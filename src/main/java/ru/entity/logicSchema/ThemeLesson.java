package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Discipline;

import java.util.Objects;

/**
 * Сущность, представляющая тему занятия.
 * Тема всегда принадлежит одной конкретной "глобальной" дисциплине.
 */
@Entity
@Table(
        name = "theme_lesson",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"discipline_id", "theme_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ThemeLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Номер темы, например, "1.1" или "2". Уникален в рамках одной дисциплины.
     */
    @Column(name = "theme_number", nullable = false)
    private String themeNumber;

    /**
     * Полное название темы.
     */
    @Column(name = "title")
    private String title;

    /**
     * Дисциплина, к которой относится эта тема.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    public ThemeLesson(String themeNumber, String title, Discipline discipline) {
        this.themeNumber = themeNumber;
        this.title = title;
        this.discipline = discipline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThemeLesson that = (ThemeLesson) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ThemeLesson{" +
                "id=" + id +
                ", themeNumber='" + themeNumber + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
