package ru.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.enums.PeriodType;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Сущность, представляющая "Учебный период".
 * Определяет временные рамки для семестров, сессий и т.д.
 */
@Entity
@Table(name = "study_period")
@Getter
@Setter
@NoArgsConstructor
public class StudyPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Читаемое название периода, например, "Осенний семестр 2025/2026".
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Учебный год, к которому относится период.
     * Например, 2025 для учебного года 2025/2026.
     */
    @Column(name = "study_year", nullable = false)
    private int studyYear;

    /**
     * Тип периода, например, "FALL_SEMESTER", "SPRING_SEMESTER", "EXAM_SESSION".
     * Можно использовать Enum, если типы фиксированы.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    /**
     * Дата начала учебного периода.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Дата окончания учебного периода.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    public StudyPeriod(String name, int studyYear, PeriodType periodType, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.studyYear = studyYear;
        this.periodType = periodType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudyPeriod that = (StudyPeriod) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "StudyPeriod{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}