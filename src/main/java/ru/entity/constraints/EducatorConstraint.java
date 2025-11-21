package ru.entity.constraints;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.entity.Educator;
import ru.enums.KindOfConstraints;

import java.time.LocalDate;

@Entity
@Table(name = "educator_constraint")
@Getter
@Setter
@NoArgsConstructor
public class EducatorConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "educator_id")
    private Educator educator;

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