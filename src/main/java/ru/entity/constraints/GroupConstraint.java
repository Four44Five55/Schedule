package ru.entity.constraints;

import jakarta.persistence.*;
import ru.entity.Group;
import ru.enums.KindOfConstraints;

import java.time.LocalDate;

@Entity
@Table(name = "group_constraint")
public class GroupConstraint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

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
