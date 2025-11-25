package ru.entity.constraints;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.entity.Auditorium;
import ru.enums.KindOfConstraints;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "auditorium_constraint")
public class AuditoriumConstraint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auditorium_id")
    private Auditorium auditorium;
    @Enumerated(EnumType.STRING)
    @Column(name = "kind_of_constraint", nullable = false)
    private KindOfConstraints kindOfConstraint;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description")
    private String description;
}
